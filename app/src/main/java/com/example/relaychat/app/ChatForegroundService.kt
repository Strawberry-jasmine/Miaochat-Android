package com.example.relaychat.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.relaychat.MainActivity
import com.example.relaychat.R
import com.example.relaychat.ui.strings.pendingReplyTextSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val coordinator by lazy { ChatExecutionCoordinator.getInstance(applicationContext) }
    private var monitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val threadId = intent?.getStringExtra(EXTRA_THREAD_ID)
        if (threadId.isNullOrBlank()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty().ifBlank {
            getString(R.string.notification_reason_background_send)
        }

        ensureNotificationChannel()
        promoteToForeground(
            notification = buildNotification(
                reply = coordinator.activeReply.value?.takeIf { it.threadId == threadId },
                fallbackReason = reason,
            ),
        )

        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            coordinator.activeReply.collectLatest { reply ->
                if (reply?.threadId == threadId) {
                    if (canPostNotifications()) {
                        notificationManager().notify(
                            NOTIFICATION_ID,
                            buildNotification(reply = reply, fallbackReason = reason),
                        )
                    }
                    return@collectLatest
                }

                stopForegroundCompat()
                stopSelfResult(startId)
                cancel()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_background_replies),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_background_replies_description)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun buildNotification(
        reply: InFlightAssistantReply?,
        fallbackReason: String,
    ): Notification {
        val visuals = reply?.let { pendingReplyVisuals(it, textSet = applicationContext.pendingReplyTextSet()) }
        val detail = visuals?.detail ?: fallbackReason
        val title = visuals?.title ?: getString(R.string.notification_background_title)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title_format, getString(R.string.app_name), title))
            .setContentText(detail)
            .setSubText(visuals?.elapsedLabel ?: getString(R.string.notification_subtext_background))
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val CHANNEL_ID = "relaychat_background_reply"
        private const val NOTIFICATION_ID = 42021
        private const val EXTRA_THREAD_ID = "extra_thread_id"
        private const val EXTRA_REASON = "extra_reason"

        fun startSend(
            context: Context,
            threadId: String,
            reason: String,
        ) {
            val intent = Intent(context, ChatForegroundService::class.java).apply {
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_REASON, reason)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
