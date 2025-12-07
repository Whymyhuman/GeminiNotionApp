package com.gemini.notion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF000000), // Notion style black
                    secondary = Color(0xFFE0E0E0),
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotionScreen()
                }
            }
        }
    }
}

// --- ViewModel ---

class NoteViewModel : ViewModel() {
    var apiKey by mutableStateOf("")
    var title by mutableStateOf("Untitled")
    var content by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun callGemini(promptType: String) {
        if (apiKey.isBlank()) {
            errorMessage = "Please enter your Gemini API Key first."
            return
        }
        
        isLoading = true
        errorMessage = null

        val prompt = when (promptType) {
            "Continue" -> "Continue writing this note naturally, keeping the same tone. Do not repeat the existing text, just continue:\n\n$content"
            "Summarize" -> "Summarize the following note in bullet points:\n\n$content"
            "Fix" -> "Fix grammar and spelling in the following text, keeping the original meaning:\n\n$content"
            else -> content
        }

        viewModelScope.launch {
            try {
                val response = GeminiClient.service.generateContent(
                    apiKey = apiKey,
                    request = GenerationRequest(listOf(Content(listOf(Part(prompt)))))
                )
                
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (resultText != null) {
                    if (promptType == "Continue") {
                        content += " $resultText"
                    } else {
                        // For summary or fix, we might want to append or replace. 
                        // For this Notion-lite, let's append nicely.
                        content += "\n\n--- AI ($promptType) ---\n$resultText"
                    }
                } else {
                    errorMessage = "AI returned no content."
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

// --- UI Components ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotionScreen(viewModel: NoteViewModel = viewModel()) {
    var showAiMenu by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiMenu = true },
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Magic")
                }
            }
        },
        bottomBar = {
            // API Key Input Area
            Surface(shadowElevation = 8.dp) {
                TextField(
                    value = viewModel.apiKey,
                    onValueChange = { viewModel.apiKey = it },
                    label = { Text("Gemini API Key (Required)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF7F7F5),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true
                )
            }
        }
    ) { paddingValues ->
        
        if (showAiMenu) {
            AlertDialog(
                onDismissRequest = { showAiMenu = false },
                title = { Text("AI Assistant") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.callGemini("Continue"); showAiMenu = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Continue Writing") }
                        
                        Button(
                            onClick = { viewModel.callGemini("Summarize"); showAiMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) { Text("Summarize") }
                        
                        Button(
                            onClick = { viewModel.callGemini("Fix"); showAiMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                        ) { Text("Fix Grammar") }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showAiMenu = false }) { Text("Close") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
            // Error Banner
            if (viewModel.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.errorMessage!!,
                            color = Color.Red,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.errorMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Red)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title Input (Bold, Large)
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

            Spacer(modifier = Modifier.height(16.dp))

            // Content Input
            TextField(
                value = viewModel.content,
                onValueChange = { viewModel.content = it },
                placeholder = { Text("Press '/' for commands...", fontSize = 18.sp, color = Color.Gray) },
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 400.dp)
            )
            
            Spacer(modifier = Modifier.height(100.dp)) // Bottom padding for FAB
        }
    }
}
