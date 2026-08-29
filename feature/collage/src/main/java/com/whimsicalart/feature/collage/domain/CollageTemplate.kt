package com.whimsicalart.feature.collage.domain

import android.graphics.RectF

data class CollageCell(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun toRectF(): RectF = RectF(left, top, right, bottom)
}

data class CollageTemplate(
    val id: String,
    val name: String,
    val photoCount: Int,
    val cells: List<CollageCell>
)

object CollageTemplates {

    val all: List<CollageTemplate> = listOf(
        template("t2_h", "2 Tile", 2, listOf(
            cell(0f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 1f, 1f)
        )),
        template("t2_v", "2 Stack", 2, listOf(
            cell(0f, 0f, 0.5f, 1f),
            cell(0.5f, 0f, 1f, 1f)
        )),
        template("t3", "3", 3, listOf(
            cell(0f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 0.5f, 1f),
            cell(0.5f, 0.5f, 1f, 1f)
        )),
        template("t4", "4 Grid", 4, listOf(
            cell(0f, 0f, 0.5f, 0.5f),
            cell(0.5f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 0.5f, 1f),
            cell(0.5f, 0.5f, 1f, 1f)
        )),
        template("t5", "5", 5, listOf(
            cell(0f, 0f, 0.5f, 0.5f),
            cell(0.5f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 1f, 0.66f),
            cell(0f, 0.66f, 0.5f, 1f),
            cell(0.5f, 0.66f, 1f, 1f)
        )),
        template("t6", "6", 6, listOf(
            cell(0f, 0f, 0.34f, 0.5f),
            cell(0.34f, 0f, 0.66f, 0.5f),
            cell(0.66f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 0.34f, 1f),
            cell(0.34f, 0.5f, 0.66f, 1f),
            cell(0.66f, 0.5f, 1f, 1f)
        )),
        template("t7", "7", 7, listOf(
            cell(0f, 0f, 0.5f, 0.34f),
            cell(0.5f, 0f, 1f, 0.34f),
            cell(0f, 0.34f, 0.5f, 0.66f),
            cell(0.5f, 0.34f, 1f, 0.66f),
            cell(0f, 0.66f, 0.34f, 1f),
            cell(0.34f, 0.66f, 0.66f, 1f),
            cell(0.66f, 0.66f, 1f, 1f)
        )),
        template("t8", "8", 8, listOf(
            cell(0f, 0f, 0.25f, 0.5f),
            cell(0.25f, 0f, 0.5f, 0.5f),
            cell(0.5f, 0f, 0.75f, 0.5f),
            cell(0.75f, 0f, 1f, 0.5f),
            cell(0f, 0.5f, 0.25f, 1f),
            cell(0.25f, 0.5f, 0.5f, 1f),
            cell(0.5f, 0.5f, 0.75f, 1f),
            cell(0.75f, 0.5f, 1f, 1f)
        )),
        template("t9", "9 Grid", 9, listOf(
            cell(0f, 0f, 0.33f, 0.33f),
            cell(0.33f, 0f, 0.66f, 0.33f),
            cell(0.66f, 0f, 1f, 0.33f),
            cell(0f, 0.33f, 0.33f, 0.66f),
            cell(0.33f, 0.33f, 0.66f, 0.66f),
            cell(0.66f, 0.33f, 1f, 0.66f),
            cell(0f, 0.66f, 0.33f, 1f),
            cell(0.33f, 0.66f, 0.66f, 1f),
            cell(0.66f, 0.66f, 1f, 1f)
        ))
    )

    fun byId(id: String): CollageTemplate? = all.firstOrNull { it.id == id }

    private fun cell(left: Float, top: Float, right: Float, bottom: Float) =
        CollageCell(left, top, right, bottom)

    private fun template(
        id: String,
        name: String,
        count: Int,
        cells: List<CollageCell>
    ) = CollageTemplate(id, name, count, cells)
}
