package com.zai.native_app.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.mikepenz.markdown.m3.Markdown
import com.zai.native_app.data.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    tokenManager: TokenManager,
    chatId: String? = null,
    viewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val messages by viewModel.messages.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val currentResponse by viewModel.currentResponse.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedModel by tokenManager.selectedModel.collectAsState(initial = "x-preview-l")
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(chatId) {
        if (chatId != null) viewModel.loadChat(chatId, context)
        else viewModel.newChat()
    }

    LaunchedEffect(messages.size, currentResponse) {
        val total = messages.size + if (currentResponse.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Z.ai", color = Color.White, fontSize = 18.sp)
                        Text(
                            when(selectedModel) {
                                "x-preview-l" -> "GLM-5.3-Flash"
                                "glm-5.3" -> "GLM-5.3"
                                "glm-5.2" -> "GLM-5.2"
                                "GLM-5-Turbo" -> "GLM-5-Turbo"
                                "glm-4.7" -> "GLM-4.7"
                                else -> selectedModel
                            },
                            color = Color(0xFF888888), fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Regenerate button
                    if (messages.isNotEmpty() && !isStreaming) {
                        IconButton(onClick = {
                            val lastUserMsg = messages.lastOrNull { it.role == "user" }
                            if (lastUserMsg != null) {
                                viewModel.regenerateLast(tokenManager, context)
                            }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        viewModel.newChat()
                        navController.navigate("chat") {
                            popUpTo("chat_list") { inclusive = false }
                            launchSingleTop = true
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A))
            )
        },
        bottomBar = {
            Column {
                if (error != null) {
                    Text(
                        text = error!!,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF2A1A1A)).padding(8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
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
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSend = {
                                if (inputText.isNotBlank() && !isStreaming) {
                                    viewModel.sendMessage(inputText, tokenManager, context)
                                    inputText = ""
                                    keyboard?.hide()
                                }
                            }
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isStreaming) {
                        FloatingActionButton(
                            onClick = { viewModel.stopStreaming() },
                            containerColor = Color(0xFFDC2626),
                            modifier = Modifier.size(48.dp)
                        ) { Text("⏹", color = Color.White, fontSize = 16.sp) }
                    } else {
                        FloatingActionButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText, tokenManager, context)
                                    inputText = ""
                                    keyboard?.hide()
                                }
                            },
                            containerColor = Color(0xFF3B82F6),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)).padding(padding)
        ) {
            if (messages.isEmpty() && currentResponse.isEmpty() && !isStreaming) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("Envía un mensaje para empezar", color = Color(0xFF666666), fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message -> MessageBubble(message, context) }
                    if (currentResponse.isNotEmpty()) {
                        item { MessageBubble(ChatMessage("assistant", currentResponse), context) }
                    }
                    if (isStreaming && currentResponse.isEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF3B82F6))
                                Spacer(Modifier.width(8.dp))
                                Text("Pensando...", color = Color(0xFF666666), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage, context: android.content.Context) {
    val isUser = message.role == "user"
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                color = if (isUser) Color(0xFF1E3A5F) else Color(0xFF1A1A1A),
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .combinedClickable(onClick = {}, onLongClick = { showMenu = true })
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = Color.White, fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                } else {
                    Markdown(content = message.content, modifier = Modifier.padding(12.dp))
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Copiar") },
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", message.content))
                        showMenu = false
                    }
                )
                if (!isUser) {
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, message.content)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir"))
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
