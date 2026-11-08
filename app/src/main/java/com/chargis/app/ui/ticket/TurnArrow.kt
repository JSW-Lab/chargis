package com.chargis.app.ui.ticket

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.chargis.app.domain.model.Maneuver
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 턴 안내 화살표. 아이콘 파일 없이 Compose로 직접 그린다(DESIGN.md: 아이콘은 직접 그림).
 * 아래에서 올라온 줄기가 가운데서 [angleDeg]만큼 꺾여 화살촉으로 끝난다. 0 = 직진, + = 오른쪽(시계 방향).
 * 유턴은 굽은 화살표, 도착은 직진 화살표 위에 둥근 점을 더해 그린다.
 * 그림은 100×100 상자 기준 좌표로 그리고 실제 크기에 맞춰 늘린다.
 */
@Composable
fun TurnArrow(maneuver: Maneuver, angleDeg: Int, color: Color, description: String, modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val k = size.minDimension / 100f
        val stroke = Stroke(width = 10f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path()
        val tip: Offset
        val dir: Double
        if (maneuver == Maneuver.U_TURN) {
            // 오른쪽 줄기로 올라가 위에서 돌아 왼쪽으로 내려온다(국내 유턴은 왼쪽으로 돈다).
            path.moveTo(62f * k, 88f * k)
            path.lineTo(62f * k, 46f * k)
            path.cubicTo(62f * k, 26f * k, 34f * k, 26f * k, 34f * k, 46f * k)
            path.lineTo(34f * k, 60f * k)
            tip = Offset(34f * k, 60f * k)
            dir = PI
        } else {
            val r = angleDeg * PI / 180.0
            val bend = Offset(50f * k, 56f * k)
            tip = Offset(bend.x + (30f * k * sin(r)).toFloat(), bend.y - (30f * k * cos(r)).toFloat())
            path.moveTo(50f * k, 90f * k)
            path.lineTo(bend.x, bend.y)
            path.lineTo(tip.x, tip.y)
            dir = r
        }
        drawPath(path, color, style = stroke)
        // 화살촉: 끝점에서 진행 방향으로 13, 양옆으로 11만큼 벌린 삼각형.
        val h = 13f * k
        val w = 11f * k
        val head = Path().apply {
            moveTo(tip.x + (h * sin(dir)).toFloat(), tip.y - (h * cos(dir)).toFloat())
            lineTo(tip.x + (w * cos(dir)).toFloat(), tip.y + (w * sin(dir)).toFloat())
            lineTo(tip.x - (w * cos(dir)).toFloat(), tip.y - (w * sin(dir)).toFloat())
            close()
        }
        drawPath(head, color)
        drawPath(head, color, style = Stroke(width = 3f * k, join = StrokeJoin.Round))
        if (maneuver == Maneuver.ARRIVE) drawCircle(color, radius = 7f * k, center = Offset(50f * k, 18f * k))
    }
}
