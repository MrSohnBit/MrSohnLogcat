package com.mrsohn.mrsohnlogcat.component

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LabelTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.widthIn(min = 46.dp)
    )
}