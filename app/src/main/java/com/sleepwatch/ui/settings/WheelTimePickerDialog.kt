package com.sleepwatch.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun WheelTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedHour by remember(initialHour) {
        mutableIntStateOf(initialHour.coerceIn(0, 23))
    }
    var selectedMinute by remember(initialMinute) {
        mutableIntStateOf(initialMinute.coerceIn(0, 59))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 320.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "上下滑动选择时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WheelPicker(
                        items = (0..23).toList(),
                        initialIndex = selectedHour,
                        onItemSelected = { selectedHour = it },
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    WheelPicker(
                        items = (0..59).toList(),
                        initialIndex = selectedMinute,
                        onItemSelected = { selectedMinute = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHour, selectedMinute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun WheelPicker(
    items: List<Int>,
    initialIndex: Int,
    onItemSelected: (Int) -> Unit,
) {
    val itemHeight = 42.dp
    val visibleItems = 5
    val safeInitialIndex = initialIndex.coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
    val coroutineScope = rememberCoroutineScope()
    val centeredIndex by remember {
        derivedStateOf { findCenteredItemIndex(listState, items.size) }
    }

    // Initialize the list at the requested value. This also explicitly emits 0,
    // so selecting 00:00 cannot be mistaken for an unset value.
    LaunchedEffect(safeInitialIndex) {
        listState.scrollToItem(safeInitialIndex)
        onItemSelected(safeInitialIndex)
    }

    // Select and snap to the row nearest the actual viewport center. Do not use
    // dp/pixel comparisons or firstVisibleItemIndex, both of which fail at edges.
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val targetIndex = findCenteredItemIndex(listState, items.size)
            if (targetIndex != null) {
                onItemSelected(targetIndex)
                val targetItem = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == targetIndex }
                val viewportCenter = (
                    listState.layoutInfo.viewportStartOffset +
                        listState.layoutInfo.viewportEndOffset
                    ) / 2
                if (targetItem != null &&
                    abs(targetItem.offset + targetItem.size / 2 - viewportCenter) > 1
                ) {
                    coroutineScope.launch { listState.animateScrollToItem(targetIndex) }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .width(78.dp)
            .height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center,
    ) {
        // Lightweight Cupertino-style selected row, with no large gradient block.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(10.dp),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(Color.Transparent),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = itemHeight * 2),
        ) {
            itemsIndexed(items) { index, value ->
                val isSelected = centeredIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable {
                            // Taps update immediately, including first/last values.
                            onItemSelected(index)
                            coroutineScope.launch {
                                listState.animateScrollToItem(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "%02d".format(value),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (isSelected) 21.sp else 18.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
                        },
                        modifier = Modifier.alpha(if (isSelected) 1f else 0.9f),
                    )
                }
            }
        }
    }
}

private fun findCenteredItemIndex(
    listState: LazyListState,
    itemCount: Int,
): Int? {
    if (itemCount == 0) return null
    val viewportCenter = (
        listState.layoutInfo.viewportStartOffset +
            listState.layoutInfo.viewportEndOffset
        ) / 2
    return listState.layoutInfo.visibleItemsInfo
        .asSequence()
        .filter { it.index in 0 until itemCount }
        .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
        ?.index
}
