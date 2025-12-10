package com.gemini.notion

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gemini.notion.data.AppDatabase
import com.gemini.notion.data.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val viewModelFactory = NoteViewModelFactory(application, database)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF000000),
                    secondary = Color(0xFFE0E0E0),
                    background = Color(0xFFFFFFFF),
                    surface = Color(0xFFFFFFFF),
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(viewModelFactory)
                }
            }
        }
    }
}

// --- ViewModel Factory ---
class NoteViewModelFactory(
    private val application: Application,
    private val database: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(application, database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- ViewModel ---

class NoteViewModel(application: Application, database: AppDatabase) : AndroidViewModel(application) {
    private val noteDao = database.noteDao()
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    var apiKey by mutableStateOf("") // In production, use DataStore
    
    // Editor State
    var currentNoteId by mutableStateOf<Int?>(null)
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Navigation State
    var currentScreen by mutableStateOf("list") // "list" or "edit"

    fun openNote(note: Note?) {
        if (note == null) {
            currentNoteId = null
            title = ""
            content = ""
        } else {
            currentNoteId = note.id
            title = note.title
            content = note.content
        }
        currentScreen = "edit"
    }

    fun saveCurrentNote() {
        if (title.isBlank() && content.isBlank()) return
        val note = Note(
            id = currentNoteId ?: 0,
            title = if (title.isBlank()) "Untitled" else title,
            content = content
        )
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.insertNote(note)
        }
        currentScreen = "list"
    }
    
    fun deleteNote(note: Note) {
         viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNote(note)
        }
    }

    fun callGemini(promptType: String, context: android.content.Context) {
        if (apiKey.isBlank()) {
            errorMessage = "Please enter Gemini API Key first (Settings)."
            return
        }
        
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val finalPrompt = when (promptType) {
                    "Continue" -> "Continue writing this note naturally:\n\n$content"
                    "Summarize" -> "Summarize in bullet points:\n\n$content"
                    "Fix" -> "Fix grammar:\n\n$content"
                    "Plan" -> "Create a time-blocked schedule based on these tasks/notes. Format: Time - Task.\n\n$content"
                    "Alarm" -> "Extract the time (HH:MM in 24h format) and a short reminder message from this text. If multiple times, pick the first one. Return ONLY this format: HH:MM|Message. Do not add any other text.\n\nText: $content"
                    else -> content
                }

                val response = GeminiClient.service.generateContent(
                    apiKey = apiKey,
                    request = GenerationRequest(listOf(Content(listOf(Part(finalPrompt)))))
                )
                
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                
                if (resultText != null) {
                    when (promptType) {
                        "Alarm" -> handleAlarmResponse(resultText, context)
                        "Continue" -> content += " $resultText"
                        else -> content += "\n\n--- AI ($promptType) ---\n$resultText"
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

    private fun handleAlarmResponse(response: String, context: android.content.Context) {
        // Expected format: HH:MM|Message
        try {
            val parts = response.split("|")
            if (parts.size >= 2) {
                val timeParts = parts[0].trim().split(":")
                val hour = timeParts[0].toInt()
                val minute = timeParts[1].toInt()
                val message = parts[1].trim()
                
                AlarmUtils.setAlarm(context, hour, minute, message)
            } else {
                errorMessage = "AI couldn't extract time. Format received: $response"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to set alarm: ${e.message}. AI said: $response"
        }
    }
}

// --- UI Navigation ---

@Composable
fun AppNavigation(viewModelFactory: NoteViewModelFactory) {
    val viewModel: NoteViewModel = viewModel(factory = viewModelFactory)

    if (viewModel.currentScreen == "list") {
        NoteListScreen(viewModel)
    } else {
        NoteEditScreen(viewModel)
    }
}

// --- List Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(viewModel: NoteViewModel) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openNote(null) },
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, "New Note")
            }
        },
        bottomBar = {
             Surface(shadowElevation = 8.dp) {
                TextField(
                    value = viewModel.apiKey,
                    onValueChange = { viewModel.apiKey = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color(0xFFF7F7F5)
                    ),
                    singleLine = true
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Notes", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(notes) { note ->
                    NoteItem(note, onClick = { viewModel.openNote(note) }, onDelete = { viewModel.deleteNote(note) })
                }
            }
        }
    }
}

@Composable
fun NoteItem(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7F5))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(note.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    note.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Gray)
            }
        }
    }
}

// --- Edit Screen ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(viewModel: NoteViewModel) {
    var showAiMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Auto-save when back pressed is handled manually by the Back Handler
    // utilizing the BackButton in the TopBar for simplicity in this MVP
    
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.saveCurrentNote() }) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiMenu = true },
                containerColor = Color.Black,
                contentColor = Color.White
            ) {
                 if (viewModel.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, "AI Magic")
                }
            }
        }
    ) {
        
        if (showAiMenu) {
             AlertDialog(
                onDismissRequest = { showAiMenu = false },
                title = { Text("AI Assistant") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.callGemini("Continue", context); showAiMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Continue Writing") }
                        Button(onClick = { viewModel.callGemini("Summarize", context); showAiMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Summarize") }
                        Button(onClick = { viewModel.callGemini("Fix", context); showAiMenu = false }, modifier = Modifier.fillMaxWidth()) { Text("Fix Grammar") }
                        Divider()
                        Button(onClick = { viewModel.callGemini("Plan", context); showAiMenu = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D47A1))) { 
                             Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text("Plan My Day") 
                        }
                        Button(onClick = { viewModel.callGemini("Alarm", context); showAiMenu = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))) { 
                             Icon(Icons.Default.Alarm, null); Spacer(Modifier.width(8.dp)); Text("Set Smart Alarm") 
                        }
                    }
                },
                confirmButton = {}, 
                dismissButton = { TextButton(onClick = { showAiMenu = false }) { Text("Close") } }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState)
        ) {
             if (viewModel.errorMessage != null) {
                Text(viewModel.errorMessage!!, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Title
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

            // Content
            TextField(
                value = viewModel.content,
                onValueChange = { viewModel.content = it },
                placeholder = { Text("Start typing... Use AI for magic.", fontSize = 18.sp, color = Color.Gray) },
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 28.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 500.dp)
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}