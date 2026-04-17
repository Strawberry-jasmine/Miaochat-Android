package com.example.relaychat

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.example.relaychat.core.model.AppLocale
import com.example.relaychat.core.model.AppSettings
import com.example.relaychat.core.model.AppThemeMode
import com.example.relaychat.core.model.ChatMessage
import com.example.relaychat.core.model.ChatRole
import com.example.relaychat.data.settings.SettingsRepository
import com.example.relaychat.data.threads.ThreadRepository
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayChatSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val device: UiDevice
        get() = UiDevice.getInstance(instrumentation)

    @Test
    fun coldLaunch_staysAlive_andShowsPrimaryNavigation() {
        val targetContext = appContext()

        composeRule.waitForIdle()
        composeRule.onNode(hasText(targetContext.getString(R.string.nav_chat)) and hasClickAction())
            .fetchSemanticsNode()
        composeRule.onNode(hasText(targetContext.getString(R.string.nav_settings)) and hasClickAction())
            .fetchSemanticsNode()

        assertThat(device.wait(Until.hasObject(By.pkg(targetContext.packageName)), 5_000)).isTrue()
        assertThat(device.currentPackageName).isEqualTo(targetContext.packageName)
        assertThat(device.executeShellCommand("pidof ${targetContext.packageName}").trim()).isNotEmpty()
    }

    @Test
    fun settingsPersist_sendWorks_newChatWorks_andImagePickerReturnsSafely() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"type":"response.output_text.delta","delta":"RelayChat "}

                    data: {"type":"response.output_text.delta","delta":"stub reply"}

                    data: {"type":"response.completed","response":{"id":"resp_test_1","model":"mock-model","output":[{"type":"message","content":[{"type":"output_text","text":"RelayChat stub reply"}]}]}}

                    """.trimIndent()
                )
        )
        server.start()

        try {
            val targetContext = appContext()
            val settingsRepository = SettingsRepository(targetContext)
            val threadRepository = ThreadRepository(targetContext)
            val baseUrl = server.url("/").toString().removeSuffix("/")
            val newChatLabel = targetContext.getString(R.string.action_new_chat)
            val settingsTabLabel = targetContext.getString(R.string.nav_settings)
            val chatTabLabel = targetContext.getString(R.string.nav_chat)
            val sendDescription = targetContext.getString(R.string.chat_composer_send_desc)
            val requestFailedTitle = targetContext.getString(R.string.error_request_failed_title)
            val apiKeyError = targetContext.getString(R.string.error_api_key_before_send)
            val actionsDescription = targetContext.getString(R.string.chat_menu_desc)
            val attachImageDescription = targetContext.getString(R.string.chat_composer_attach_image_desc)
            val twoMessageSummary =
                targetContext.resources.getQuantityString(R.plurals.thread_message_count, 2, 2)

            composeRule.onNode(hasText(settingsTabLabel) and hasClickAction()).performClick()
            composeRule.waitForIdle()

            replaceSettingsField(index = 0, value = "Mock Provider")
            replaceSettingsField(index = 1, value = baseUrl)
            replaceSettingsField(index = 2, value = "/responses")
            replaceSettingsField(index = 3, value = "test-key")
            replaceSettingsField(index = 4, value = "mock-model")

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    val settings = settingsRepository.settingsFlow.first()
                    settings.provider.displayName == "Mock Provider" &&
                        settings.provider.baseUrl == baseUrl &&
                        settings.provider.path == "/responses" &&
                        settings.provider.model == "mock-model" &&
                        settingsRepository.readApiKey() == "test-key"
                }
            }

            composeRule.onNode(hasText(chatTabLabel) and hasClickAction()).performClick()
            composeRule.waitForIdle()
            assertThat(nodeExists("Mock Provider", substring = true)).isTrue()
            assertThat(nodeExists("mock-model", substring = true)).isTrue()

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            assertThat(nodeExists("Mock Provider", substring = true)).isTrue()
            assertThat(nodeExists("mock-model", substring = true)).isTrue()

            composeRule.onAllNodes(hasSetTextAction())[0]
                .performTextReplacement("hello relaychat")
            composeRule.onNodeWithText("hello relaychat").fetchSemanticsNode()
            composeRule.onNodeWithContentDescription(sendDescription).performClick()

            val request = server.takeRequest(5, TimeUnit.SECONDS)
            if (request == null) {
                val requestFailedVisible = nodeExists(requestFailedTitle)
                val apiKeyErrorVisible = nodeExists(apiKeyError)
                val cleartextErrorVisible = nodeExists("CLEARTEXT", substring = true)
                error(
                    "No request reached MockWebServer. " +
                        "requestFailedVisible=$requestFailedVisible " +
                        "apiKeyErrorVisible=$apiKeyErrorVisible " +
                        "cleartextErrorVisible=$cleartextErrorVisible"
                )
            }
            assertThat(request!!.path).isEqualTo("/responses")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key")
            assertThat(request.body.readUtf8()).contains("hello relaychat")

            composeRule.waitUntil(timeoutMillis = 10_000) {
                runBlocking {
                    threadRepository.threadsFlow.first()
                        .firstOrNull()
                        ?.messages
                        ?.lastOrNull()
                        ?.text == "RelayChat stub reply"
                }
            }
            assertThat(nodeExists(twoMessageSummary, substring = true)).isTrue()

            composeRule.onNodeWithContentDescription(actionsDescription).performClick()
            composeRule.onNodeWithText(newChatLabel).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking {
                    val selectedThreadId = threadRepository.selectedThreadIdFlow.first()
                    threadRepository.threadsFlow.first()
                        .firstOrNull { it.id == selectedThreadId }
                        ?.messages
                        .isNullOrEmpty()
                }
            }

            composeRule.onNodeWithContentDescription(attachImageDescription).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                device.currentPackageName != targetContext.packageName
            }
            val pickerPackage = device.currentPackageName
            assertThat(pickerPackage).isNotEqualTo(targetContext.packageName)

            device.pressBack()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                device.currentPackageName == targetContext.packageName
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun localeAndThemeChanges_applyImmediately_withoutCrashing() {
        val targetContext = appContext()
        val settingsRepository = SettingsRepository(targetContext)

        runBlocking {
            settingsRepository.writeSettings(
                AppSettings.Default.copy(
                    appLocale = AppLocale.ENGLISH,
                    themeMode = AppThemeMode.LIGHT,
                ),
            )
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        composeRule.onNode(hasText(composeRule.activity.getString(R.string.nav_settings)) and hasClickAction())
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.app_locale_simplified_chinese))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                settingsRepository.settingsFlow.first().appLocale == AppLocale.SIMPLIFIED_CHINESE
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText(composeRule.activity.getString(R.string.nav_settings)) and hasClickAction())
            .fetchSemanticsNode()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.theme_mode_dark))
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                settingsRepository.settingsFlow.first().themeMode == AppThemeMode.DARK
            }
        }

        val persistedSettings = runBlocking { settingsRepository.settingsFlow.first() }
        assertThat(persistedSettings.appLocale).isEqualTo(AppLocale.SIMPLIFIED_CHINESE)
        assertThat(persistedSettings.themeMode).isEqualTo(AppThemeMode.DARK)
        assertThat(device.currentPackageName).isEqualTo(targetContext.packageName)

        composeRule.onNode(hasText(composeRule.activity.getString(R.string.nav_chat)) and hasClickAction())
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText(composeRule.activity.getString(R.string.nav_chat)) and hasClickAction())
            .fetchSemanticsNode()
    }

    @Test
    fun historyRail_expands_switchesThreads_andClearsCurrentThread() {
        val targetContext = appContext()
        val threadRepository = ThreadRepository(targetContext)
        val titleOne = "History Alpha ${System.currentTimeMillis()}"
        val titleTwo = "History Beta ${System.currentTimeMillis()}"
        var firstThreadId = ""
        var secondThreadId = ""

        runBlocking {
            firstThreadId = threadRepository.createThread(title = titleOne, select = true)
            threadRepository.appendMessage(
                message = ChatMessage(role = ChatRole.USER, text = "alpha message"),
                threadId = firstThreadId,
            )
            secondThreadId = threadRepository.createThread(title = titleTwo, select = false)
            threadRepository.appendMessage(
                message = ChatMessage(role = ChatRole.USER, text = "beta message"),
                threadId = secondThreadId,
            )
            threadRepository.selectThread(firstThreadId)
        }

        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        val expandDescription = composeRule.activity.getString(R.string.history_expand_desc)
        val collapseDescription = composeRule.activity.getString(R.string.history_collapse_desc)
        val searchLabel = composeRule.activity.getString(R.string.history_search_label)
        val clearThreadLabel = composeRule.activity.getString(R.string.action_clear_thread)

        composeRule.onNodeWithContentDescription(expandDescription).performClick()
        composeRule.onNodeWithText(searchLabel).fetchSemanticsNode()
        composeRule.onNodeWithContentDescription(collapseDescription).performClick()
        assertThat(nodeExists(searchLabel)).isFalse()

        composeRule.onNodeWithText(titleTwo, substring = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                threadRepository.selectedThreadIdFlow.first() == secondThreadId
            }
        }
        assertThat(nodeExists(titleTwo, substring = true)).isTrue()

        composeRule.onNodeWithContentDescription(expandDescription).performClick()
        composeRule.onNodeWithText(clearThreadLabel).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                threadRepository.getThread(secondThreadId)?.messages.isNullOrEmpty()
            }
        }
    }

    private fun replaceSettingsField(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index]
            .performScrollTo()
            .performTextReplacement(value)
    }

    private fun appContext(): android.content.Context =
        ApplicationProvider.getApplicationContext()

    private fun nodeExists(
        text: String,
        substring: Boolean = false,
    ): Boolean = runCatching {
        composeRule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
    }.getOrDefault(false)
}
