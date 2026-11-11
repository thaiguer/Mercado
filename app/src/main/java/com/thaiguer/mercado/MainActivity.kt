package com.thaiguer.mercado

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thaiguer.mercado.ui.theme.MercadoTheme
import com.thaiguer.mercado.viewmodel.MercadoViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MercadoTheme {
                MercadoApp()
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: String
) {
    HOME("Shopping List", "🛒"),
    MANAGE("Management", "⚙️")
}

@Composable
fun MercadoApp(
    mercadoViewModel: MercadoViewModel = viewModel()
) {
    val state by mercadoViewModel.uiState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    var currentDestination by rememberSaveable {
        mutableStateOf(AppDestinations.HOME)
    }

    DisposableEffect(lifecycleOwner, mercadoViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mercadoViewModel.onAppResumed()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Text(destination.icon)
                    },
                    label = {
                        Text(destination.label)
                    },
                    selected = currentDestination == destination,
                    onClick = {
                        currentDestination = destination
                    }
                )
            }
        }
    ) {
        when (currentDestination) {

            AppDestinations.HOME -> {
                HomeScreen(
                    products = state.items,

                    onAdd = { item ->
                        mercadoViewModel.addItem(item)
                    },

                    onUpdate = { item ->
                        mercadoViewModel.updateItem(item)
                    },

                    onRemove = { id ->
                        mercadoViewModel.removeItem(id)
                    }
                )
            }

            AppDestinations.MANAGE -> {
                ManageScreen(
                    items = state.items,

                    onClearSelected = {
                        mercadoViewModel.clearSelected()
                    },

                    onRemoveSelected = {
                        mercadoViewModel.removeSelected()
                    },

                    onClearQuantity = {
                        mercadoViewModel.clearQuantity()
                    },

                    onClearPrices = {
                        mercadoViewModel.clearPrices()
                    },

                    onClearAll = {
                        mercadoViewModel.clearAll()
                    },

                    // Firebase configuration

                    firebaseConfig = state.firebaseConfig,

                    onSaveFirebaseConfig = { config ->
                        mercadoViewModel.saveFirebaseConfig(config)
                    },

                    // Firebase authentication

                    email = state.email,

                    onEmailChange = { email ->
                        mercadoViewModel.updateEmail(email)
                    },

                    firebaseUserEmail = state.firebaseUserEmail,

                    onSignIn = { email, password ->
                        mercadoViewModel.signIn(email, password)
                    },

                    onSignOut = {
                        mercadoViewModel.signOut()
                    },

                    // Synchronization

                    syncEnabled = state.syncEnabled,

                    onSyncEnabledChange = { enabled ->
                        mercadoViewModel.setSyncEnabled(enabled)
                    },

                    syncMessage = state.syncMessage,

                    syncBusy = state.syncBusy,

                    onSyncNow = {
                        mercadoViewModel.syncNow()
                    }
                )
            }
        }
    }
}