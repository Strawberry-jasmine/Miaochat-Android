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
import androidx.test.uiautomator.UiDevice
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
            val targetContext = ApplicationProvider.getApplicationContext<android.content.Context>()
            val settingsRepository = SettingsRepository(targetContext)
            val threadRepository = ThreadRepository(targetContext)
            val baseUrl = server.url("/").toString().removeSuffix("/")

            composeRule.onNodeWithText("New Chat").fetchSemanticsNode()

            composeRule.onNode(hasText("Settings") and hasClickAction()).performClick()
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

            composeRule.onNode(hasText("Chat") and hasClickAction()).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Mock Provider | mock-model").fetchSemanticsNode()

            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Mock Provider | mock-model").fetchSemanticsNode()

            composeRule.onAllNodes(hasSetTextAction())[0]
                .performTextReplacement("hello relaychat")
            composeRule.onNodeWithText("hello relaychat").fetchSemanticsNode()
            composeRule.onNodeWithContentDescription("Send").performClick()

            val request = server.takeRequest(5, TimeUnit.SECONDS)
            if (request == null) {
                val requestFailedVisible = nodeExists("Request failed")
                val apiKeyErrorVisible = nodeExists("Set an API key in Settings before sending.")
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
            composeRule.onNodeWithText("Regenerate").fetchSemanticsNode()
            composeRule.onNodeWithText("2 messages | $baseUrl/responses").fetchSemanticsNode()

            composeRule.onNodeWithContentDescription("Actions").performClick()
            composeRule.onNodeWithText("New chat").performClick()
            composeRule.onNodeWithText("0 messages | $baseUrl/responses").fetchSemanticsNode()

            composeRule.onNodeWithContentDescription("Attach image").performClick()

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

    private fun replaceSettingsField(index: Int, value: String) {
        composeRule.onAllNodes(hasSetTextAction())[index]
            .performScrollTo()
            .performTextReplacement(value)
    }

    private fun nodeExists(
        text: String,
        substring: Boolean = false,
    ): Boolean = runCatching {
        composeRule.onNodeWithText(text, substring = substring).fetchSemanticsNode()
        true
    }.getOrDefault(false)
}
