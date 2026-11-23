package dev.opencab.feature.roster

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import dev.opencab.core.model.RosterColor
import dev.opencab.core.model.RosterIcon

/** Named roster colours. They are stored by key, so the palette can evolve without a migration. */
internal fun RosterColor.toComposeColor(): Color = when (this) {
    RosterColor.BLUE -> Color(0xFF2A6FDB)
    RosterColor.TEAL -> Color(0xFF00897B)
    RosterColor.GREEN -> Color(0xFF2E7D32)
    RosterColor.AMBER -> Color(0xFFB26A00)
    RosterColor.RED -> Color(0xFFC62828)
    RosterColor.VIOLET -> Color(0xFF6A3FCB)
}

/** Simple silhouettes, drawn locally so the roster does not pull in the extended icon pack. */
internal fun RosterIcon.imageVector(): ImageVector = when (this) {
    RosterIcon.STEAM -> steamIcon
    RosterIcon.DIESEL -> dieselIcon
    RosterIcon.ELECTRIC -> electricIcon
    RosterIcon.SHUNTER -> shunterIcon
}

private fun buildIcon(
    name: String,
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) { block() }
}.build()

private val steamIcon: ImageVector = buildIcon("RosterSteam") {
    // Boiler, cab, chimney and footplate.
    moveTo(4f, 11f)
    lineTo(15f, 11f)
    lineTo(15f, 17f)
    lineTo(4f, 17f)
    close()
    moveTo(13f, 7f)
    lineTo(19f, 7f)
    lineTo(19f, 17f)
    lineTo(13f, 17f)
    close()
    moveTo(5f, 6f)
    lineTo(7f, 6f)
    lineTo(7f, 11f)
    lineTo(5f, 11f)
    close()
    moveTo(3f, 17f)
    lineTo(20f, 17f)
    lineTo(20f, 19f)
    lineTo(3f, 19f)
    close()
}

private val dieselIcon: ImageVector = buildIcon("RosterDiesel") {
    // Long hood and a sloped cab nose.
    moveTo(3f, 9f)
    lineTo(15f, 9f)
    lineTo(15f, 17f)
    lineTo(3f, 17f)
    close()
    moveTo(15f, 9f)
    lineTo(20f, 12f)
    lineTo(20f, 17f)
    lineTo(15f, 17f)
    close()
    moveTo(3f, 17f)
    lineTo(20f, 17f)
    lineTo(20f, 19f)
    lineTo(3f, 19f)
    close()
}

private val electricIcon: ImageVector = buildIcon("RosterElectric") {
    // Pantograph, box body and footplate.
    moveTo(10f, 4f)
    lineTo(14f, 4f)
    lineTo(12f, 8f)
    close()
    moveTo(4f, 9f)
    lineTo(20f, 9f)
    lineTo(20f, 17f)
    lineTo(4f, 17f)
    close()
    moveTo(3f, 17f)
    lineTo(20f, 17f)
    lineTo(20f, 19f)
    lineTo(3f, 19f)
    close()
}

private val shunterIcon: ImageVector = buildIcon("RosterShunter") {
    // Short hood with a tall cab.
    moveTo(4f, 12f)
    lineTo(12f, 12f)
    lineTo(12f, 17f)
    lineTo(4f, 17f)
    close()
    moveTo(12f, 8f)
    lineTo(19f, 8f)
    lineTo(19f, 17f)
    lineTo(12f, 17f)
    close()
    moveTo(3f, 17f)
    lineTo(20f, 17f)
    lineTo(20f, 19f)
    lineTo(3f, 19f)
    close()
}
