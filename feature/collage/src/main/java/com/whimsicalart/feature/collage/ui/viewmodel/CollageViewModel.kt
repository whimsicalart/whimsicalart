package com.whimsicalart.feature.collage.ui.viewmodel

import android.content.ContentResolver
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whimsicalart.core.common.BitmapPool
import com.whimsicalart.feature.collage.data.CollageRenderer
import com.whimsicalart.feature.collage.data.CollageSaver
import com.whimsicalart.feature.collage.domain.CollageTemplate
import com.whimsicalart.feature.collage.domain.CollageTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollageUiState(
    val template: CollageTemplate = CollageTemplates.byId("t2_h")!!,
    val slots: List<Uri?> = listOf(null, null),
    val slotRects: Map<Int, RectF> = emptyMap(),
    val isFreeForm: Boolean = false,
    val borderWidth: Float = 8f,
    val borderColor: Color = Color.White,
    val backgroundColor: Color = Color(0xFFECEFF1),
    val isSaving: Boolean = false
)

@HiltViewModel
class CollageViewModel @Inject constructor(
    private val saver: CollageSaver
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollageUiState())
    val uiState = _uiState.asStateFlow()

    fun selectTemplate(template: CollageTemplate) {
        if (template.id == _uiState.value.template.id) return
        _uiState.update { state ->
            val slots = state.slots.toMutableList()
            while (slots.size < template.photoCount) slots.add(null)
            state.copy(
                template = template,
                slots = slots.take(template.photoCount),
                slotRects = emptyMap()
            )
        }
    }

    fun toggleFreeForm() {
        _uiState.update { it.copy(isFreeForm = !it.isFreeForm) }
    }

    fun translateSlot(index: Int, dx: Float, dy: Float) {
        _uiState.update { state ->
            if (index !in state.slots.indices) {
                state
            } else {
                val base = state.slotRects[index] ?: state.template.cells[index].toRectF()
                val rect = RectF(
                    base.left + dx,
                    base.top + dy,
                    base.right + dx,
                    base.bottom + dy
                )
                state.copy(slotRects = state.slotRects + (index to clamped(rect)))
            }
        }
    }

    private fun clamped(rect: RectF): RectF {
        val left = rect.left.coerceIn(0f, 1f)
        val top = rect.top.coerceIn(0f, 1f)
        return RectF(
            left,
            top,
            (left + rect.width()).coerceAtMost(1f),
            (top + rect.height()).coerceAtMost(1f)
        )
    }

    fun assignPhoto(index: Int, uri: Uri?) {
        _uiState.update { state ->
            if (index !in state.slots.indices) state
            else state.copy(slots = state.slots.toMutableList().also { it[index] = uri })
        }
    }

    fun updateBorderWidth(width: Float) {
        _uiState.update { it.copy(borderWidth = width) }
    }

    fun updateBorderColor(color: Color) {
        _uiState.update { it.copy(borderColor = color) }
    }

    fun updateBackgroundColor(color: Color) {
        _uiState.update { it.copy(backgroundColor = color) }
    }

    fun setInitialPhotos(uris: List<Uri>) {
        _uiState.update { state ->
            val slots = uris.take(state.template.photoCount)
                .map { it as Uri? }
                .toMutableList()
            while (slots.size < state.template.photoCount) slots.add(null)
            state.copy(slots = slots)
        }
    }

    fun saveCollage(
        resolver: android.content.ContentResolver,
        onResult: (Boolean) -> Unit
    ) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val state = _uiState.value
            val bitmap = CollageRenderer.render(
                resolver = resolver,
                template = state.template,
                slotUris = state.slots,
                slotRects = state.slotRects,
                isFreeForm = state.isFreeForm,
                borderWidthRatio = state.borderWidth / 100f,
                borderColorArgb = state.borderColor.toArgb(),
                backgroundColorArgb = state.backgroundColor.toArgb()
            )
            val saved = bitmap != null && saver.saveCollage(bitmap) != null
            bitmap?.let { BitmapPool.put(it) }
            _uiState.update { it.copy(isSaving = false) }
            onResult(saved)
        }
    }
}
