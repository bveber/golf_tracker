package com.golftracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NumberStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange = 0..99,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 48.dp,
    textStyle: TextStyle? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(value.toString()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Sync editText when value changes externally
    LaunchedEffect(value) {
        if (!isEditing) editText = value.toString()
    }

    val style = textStyle ?: MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(
                    enabled = value > range.first,
                    onClick = { onValueChange(value - 1) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(width = buttonSize, height = buttonSize),
            contentAlignment = Alignment.Center
        ) {
            if (isEditing) {
                BasicTextField(
                    value = editText,
                    onValueChange = { newText ->
                        // Only allow digits
                        editText = newText.filter { it.isDigit() }.take(3)
                    },
                    textStyle = style.copy(
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = editText.toIntOrNull()?.coerceIn(range) ?: value
                            onValueChange(parsed)
                            isEditing = false
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .width(buttonSize)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (!state.isFocused && isEditing) {
                                val parsed = editText.toIntOrNull()?.coerceIn(range) ?: value
                                onValueChange(parsed)
                                isEditing = false
                            }
                        }
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }
            } else {
                Text(
                    text = value.toString(),
                    style = style,
                    modifier = Modifier.clickable {
                        editText = value.toString()
                        isEditing = true
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    enabled = value < range.last,
                    onClick = { onValueChange(value + 1) }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}
