package com.gemini.notion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.gemini.notion.ui.EditorScreen
import com.gemini.notion.ui.HomeScreen
import com.gemini.notion.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val navController = rememberNavController()
            val viewModel: NoteViewModel = viewModel(factory = NoteViewModel.Factory)

            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        onNoteClick = { noteId ->
                            viewModel.loadNote(noteId)
                            navController.navigate("editor")
                        },
                        onSettingsClick = {
                            navController.navigate("settings")
                        }
                    )
                }
                composable("editor") {
                    EditorScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
