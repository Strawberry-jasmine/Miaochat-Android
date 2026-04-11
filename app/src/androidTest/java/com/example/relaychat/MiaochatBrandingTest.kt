package com.example.relaychat

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiaochatBrandingTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun applicationLabel_isMiaochat() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()

        assertThat(label).isEqualTo("Miaochat")
    }

    @Test
    fun chatScreen_displaysMiaochatBranding() {
        composeRule.onNodeWithText("Miaochat").fetchSemanticsNode()
        composeRule.onNodeWithText(
            "Miaochat keeps thread history locally.",
            substring = true,
        ).fetchSemanticsNode()
    }
}
