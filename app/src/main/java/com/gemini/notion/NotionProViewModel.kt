package com.gemini.notion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemini.notion.data.Note
import com.gemini.notion.data.NoteRepository
import com.gemini.notion.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotionProViewModel(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // --- State ---
    val allNotes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val apiKeyFlow: StateFlow<String> = settingsRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Editor State
    var currentNoteId by mutableStateOf<Int?>(null)
    var title by mutableStateOf("")
    var content by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // --- Actions ---

    fun loadNote(id: Int) {
        if (id == -1) { // New Note
            currentNoteId = null
            title = ""
            content = ""
            return
        }
        
        viewModelScope.launch {
            val note = repository.getNote(id)
            if (note != null) {
                currentNoteId = note.id
                title = note.title
                content = note.content
            }
        }
    }

    fun saveNote() {
        if (title.isBlank()) return // Don't save empty titles
        
        viewModelScope.launch {
            val note = Note(
                id = currentNoteId ?: 0,
                title = title,
                content = content
            )
            repository.saveNote(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settingsRepository.saveApiKey(key)
        }
    }

    // --- AI Logic ---
    fun callGemini(promptType: String) {
        val key = apiKeyFlow.value
        if (key.isBlank()) {
            errorMessage = "Please enter your Gemini API Key in Settings first."
            return
        }
        
        isLoading = true
        errorMessage = null

        val prompt = when (promptType) {
            "Continue" -> "Continue writing this note naturally. Context: Title: $title. Content: $content"
            "Summarize" -> "Summarize this note in bullet points. Context: $content"
            "Fix" -> "Fix grammar and spelling. Context: $content"
            else -> content
        }

        viewModelScope.launch {
            try {
                val response = GeminiClient.service.generateContent(
                    apiKey = key,
                    request = GenerationRequest(listOf(Content(listOf(Part(prompt)))))
                )
                
                val resultText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (resultText != null) {
                    if (promptType == "Continue") {
                        content += " $resultText"
                    } else {
                        content += "\n\n--- AI ($promptType) ---\n$resultText"
                    }
                    saveNote() // Auto-save after AI
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
