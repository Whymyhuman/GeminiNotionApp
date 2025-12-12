package com.gemini.notion.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoteRepository {
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val allNotes: Flow<List<Note>> = _notes.asStateFlow()

    private var nextId = 1

    suspend fun getNote(id: Int): Note? {
        return _notes.value.find { it.id == id }
    }

    suspend fun saveNote(note: Note) {
        val currentList = _notes.value.toMutableList()
        if (note.id == 0) {
            currentList.add(note.copy(id = nextId++))
        } else {
            val index = currentList.indexOfFirst { it.id == note.id }
            if (index != -1) {
                currentList[index] = note
            }
        }
        _notes.value = currentList
    }

    suspend fun deleteNote(note: Note) {
        val currentList = _notes.value.toMutableList()
        currentList.removeIf { it.id == note.id }
        _notes.value = currentList
    }
}
