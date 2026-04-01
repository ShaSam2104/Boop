package com.shashsam.boop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shashsam.boop.data.ProfileItemEntity
import com.shashsam.boop.ui.theme.BoopBrandPurple
import com.shashsam.boop.ui.theme.BoopShapeMedium
import com.shashsam.boop.ui.theme.LocalBoopTokens
import com.shashsam.boop.ui.theme.NeoBrutalistButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileItemDialog(
    existingItem: ProfileItemEntity? = null,
    onSave: (type: String, label: String, value: String, size: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var type by remember { mutableStateOf(existingItem?.type ?: "link") }
    var label by remember { mutableStateOf(existingItem?.label ?: "") }
    var value by remember { mutableStateOf(existingItem?.value ?: "") }
    var size by remember { mutableStateOf(existingItem?.size ?: "half") }

    val isEdit = existingItem != null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = BoopShapeMedium,
        containerColor = LocalBoopTokens.current.dialogSurface,
        title = {
            Text(
                text = if (isEdit) "Edit Item" else "Add Item",
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column {
                // Type selector
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("link" to "Link", "email" to "Email", "phone" to "Phone").forEach { (t, lbl) ->
                        val isSelected = type == t
                        FilterChip(
                            selected = isSelected,
                            onClick = { type = t },
                            label = {
                                Text(
                                    lbl,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Label field
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Label") },
                    placeholder = {
                        Text(
                            when (type) {
                                "email" -> "Work Email"
                                "phone" -> "Mobile"
                                else -> "GitHub"
                            }
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BoopBrandPurple,
                        cursorColor = BoopBrandPurple,
                        focusedLabelColor = BoopBrandPurple
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Value field
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = {
                        Text(
                            when (type) {
                                "email" -> "Email Address"
                                "phone" -> "Phone Number"
                                else -> "URL"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (type) {
                            "email" -> KeyboardType.Email
                            "phone" -> KeyboardType.Phone
                            else -> KeyboardType.Uri
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BoopBrandPurple,
                        cursorColor = BoopBrandPurple,
                        focusedLabelColor = BoopBrandPurple
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Size selector
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("half" to "Half", "full" to "Full").forEach { (s, lbl) ->
                        val isSelected = size == s
                        FilterChip(
                            selected = isSelected,
                            onClick = { size = s },
                            label = {
                                Text(
                                    lbl,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            NeoBrutalistButton(
                onClick = {
                    val trimLabel = label.trim()
                    val trimValue = value.trim()
                    if (trimLabel.isNotEmpty() && trimValue.isNotEmpty()) {
                        onSave(type, trimLabel, trimValue, size)
                    }
                },
                enabled = label.trim().isNotEmpty() && value.trim().isNotEmpty()
            ) {
                Text("Save", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            Row {
                if (isEdit && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "Delete",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
