package com.zai.native_app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.zai.native_app.data.TokenManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    tokenManager: TokenManager
) {
    val context = LocalContext.current
    val selectedModel by tokenManager.selectedModel.collectAsState(initial = "glm-5.3")
    val scope = rememberCoroutineScope()

    val models = listOf(
        "glm-5.3" to "GLM-5.3 (mejor)",
        "x-preview-l" to "GLM-5.3-Flash (gratis)",
        "glm-5.2" to "GLM-5.2",
        "GLM-5-Turbo" to "GLM-5-Turbo",
        "glm-4.7" to "GLM-4.7",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0A))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Modelo",
                color = Color(0xFF999999),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            models.forEach { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { tokenManager.setSelectedModel(id) }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedModel == id,
                        onClick = {
                            scope.launch { tokenManager.setSelectedModel(id) }
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color(0xFF3B82F6)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(name, color = Color.White, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider(color = Color(0xFF222222))
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Cuenta",
                color = Color(0xFF999999),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(
                onClick = {
                    scope.launch {
                        tokenManager.clearToken()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFDC2626)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cerrar sesión", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Acerca de",
                color = Color(0xFF999999),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Z.ai Native v1.0.0",
                color = Color(0xFF666666),
                fontSize = 14.sp
            )
            Text(
                "Cliente nativo para chat.z.ai",
                color = Color(0xFF444444),
                fontSize = 13.sp
            )
        }
    }
}

