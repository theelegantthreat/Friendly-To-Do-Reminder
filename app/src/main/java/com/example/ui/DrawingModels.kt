package com.example.ui

data class DrawingPoint(val x: Float, val y: Float)

data class DrawingPath(
    val points: List<DrawingPoint>,
    val colorHex: String,
    val strokeWidth: Float
)

object DrawingSerializer {
    fun serializeDrawing(paths: List<DrawingPath>): String {
        return paths.joinToString("||") { path ->
            val pointsStr = path.points.joinToString(";") { "${it.x},${it.y}" }
            "${path.colorHex}|${path.strokeWidth}|$pointsStr"
        }
    }

    fun deserializeDrawing(data: String): List<DrawingPath> {
        if (data.isBlank()) return emptyList()
        return try {
            data.split("||").mapNotNull { segment ->
                val parts = segment.split("|")
                if (parts.size < 3) return@mapNotNull null
                val colorHex = parts[0]
                val strokeWidth = parts[1].toFloatOrNull() ?: 5f
                val pointsStr = parts[2]
                val points = if (pointsStr.isBlank()) emptyList() else {
                    pointsStr.split(";").mapNotNull { pt ->
                        val coords = pt.split(",")
                        if (coords.size == 2) {
                            val x = coords[0].toFloatOrNull() ?: 0f
                            val y = coords[1].toFloatOrNull() ?: 0f
                            DrawingPoint(x, y)
                        } else null
                    }
                }
                DrawingPath(points, colorHex, strokeWidth)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
