package com.example.relaychat.ui.chat

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private sealed interface MessageBlock {
    data class Markdown(
        val id: String,
        val text: String,
    ) : MessageBlock

    data class Code(
        val id: String,
        val language: String?,
        val code: String,
    ) : MessageBlock
}

@Composable
fun MessageContent(
    text: String,
    isOutgoing: Boolean,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(text) { parseMessageBlocks(text) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MessageBlock.Markdown -> MarkdownBlock(
                    text = block.text,
                    isOutgoing = isOutgoing,
                )

                is MessageBlock.Code -> CodeBlock(
                    language = block.language,
                    code = block.code,
                    isOutgoing = isOutgoing,
                    onCopy = { onCopyCode(block.code) },
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlock(
    text: String,
    isOutgoing: Boolean,
) {
    val parser = remember { Parser.builder().build() }
    val renderer = remember { HtmlRenderer.builder().build() }
    val html = remember(text) { renderer.render(parser.parse(text)) }
    val textColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface
    val linkColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
    val context = LocalContext.current

    AndroidView(
        factory = {
            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.1f)
            }
        },
        update = { view ->
            view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    isOutgoing: Boolean,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isOutgoing) {
                    Color.White.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = language?.takeIf { it.isNotBlank() }?.uppercase() ?: "CODE",
                style = MaterialTheme.typography.labelMedium,
                color = if (isOutgoing) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCopy) {
                Text("Copy")
            }
        }

        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall,
            color = if (isOutgoing) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}

private fun parseMessageBlocks(text: String): List<MessageBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MessageBlock>()
    val markdownBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var language: String? = null
    var insideCode = false

    fun flushMarkdown() {
        val chunk = markdownBuffer.joinToString("\n").trim('\n')
        if (chunk.isNotEmpty()) {
            blocks += MessageBlock.Markdown(
                id = java.util.UUID.randomUUID().toString(),
                text = chunk,
            )
        }
        markdownBuffer.clear()
    }

    fun flushCode() {
        blocks += MessageBlock.Code(
            id = java.util.UUID.randomUUID().toString(),
            language = language,
            code = codeBuffer.joinToString("\n"),
        )
        codeBuffer.clear()
        language = null
    }

    lines.forEach { line ->
        if (line.startsWith("```")) {
            if (insideCode) {
                flushCode()
                insideCode = false
            } else {
                flushMarkdown()
                val opener = line.removePrefix("```").trim()
                language = opener.ifBlank { null }
                insideCode = true
            }
            return@forEach
        }

        if (insideCode) {
            codeBuffer += line
        } else {
            markdownBuffer += line
        }
    }

    if (insideCode) {
        flushCode()
    } else {
        flushMarkdown()
    }

    return if (blocks.isEmpty()) {
        listOf(MessageBlock.Markdown(id = java.util.UUID.randomUUID().toString(), text = text))
    } else {
        blocks
    }
}
