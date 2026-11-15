package com.example.skydiveapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LoadingLabel(loading: Boolean, label: String, loadingLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        }
        Text(if (loading) loadingLabel else label)
    }
}
