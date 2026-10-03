package com.example.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiRepository
import com.example.ai.Message
import com.example.themes.KeyboardTheme
import kotlinx.coroutines.launch

@Composable
fun AiChatPanel(
    theme: KeyboardTheme,
    initialContext: String? = null,
    onBack: () -> Unit,
    onInsert: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    var isLoading by remember { mutableStateOf(false) }

    // Initial greeting and context
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            if (!initialContext.isNullOrBlank()) {
                messages.add(Message("system", "The user is currently typing the following text: \"$initialContext\". Use this as context for the conversation."))
                messages.add(Message("assistant", "I see you're typing: \"$initialContext\". How can I help you with this?"))
            } else {
                messages.add(Message("assistant", "Hello! I'm your AI assistant. How can I help you today?"))
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Standard keyboard height area
            .background(theme.backgroundColor)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.textColor)
            }
            Text(
                "AI Chat",
                color = theme.textColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { messages.clear(); if (!initialContext.isNullOrBlank()) messages.add(Message("system", "Context: $initialContext")); messages.add(Message("assistant", "Hello! How can I help you?")) }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = theme.textColor.copy(alpha = 0.6f))
            }
        }

        // Chat Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages.filter { it.role != "system" }) { msg ->
                val isUser = msg.role == "user"
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(
                                topStart = 12.dp,
                                topEnd = 12.dp,
                                bottomStart = if (isUser) 12.dp else 0.dp,
                                bottomEnd = if (isUser) 0.dp else 12.dp
                            ))
                            .background(if (isUser) theme.accentColor.copy(alpha = 0.2f) else theme.keyColor.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = msg.content,
                            color = theme.textColor,
                            fontSize = 14.sp
                        )
                        if (!isUser) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                IconButton(
                                    onClick = { onInsert(msg.content) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Insert",
                                        tint = theme.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                item {
                    Box(modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = theme.accentColor,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Input Area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = theme.keyColor.copy(alpha = 0.2f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(theme.backgroundColor)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (inputText.isEmpty()) {
                        Text("Ask me anything...", color = theme.textColor.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = theme.textColor, fontSize = 14.sp),
                        cursorBrush = SolidColor(theme.accentColor)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && !isLoading) {
                            val userMsg = inputText
                            messages.add(Message("user", userMsg))
                            inputText = ""
                            isLoading = true
                            scope.launch {
                                val response = AiRepository.chat(messages.toList())
                                messages.add(Message("assistant", response))
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(theme.accentColor)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
