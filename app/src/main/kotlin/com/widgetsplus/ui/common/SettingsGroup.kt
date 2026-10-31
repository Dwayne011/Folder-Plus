package com.widgetsplus.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive settings group: rows sit on `surfaceContainer` with a
 * 2 dp gap, the first and last rows get large outer corners and every row gets
 * small inner corners, so the group reads as one rounded card.
 */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, rows: List<@Composable () -> Unit>) {
    if (rows.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Surface(
                shape = groupItemShape(index, rows.size),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                row()
            }
        }
    }
}

/** Large (24 dp) corners on the outside of the group, small (6 dp) between rows. */
fun groupItemShape(index: Int, count: Int): Shape {
    val large = 24.dp
    val small = 6.dp
    val top = if (index == 0) large else small
    val bottom = if (index == count - 1) large else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Section label above a [SettingsGroup]. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp, bottom = 8.dp),
    )
}
