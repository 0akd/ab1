package com.arjun.absolutra.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun KeyboardLayout(
    onKeyPress: (String) -> Unit,
    onDelete: () -> Unit,
    onEnter: () -> Unit,
    marginTop: Int = 8,
    marginBottom: Int = 8,
    marginLeft: Int = 0,
    marginRight: Int = 0,
    rowSpacing: Int = 0,
    horizontalSpacing: Int = 8,
    verticalSpacing: Int = 8,
    rowHeight: Int = 50,
    widthLeft: Float = 1f,
    widthMiddle: Float = 1f,
    widthRight: Float = 1f
) {
    val rows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m", "DEL"),
        listOf("SPACE", "ENTER")
    )

    val leftKeys = setOf("q", "w", "e", "a", "s", "d", "z", "x", "c")
    val middleKeys = setOf("r", "t", "y", "u", "f", "g", "h", "v", "b", "n")
    val rightKeys = setOf("i", "o", "p", "j", "k", "l", "m", "DEL")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(
                top = marginTop.dp,
                bottom = marginBottom.dp,
                start = marginLeft.dp,
                end = marginRight.dp
            )
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (index < rows.size - 1) rowSpacing.dp else 0.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { key ->
                    val baseWeight = when (key) {
                        "SPACE" -> 3f
                        "DEL", "ENTER" -> 1.5f
                        else -> 1f
                    }

                    val groupMultiplier = when (key) {
                        in leftKeys -> widthLeft
                        in middleKeys -> widthMiddle
                        in rightKeys -> widthRight
                        else -> 1f
                    }

                    KeyItem(
                        text = key,
                        height = rowHeight,
                        horizontalSpacing = horizontalSpacing,
                        verticalSpacing = verticalSpacing,
                        modifier = Modifier.weight(baseWeight * groupMultiplier),
                        onClick = {
                            when (key) {
                                "DEL" -> onDelete()
                                "SPACE" -> onKeyPress(" ")
                                "ENTER" -> onEnter()
                                else -> onKeyPress(key)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun KeyItem(
    text: String,
    height: Int,
    horizontalSpacing: Int,
    verticalSpacing: Int,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .padding(
                horizontal = (horizontalSpacing / 2f).dp,
                vertical = (verticalSpacing / 2f).dp
            )
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
