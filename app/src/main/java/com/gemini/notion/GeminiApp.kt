package com.gemini.notion

import android.app.Application
import com.gemini.notion.data.AppDatabase
import com.gemini.notion.data.NoteRepository
import com.gemini.notion.data.SettingsRepository

class GeminiApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { NoteRepository(database.noteDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
