package io.github.lucf15.localai.reader

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.*
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable private data object ExamplesDestination : NavKey
@Serializable private data class ExampleDestination(val id: String) : NavKey

@Composable
internal fun SampleApp() {
    val backStack = rememberNavBackStack(ExamplesDestination)
    val app = LocalContext.current.applicationContext as Application
    NavDisplay(backStack = backStack, onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<ExamplesDestination> { ExamplesScreen(onOpen = { id -> backStack.add(ExampleDestination(id)) }) }
            entry<ExampleDestination> { key ->
                val factory = remember(app, key.id) {
                    viewModelFactory { initializer { ExampleViewModel(app, key.id, createSavedStateHandle()) } }
                }
                val vm: ExampleViewModel = viewModel(factory = factory)
                ExampleScreen(key.id, vm, onBack = { backStack.removeLastOrNull() })
            }
        })
}
