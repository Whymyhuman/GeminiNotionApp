package com.gemini.notion

import android.app.Application
import com.gemini.notion.data.NoteRepository
import com.gemini.notion.data.SettingsRepository

class GeminiApp : Application() {
    // val database by lazy { AppDatabase.getDatabase(this) } // Disabled
    val repository by lazy { NoteRepository() } // Use fake repo
    val settingsRepository by lazy { SettingsRepository(this) }
}