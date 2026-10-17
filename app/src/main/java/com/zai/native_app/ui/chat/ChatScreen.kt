package com.zai.native_app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mikepenz.markdown.m3.Markdown
import com.zai.native_app.data.TokenManager
import okhttp3.sse.EventSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    tokenManager: TokenManager,
    chatId: String? = null,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val error by viewModel.error.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Initialize and load chat
    LaunchedEffect(chatId) {
        if (chatId != null) {
            viewModel.loadChat(chatId, context)
        } else {
            viewModel.newChat()
        }
    }

    // Auto-scroll
    LaunchedEffect(messages.size, currentResponse) {
        val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Z.ai", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.newChat()
                        navController.navigate("chat") {
                            popUpTo("chat_list") { inclusive = false }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        bottomBar = {
            Column {
                if (error != null) {
                    Text(
                        text = error!!,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF2A1A1A))
                            .padding(8.dp)
                    )
                }
                BottomBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank() && !isStreaming) {
                            viewModel.sendMessage(inputText, tokenManager, context)
                            inputText = ""
                        }
                    },
                    isStreaming = isStreaming,
                    onStop = { viewModel.stopStreaming() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
                .padding(paddingValues)
        ) {
            if (messages.isEmpty() && currentResponse.isEmpty() && !isStreaming) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Envía un mensaje para empezar",
                        color = Color(0xFF666666),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message)
                    }
                    if (currentResponse.isNotEmpty()) {
                        item {
                            MessageBubble(ChatMessage("assistant", currentResponse))
                        }
                    }
                    if (isStreaming && currentResponse.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isStreaming: Boolean,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A))
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Mensaje...", color = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(24.dp),
            maxLines = 5
        )
        Spacer(modifier = Modifier.width(8.dp))
        if (isStreaming) {
            FloatingActionButton(
                onClick = onStop,
                containerColor = Color(0xFFDC2626),
                modifier = Modifier.size(48.dp)
            ) {
                Text("⏹", color = Color.White, fontSize = 16.sp)
            }
        } else {
            FloatingActionButton(
                onClick = onSend,
                containerColor = Color(0xFF3B82F6),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Color(0xFF1E3A5F) else Color(0xFF1A1A1A),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            if (isUser) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(12.dp)
                )
            } else {
                // Markdown rendering for assistant messages
                Markdown(
                    content = message.content,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
