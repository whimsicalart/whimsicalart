package com.whimsicalart.feature.filters.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.whimsicalart.feature.filters.domain.Filter

@Composable
fun FilterSelector(
    filters: List<Filter>,
    selectedFilterId: String?,
    onFilterSelected: (Filter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        filters.forEach { filter ->
            FilterItem(
                filter = filter,
                isSelected = filter.id == selectedFilterId,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun FilterItem(
    filter: Filter,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Image(
                painter = rememberAsyncImagePainter(filter.id),
                contentDescription = filter.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp)
            )
        }

        Text(
            text = filter.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
