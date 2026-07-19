package kr.co.edoli.edolitool.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * A compact numeric control that supports horizontal scrubbing and precise value entry.
 *
 * Dragging across the field changes the value in proportion to the field's actual width.
 * Tapping the field opens a numeric input dialog suitable for touch devices. When [valueRange]
 * is omitted, the progress fill and range validation are disabled.
 */
@Composable
fun ScrubbableValueField(
    label: String,
    value: Float,
    unit: String,
    valueRange: ClosedFloatingPointRange<Float>? = null,
    modifier: Modifier = Modifier,
    formatValue: (Float) -> String = { it.toString() },
    parseValue: (String) -> Float? = { it.trim().replace(',', '.').toFloatOrNull() },
    unboundedDragStep: Float = 0.1f,
    onValueChange: (Float) -> Unit,
) {
    var showEditor by remember { mutableStateOf(false) }
    var editQuery by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val currentValue by rememberUpdatedState(value)
    val density = LocalDensity.current
    val progress = valueRange?.let { range ->
        ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    }
    val shape = RoundedCornerShape(12.dp)

    val applyEditedValue = {
        val editedValue = parseValue(editQuery)
        if (editedValue == null || (valueRange != null && editedValue !in valueRange)) {
            showError = true
        } else {
            onValueChange(editedValue)
            showEditor = false
        }
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .clickable {
                editQuery = formatValue(value)
                showError = false
                showEditor = true
            }
            .pointerInput(valueRange, unboundedDragStep) {
                var draggedValue = currentValue
                detectHorizontalDragGestures(
                    onDragStart = { draggedValue = currentValue },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        draggedValue = valueRange?.let { range ->
                            val draggedFraction = dragAmount / size.width.coerceAtLeast(1)
                            val valueRangeSize = range.endInclusive - range.start
                            (draggedValue + draggedFraction * valueRangeSize)
                                .coerceIn(range.start, range.endInclusive)
                        } ?: draggedValue + dragAmount / density.density * unboundedDragStep
                        onValueChange(draggedValue)
                    },
                )
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            progress?.let { filledProgress ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(filledProgress.coerceAtLeast(0.001f))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = label,
                    modifier = Modifier.padding(start = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${formatValue(value)} $unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showEditor) {
        LaunchedEffect(showEditor) { focusRequester.requestFocus() }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("$label 입력") },
            text = {
                OutlinedTextField(
                    value = editQuery,
                    onValueChange = {
                        editQuery = it
                        showError = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    suffix = { Text(unit) },
                    isError = showError,
                    supportingText = {
                        val rangeText = valueRange?.let { range ->
                            "${formatValue(range.start)}~${formatValue(range.endInclusive)} $unit"
                        }
                        Text(
                            when {
                                showError && rangeText != null -> "범위 안의 숫자를 입력하세요. ($rangeText)"
                                showError -> "유효한 숫자를 입력하세요."
                                rangeText != null -> "입력 범위: $rangeText"
                                else -> "소수점 입력 가능"
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { applyEditedValue() }),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { applyEditedValue() }) { Text("적용") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("취소") }
            },
        )
    }
}
