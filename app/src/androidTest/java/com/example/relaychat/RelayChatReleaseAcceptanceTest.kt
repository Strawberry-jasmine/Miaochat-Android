package com.example.relaychat

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayChatReleaseAcceptanceTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val device: UiDevice
        get() = UiDevice.getInstance(instrumentation)

    @Test
    fun releaseApp_canSendImageAndModelReadsItsText() {
        forceStopReleaseApp()
        val probeName = "relaychat_probe_multimodal.png"
        repeat(3) { attempt ->
            device.executeShellCommand("logcat -c")

            launchReleaseApp()
            dismissPickerOrHomeResidue()
            dismissRequestFailedDialogIfPresent()
            openFreshChat()

            requireObject(By.desc("Attach image"), 10_000).click()
            assertThat(device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE)), 10_000)).isTrue()

            openDownloadsRoot()
            selectFile(probeName)

            assertThat(device.wait(Until.hasObject(By.text("Image attached")), 10_000)).isTrue()

            val composer = requireObject(By.clazz("android.widget.EditText"), 10_000)
            composer.text = "What exact word and number are shown inside the attached image?"
            clickSendButton()

            val replyLog = waitForAssistantReplyLogOrTransientFailure(
                expectedOne = "zebra",
                expectedTwo = "42",
                timeoutMs = 120_000,
            )
            if (replyLog != null) {
                Log.i(TAG, "Multimodal reply log: $replyLog")
                assertThat(replyLog.lowercase(Locale.US)).contains("zebra")
                assertThat(replyLog).contains("42")
                return
            }

            Log.w(TAG, "Multimodal attempt ${attempt + 1} hit a transient failure dialog; retrying.")
        }

        error("Multimodal verification exhausted retries after transient failures.")
    }

    @Test
    fun releaseApp_reasoningModeSwitchChangesRequestPayload() {
        forceStopReleaseApp()
        device.executeShellCommand("logcat -c")

        launchReleaseApp()
        dismissPickerOrHomeResidue()
        openSettingsTab()
        applyProviderPreset("Use intelalloc preset")
        openChatTab()

        openFreshChat()
        requireObject(By.text("Deep"), 10_000).click()
        assertThat(device.wait(Until.hasObject(By.text("Reasoning: xhigh")), 10_000)).isTrue()

        val composer = requireObject(By.clazz("android.widget.EditText"), 10_000)
        composer.text = "deep_probe_20260408"
        clickSendButton()

        val deepLog = waitForLogLine(
            timeoutMs = 90_000,
            contains = listOf(
                "RelayChatNetwork",
                "send request",
                "input=\"deep_probe_20260408\"",
                "reasoning=xhigh",
            ),
        )
        Log.i(TAG, "Deep reasoning request log: $deepLog")

        val deepCompletionLog = waitForLogLine(
            timeoutMs = 120_000,
            contains = listOf(
                "RelayChatStream",
                "completed",
                "deep_probe_20260408",
            ),
        )
        Log.i(TAG, "Deep reasoning completion log: $deepCompletionLog")

        openFreshChat()
        requireObject(By.text("Balanced"), 10_000).click()
        assertThat(device.wait(Until.hasObject(By.text("Reasoning: medium")), 10_000)).isTrue()

        val secondComposer = requireObject(By.clazz("android.widget.EditText"), 10_000)
        secondComposer.text = "balanced_probe_20260408"
        clickSendButton()

        val balancedLog = waitForLogLine(
            timeoutMs = 90_000,
            contains = listOf(
                "RelayChatNetwork",
                "send request",
                "input=\"balanced_probe_20260408\"",
                "reasoning=medium",
            ),
        )
        Log.i(TAG, "Balanced reasoning request log: $balancedLog")
    }

    @Test
    fun releaseApp_openaiWebSearchPathSendsToolAndRestoresIntelallocPreset() {
        forceStopReleaseApp()
        device.executeShellCommand("logcat -c")

        launchReleaseApp()
        dismissPickerOrHomeResidue()
        openSettingsTab()
        applyProviderPreset("Use OpenAI Responses preset")

        openChatTab()
        openFreshChat()
        requireObject(By.text("Deep"), 10_000).click()
        assertThat(device.wait(Until.hasObject(By.text("Reasoning: xhigh")), 10_000)).isTrue()

        val composer = requireObject(By.clazz("android.widget.EditText"), 10_000)
        composer.text = "What are the latest NASA Mars updates today? Use live web info."
        clickSendButton()

        val requestLog = waitForLogLine(
            timeoutMs = 60_000,
            contains = listOf(
                "RelayChatNetwork",
                "send request",
                "tools=1",
                "What are the latest NASA Mars updates today? Use live web info.",
            ),
        )
        Log.i(TAG, "OpenAI web-search request log: $requestLog")

        val resultLog = waitForLogLine(
            timeoutMs = 60_000,
            containsAny = listOf(
                listOf("RelayChatNetwork", "send response", "code=200"),
                listOf("RelayChatNetwork", "send response", "code=403"),
                listOf("RelayChatNetwork", "send failed"),
            ),
        )
        Log.i(TAG, "OpenAI web-search result log: $resultLog")

        if (resultLog.contains("code=200")) {
            val completionLog = waitForLogLine(
                timeoutMs = 120_000,
                contains = listOf("RelayChatStream", "completed"),
            )
            Log.i(TAG, "OpenAI web-search completion log: $completionLog")
            assertThat(completionLog).isNotEmpty()
        } else if (resultLog.contains("code=403")) {
            val errorLog = waitForLogLine(
                timeoutMs = 15_000,
                contains = listOf("unsupported_country_region_territory"),
            )
            Log.i(TAG, "OpenAI web-search region error log: $errorLog")
            assertThat(errorLog).contains("unsupported_country_region_territory")
        }

        dismissRequestFailedDialogIfPresent()
        openSettingsTab()
        applyProviderPreset("Use intelalloc preset")
        assertThat(device.wait(Until.hasObject(By.text("intelalloc Codex")), 10_000)).isTrue()
        openChatTab()
        assertThat(device.wait(Until.hasObject(By.text("intelalloc | gpt-5.4")), 10_000)).isTrue()
    }

    @Test
    fun releaseApp_intelallocHistoryAndThinkingIndicatorsWork() {
        val simplePrompt = "Reply with READY only."
        val webPrompt = "What are the latest NASA Mars updates today? Use live web info."

        forceStopReleaseApp()
        device.executeShellCommand("logcat -c")

        launchReleaseApp()
        dismissPickerOrHomeResidue()
        dismissRequestFailedDialogIfPresent()
        openSettingsTab()
        applyProviderPreset("Use intelalloc preset")
        openChatTab()

        assertThat(device.wait(Until.hasObject(By.text("History")), 10_000)).isTrue()

        openFreshChat()
        requireObject(By.text("Balanced"), 10_000).click()

        val composer = requireObject(By.clazz("android.widget.EditText"), 10_000)
        composer.text = simplePrompt
        clickSendButton()

        assertThat(device.wait(Until.hasObject(By.text("Sending")), 5_000)).isTrue()
        assertThat(device.wait(Until.hasObject(By.text("thinking")), 5_000)).isTrue()
        val simpleThinking = waitForLogLine(
            timeoutMs = 30_000,
            contains = listOf("RelayChatUi", "phase=thinking", "detail=sending request"),
        )
        val simpleThreadId = requireThreadId(simpleThinking)

        val simpleCompleted = waitForLogLine(
            timeoutMs = 120_000,
            contains = listOf("RelayChatUi", "thread=$simpleThreadId", "phase=completed"),
            afterLine = simpleThinking,
        )
        Log.i(TAG, "Simple message completion log: $simpleCompleted")
        assertThat(device.wait(Until.gone(By.text("thinking")), 30_000)).isTrue()
        device.executeShellCommand("logcat -c")

        openFreshChat()
        requireObject(By.text("Balanced"), 10_000).click()
        setWebSearch(enabled = true)

        val webComposer = requireObject(By.clazz("android.widget.EditText"), 10_000)
        webComposer.text = webPrompt
        clickSendButton()

        assertThat(device.wait(Until.hasObject(By.text("Sending")), 5_000)).isTrue()
        assertThat(device.wait(Until.hasObject(By.text("thinking")), 5_000)).isTrue()
        val webThinking = waitForLogLine(
            timeoutMs = 30_000,
            contains = listOf("RelayChatUi", "phase=thinking", "detail=sending request"),
        )
        val webThreadId = requireThreadId(webThinking)

        val webRequest = waitForLogLine(
            timeoutMs = 60_000,
            contains = listOf(
                "RelayChatNetwork",
                "send request",
                "tools=1",
                webPrompt,
            ),
        )
        Log.i(TAG, "Intelalloc web request log: $webRequest")

        val firstToolEvent = waitForLogLine(
            timeoutMs = 90_000,
            contains = listOf(
                "RelayChatNetwork",
                "stream event",
                "stage=tool",
            ),
            afterLine = webRequest,
        )
        Log.i(TAG, "Intelalloc tool-stage log: $firstToolEvent")

        val searchingPhase = waitForLogLine(
            timeoutMs = 90_000,
            contains = listOf(
                "RelayChatUi",
                "thread=$webThreadId",
                "phase=searching",
            ),
            afterLine = firstToolEvent,
        )
        Log.i(TAG, "Intelalloc UI searching log: $searchingPhase")

        val reasoningPhase = waitForLogLine(
            timeoutMs = 120_000,
            contains = listOf(
                "RelayChatUi",
                "thread=$webThreadId",
                "phase=thinking",
                "detail=reasoning",
            ),
            afterLine = searchingPhase,
        )
        Log.i(TAG, "Intelalloc UI reasoning log: $reasoningPhase")

        val timingSummary = waitForLogLine(
            timeoutMs = 240_000,
            contains = listOf(
                "RelayChatNetwork",
                "stream timing",
            ),
            afterLine = reasoningPhase,
        )
        Log.i(TAG, "Intelalloc timing summary: $timingSummary")

        val webCompleted = waitForLogLine(
            timeoutMs = 240_000,
            contains = listOf("RelayChatUi", "thread=$webThreadId", "phase=completed"),
            afterLine = timingSummary,
        )
        Log.i(TAG, "Intelalloc web completion log: $webCompleted")
        assertThat(device.wait(Until.gone(By.text("thinking")), 30_000)).isTrue()

        openHistory()
        assertThat(device.wait(Until.hasObject(By.text(autoTitle(webPrompt))), 10_000)).isTrue()
        val historySearch = requireObject(By.clazz("android.widget.EditText"), 10_000)
        historySearch.text = autoTitle(simplePrompt)
        assertThat(device.wait(Until.hasObject(By.text(autoTitle(simplePrompt))), 10_000)).isTrue()

        requireObject(By.text(autoTitle(simplePrompt)), 10_000).click()
        assertThat(device.wait(Until.hasObject(By.textContains(simplePrompt)), 10_000)).isTrue()
    }

    private fun launchReleaseApp() {
        val launchIntent = instrumentation.targetContext.packageManager
            .getLaunchIntentForPackage(RELEASE_PACKAGE)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ?: error("Release app $RELEASE_PACKAGE is not installed.")

        instrumentation.targetContext.startActivity(launchIntent)
        assertThat(device.wait(Until.hasObject(By.pkg(RELEASE_PACKAGE)), 10_000)).isTrue()
        device.waitForIdle()
    }

    private fun forceStopReleaseApp() {
        device.executeShellCommand("am force-stop $RELEASE_PACKAGE")
        device.waitForIdle()
    }

    private fun dismissPickerOrHomeResidue() {
        repeat(2) {
            when {
                device.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE)) -> {
                    device.pressBack()
                    device.wait(Until.hasObject(By.pkg(RELEASE_PACKAGE)), 5_000)
                }

                device.hasObject(By.pkg(LAUNCHER_PACKAGE)) -> launchReleaseApp()
            }
        }
    }

    private fun dismissRequestFailedDialogIfPresent() {
        if (device.hasObject(By.text("Request failed"))) {
            device.findObject(By.text("OK"))?.click()
            device.wait(Until.gone(By.text("Request failed")), 5_000)
        }
    }

    private fun clickSendButton(timeoutMs: Long = 10_000) {
        device.waitForIdle()
        val send = device.wait(Until.findObject(By.desc("Send")), timeoutMs)
        if (send != null) {
            send.click()
            return
        }

        Log.w(TAG, "Falling back to send-button coordinate tap after accessibility lookup timed out.")
        device.click(1836, 870)
        device.waitForIdle()
    }

    private fun openFreshChat() {
        requireObject(By.desc("Actions"), 10_000).click()
        requireObject(By.text("New chat"), 5_000).click()
        device.waitForIdle()
    }

    private fun openHistory() {
        val history = device.wait(Until.findObject(By.text("History")), 10_000)
            ?: device.wait(Until.findObject(By.desc("History")), 10_000)
            ?: error("Could not find the History entry point.")
        history.click()
        assertThat(device.wait(Until.hasObject(By.text("Search threads")), 10_000)).isTrue()
    }

    private fun openSettingsTab() {
        requireObject(By.text("Settings"), 10_000).click()
        device.waitForIdle()
    }

    private fun setWebSearch(enabled: Boolean) {
        var label = device.findObject(By.text("Web/search"))
        repeat(4) {
            if (label != null) {
                return@repeat
            }
            device.swipe(960, 800, 960, 420, 20)
            device.waitForIdle()
            label = device.findObject(By.text("Web/search"))
        }
        val resolvedLabel = label ?: error("Could not find the Web/search label.")
        val labelBounds = resolvedLabel.visibleBounds
        val toggle = device.findObjects(By.checkable(true))
            .filter { candidate ->
                val bounds = candidate.visibleBounds
                bounds.centerX() > labelBounds.centerX()
            }
            .minByOrNull { candidate ->
                val bounds = candidate.visibleBounds
                abs(bounds.centerY() - labelBounds.centerY()) + abs(bounds.centerX() - 1820)
            }
            ?: error("Could not find the Web/search toggle.")

        if (toggle.isChecked != enabled) {
            toggle.click()
            device.waitForIdle()
        }
    }

    private fun openChatTab() {
        requireObject(By.text("Chat"), 10_000).click()
        device.waitForIdle()
    }

    private fun applyProviderPreset(label: String) {
        requireObject(By.text(label), 10_000).click()
        device.waitForIdle()
    }

    private fun openDownloadsRoot() {
        requireObject(By.clazz("android.widget.ImageButton"), 5_000).click()

        val downloads = device.wait(
            Until.findObject(
                By.text(
                    java.util.regex.Pattern.compile("^(Downloads|下载)$")
                )
            ),
            5_000,
        ) ?: device.wait(
            Until.findObject(
                By.text(
                    java.util.regex.Pattern.compile(".*(Downloads|下载).*")
                )
            ),
            5_000,
        ) ?: error("Could not find the Downloads root in DocumentsUI.")

        downloads.click()
        if (device.hasObject(By.res(DOCUMENTS_UI_PACKAGE, "drawer_roots"))) {
            device.click(900, 500)
            device.wait(Until.gone(By.res(DOCUMENTS_UI_PACKAGE, "drawer_roots")), 5_000)
        }
        assertThat(
            device.wait(Until.hasObject(By.text(probeFileMatcher())), 10_000)
        ).isTrue()
    }

    private fun selectFile(fileName: String) {
        val label = requireObject(By.text(fileName), 10_000)
        val entry = generateSequence(label) { it.parent }.drop(1).firstOrNull()

        entry?.click() ?: label.click()
        device.waitForIdle()

        if (device.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE))) {
            val bounds = label.visibleBounds
            device.click(bounds.centerX(), bounds.centerY())
            device.waitForIdle()
        }

        if (device.hasObject(By.pkg(DOCUMENTS_UI_PACKAGE))) {
            device.pressEnter()
        }
    }

    private fun waitForAssistantReplyLogOrTransientFailure(
        expectedOne: String,
        expectedTwo: String,
        timeoutMs: Long,
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastLog = ""

        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text("Request failed")) &&
                device.hasObject(By.textContains("overloaded"))
            ) {
                val message = device.findObject(By.textContains("overloaded"))?.text ?: "Request failed"
                Log.w(TAG, "Transient server failure during multimodal verification: $message")
                dismissRequestFailedDialogIfPresent()
                return null
            }

            val logOutput = device.executeShellCommand(
                "logcat -d -v time RelayChatStream:I RelayChatNetwork:I *:S"
            )
            lastLog = logOutput
            val candidate = logOutput.lineSequence().lastOrNull { line ->
                val normalized = line.lowercase(Locale.US)
                normalized.contains(expectedOne.lowercase(Locale.US)) &&
                    normalized.contains(expectedTwo.lowercase(Locale.US))
            }
            if (candidate != null) {
                return candidate
            }
            Thread.sleep(1_500)
        }

        error("No assistant reply log contained \"$expectedOne\" and \"$expectedTwo\". Last log dump: $lastLog")
    }

    private fun waitForLogLine(
        timeoutMs: Long,
        contains: List<String> = emptyList(),
        containsAny: List<List<String>> = emptyList(),
        afterLine: String? = null,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastLog = ""

        while (System.currentTimeMillis() < deadline) {
            val logOutput = device.executeShellCommand(
                "logcat -d -v time RelayChatNetwork:I RelayChatStream:I RelayChatUi:I *:S"
            )
            lastLog = logOutput
            val candidates = logOutput.lineSequence().toList().let { lines ->
                if (afterLine == null) {
                    lines
                } else {
                    val startIndex = lines.indexOfLast { it == afterLine }
                    if (startIndex == -1) emptyList() else lines.drop(startIndex + 1)
                }
            }
            val line = candidates.lastOrNull { candidate ->
                val allMatch = contains.isNotEmpty() && contains.all { it in candidate }
                val anyMatch = containsAny.isNotEmpty() &&
                    containsAny.any { group -> group.all { it in candidate } }
                allMatch || anyMatch
            }
            if (line != null) {
                return line
            }
            Thread.sleep(1_500)
        }

        error("No matching log line was found. Last log dump: $lastLog")
    }

    private fun probeFileMatcher(): java.util.regex.Pattern =
        java.util.regex.Pattern.compile("^relaychat_probe_multimodal\\.png$")

    private fun autoTitle(prompt: String): String = prompt.trim().take(28)

    private fun requireThreadId(logLine: String): String =
        THREAD_PATTERN.find(logLine)?.groupValues?.get(1)
            ?: error("Could not extract thread id from log line: $logLine")

    private fun requireObject(
        selector: BySelector,
        timeoutMs: Long,
    ) = device.wait(Until.findObject(selector), timeoutMs)
        ?: error("Timed out waiting for selector: $selector")
}

private const val TAG = "RelayChatAcceptance"
private const val RELEASE_PACKAGE = "com.example.relaychat"
private const val DOCUMENTS_UI_PACKAGE = "com.android.documentsui"
private const val LAUNCHER_PACKAGE = "com.android.launcher3"
private val THREAD_PATTERN = Regex("""thread=([0-9a-f-]+)""")
