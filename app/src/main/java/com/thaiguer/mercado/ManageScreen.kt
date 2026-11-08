package com.thaiguer.mercado

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.thaiguer.mercado.core.MercadoItem

@Composable
fun ManageScreen(
    items: List<MercadoItem>,
    onClearSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onClearQuantity: () -> Unit,
    onClearPrices: () -> Unit,
    onClearAll: () -> Unit,

    repositoryUrl: String,
    token: String,
    branch: String,
    filePath: String,
    onSaveCloudConfig: (String, String, String, String) -> Unit,

    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
    syncMessage: String,
    syncBusy: Boolean,
    onSyncNow: () -> Unit
) {
    var urlInput by remember(repositoryUrl) {
        mutableStateOf(repositoryUrl)
    }

    var tokenInput by remember(token) {
        mutableStateOf(token)
    }

    var branchInput by remember(branch) {
        mutableStateOf(branch)
    }

    var filePathInput by remember(filePath) {
        mutableStateOf(filePath)
    }

    var showDialog by remember {
        mutableStateOf(false)
    }

    var action by remember {
        mutableStateOf("")
    }

    fun requestConfirmation(type: String) {
        action = type
        showDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Management",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Products: ${items.size}")

        HorizontalDivider()

        Text(
            "GitHub synchronization",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = {
                urlInput = it
            },
            label = {
                Text("Repository URL")
            },
            placeholder = {
                Text("https://github.com/user/repository")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = tokenInput,
            onValueChange = {
                tokenInput = it
            },
            label = {
                Text("Personal Access Token")
            },
            placeholder = {
                Text("Paste your GitHub token")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = branchInput,
            onValueChange = {
                branchInput = it
            },
            label = {
                Text("Branch")
            },
            placeholder = {
                Text("main")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = filePathInput,
            onValueChange = {
                filePathInput = it
            },
            label = {
                Text("JSON file path")
            },
            placeholder = {
                Text("data/mercado.json")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveCloudConfig(
                    urlInput.trim(),
                    tokenInput.trim(),
                    branchInput.trim(),
                    filePathInput.trim()
                )
            },
            enabled = urlInput.isNotBlank() &&
                    tokenInput.isNotBlank() &&
                    branchInput.isNotBlank() &&
                    filePathInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save GitHub settings")
        }

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = syncEnabled,
                onCheckedChange = onSyncEnabledChange
            )

            Text("Sync to GitHub")
        }

        Text(
            syncMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (
                syncMessage.contains("failed", ignoreCase = true) ||
                syncMessage.contains("error", ignoreCase = true)
            ) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Button(
            onClick = onSyncNow,
            enabled = syncEnabled &&
                    !syncBusy &&
                    repositoryUrl.isNotBlank() &&
                    token.isNotBlank() &&
                    branch.isNotBlank() &&
                    filePath.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (syncBusy) {
                    "Synchronizing..."
                } else {
                    "Sync now"
                }
            )
        }

        HorizontalDivider()

        Text(
            "Product management",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            onClick = {
                requestConfirmation("removeSelected")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.ok },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Remove selected items (${items.count { it.ok }})")
        }

        Button(
            onClick = onClearSelected,
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.ok }
        ) {
            Text("Uncheck selected items (${items.count { it.ok }})")
        }

        Button(
            onClick = {
                requestConfirmation("quantity")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.count != 0 }
        ) {
            Text("Clear quantity")
        }

        Button(
            onClick = {
                requestConfirmation("prices")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.any { it.price != 0.0 }
        ) {
            Text("Clear prices")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                requestConfirmation("all")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = items.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Clear ALL")
        }
    }

    if (showDialog) {
        val message = when (action) {
            "removeSelected" ->
                "Remove all selected products permanently?"

            "quantity" ->
                "Reset the quantity of all products to zero?"

            "prices" ->
                "Reset all product prices to zero?"

            "all" ->
                "Are you sure you want to delete ALL products?"

            else -> ""
        }

        AlertDialog(
            onDismissRequest = {
                showDialog = false
            },
            title = {
                Text("Confirm action")
            },
            text = {
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (action) {
                            "removeSelected" -> onRemoveSelected()
                            "quantity" -> onClearQuantity()
                            "prices" -> onClearPrices()
                            "all" -> onClearAll()
                        }

                        showDialog = false
                    }
                ) {
                    Text(
                        "Confirm",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}