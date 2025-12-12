package com.gemini.notion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.notion.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var showAiMenu by remember { mutableStateOf(false) }

    // Auto-save when back is pressed (handled by caller mostly, but let's ensure save on dispose if needed)
    // For simplicity, we save whenever text changes? No, that's too heavy. Save on Back.
    
    DisposableEffect(Unit) {
        onDispose { viewModel.saveNote() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveNote()
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAiMenu = true }) {
                        if (viewModel.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "AI")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showAiMenu) {
            AlertDialog(
                onDismissRequest = { showAiMenu = false },
                title = { Text("AI Assistant") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.callGemini("Continue"); showAiMenu = false }) { Text("Continue Writing") }
                        Button(onClick = { viewModel.callGemini("Summarize"); showAiMenu = false }) { Text("Summarize") }
                        Button(onClick = { viewModel.callGemini("Fix"); showAiMenu = false }) { Text("Fix Grammar") }
                    }
                },
                confirmButton = {}
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
             if (viewModel.errorMessage != null) {
                Text(viewModel.errorMessage!!, color = Color.Red, modifier = Modifier.padding(8.dp))
            }

            TextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                placeholder = { Text("Untitled", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Gray) },
                textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextField(
                value = viewModel.content,
                onValueChange = { viewModel.content = it },
                placeholder = { Text("Start typing...", fontSize = 18.sp, color = Color.Gray) },
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 500.dp)
            )
        }
    }
}
