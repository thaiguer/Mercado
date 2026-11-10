package com.thaiguer.mercado

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.thaiguer.mercado.core.MercadoItem

import java.text.Normalizer
import java.util.Locale

@Composable
fun HomeScreen(
    products: List<MercadoItem>,
    onAdd: (MercadoItem) -> Unit,
    onUpdate: (MercadoItem) -> Unit,
    onRemove: (String) -> Unit
) {
    var showAddDialog by remember {
        mutableStateOf(false)
    }

    var itemToRemove by remember {
        mutableStateOf<MercadoItem?>(null)
    }

    var searchQuery by remember {
        mutableStateOf("")
    }

    val total = products.sumOf { it.total }

    val purchased = products
        .filter { it.ok }
        .sumOf { it.total }

    val totalVolumes = products
        .filter { it.ok }
        .sumOf { it.count }

    val filteredProducts = products.filter {
        it.name.normalizeForSearch()
            .contains(searchQuery.normalizeForSearch())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp)
    ) {
        Text(
            "Shopping List",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Text(
                    "Total: %.2f".format(total),
                    style = MaterialTheme.typography.titleLarge
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Purchased: %.2f".format(purchased),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "Total Volumes: ${products.filter { it.ok }.sumOf { it.count }}"
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                showAddDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add product")
        }

        Spacer(Modifier.height(8.dp))

        // Search is directly below Add product.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
            },
            label = {
                Text("Search products")
            },
            placeholder = {
                Text("Enter product name")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                filteredProducts,
                key = { it.id }
            ) { item ->

                var priceText by remember(item.id) {
                    mutableStateOf(item.price.toString())
                }

                val bringIntoViewRequester = remember {
                    BringIntoViewRequester()
                }

                val scope = rememberCoroutineScope()

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = item.ok,
                                    onCheckedChange = {
                                        onUpdate(item.copy(ok = it))
                                    }
                                )

                                Column {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    Text(
                                        "Subtotal: %.2f".format(item.total)
                                    )
                                }
                            }

                            TextButton(
                                onClick = {
                                    itemToRemove = item
                                }
                            ) {
                                Text("Remove")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { value ->
                                val normalized = value.replace(",", ".")

                                if (
                                    normalized.isEmpty() ||
                                    normalized.matches(
                                        Regex("^\\d*\\.?\\d{0,2}$")
                                    )
                                ) {
                                    priceText = value

                                    val newPrice =
                                        normalized.toDoubleOrNull()

                                    if (newPrice != null) {
                                        onUpdate(
                                            item.copy(price = newPrice)
                                        )
                                    } else if (normalized.isEmpty()) {
                                        onUpdate(
                                            item.copy(price = 0.0)
                                        )
                                    }
                                }
                            },
                            label = {
                                Text("Unit price")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier
                                .width(160.dp)
                                .bringIntoViewRequester(
                                    bringIntoViewRequester
                                )
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            bringIntoViewRequester
                                                .bringIntoView()
                                        }
                                    }
                                }
                        )

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = {
                                    if (item.count > 0) {
                                        onUpdate(
                                            item.copy(
                                                count = item.count - 1
                                            )
                                        )
                                    }
                                }
                            ) {
                                Text("-")
                            }

                            Text(
                                "Quantity: ${item.count}",
                                modifier = Modifier.padding(top = 12.dp)
                            )

                            TextButton(
                                onClick = {
                                    onUpdate(
                                        item.copy(
                                            count = item.count + 1
                                        )
                                    )
                                }
                            ) {
                                Text("+")
                            }
                        }
                    }
                }
            }
        }
    }

    itemToRemove?.let { item ->
        AlertDialog(
            onDismissRequest = {
                itemToRemove = null
            },
            title = {
                Text("Remove product?")
            },
            text = {
                Text("Are you sure you want to remove \"${item.name}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(item.id)
                        itemToRemove = null
                    }
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        itemToRemove = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        var name by remember {
            mutableStateOf("")
        }

        var count by remember {
            mutableStateOf("1")
        }

        var price by remember {
            mutableStateOf("")
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
            },
            title = {
                Text("Add product")
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                        },
                        label = {
                            Text("Product name")
                        },
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = count,
                        onValueChange = {
                            if (it.all(Char::isDigit)) {
                                count = it
                            }
                        },
                        label = {
                            Text("Quantity")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    OutlinedTextField(
                        value = price,
                        onValueChange = { value ->
                            val normalized = value.replace(",", ".")

                            if (
                                normalized.isEmpty() ||
                                normalized.matches(
                                    Regex("^\\d*\\.?\\d{0,2}$")
                                )
                            ) {
                                price = value
                            }
                        },
                        label = {
                            Text("Unit price")
                        },
                        placeholder = {
                            Text("0.00")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newQuantity = count.toIntOrNull()
                        val newPrice = price
                            .replace(",", ".")
                            .toDoubleOrNull() ?: 0.0

                        if (
                            name.isNotBlank() &&
                            newQuantity != null &&
                            newQuantity > 0
                        ) {
                            onAdd(
                                MercadoItem(
                                    id = "",
                                    name = name.trim(),
                                    count = newQuantity,
                                    price = newPrice
                                )
                            )

                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank() &&
                            (count.toIntOrNull() ?: 0) > 0
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun String.normalizeForSearch(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .trim()
}