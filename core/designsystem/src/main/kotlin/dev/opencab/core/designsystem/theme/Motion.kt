package dev.opencab.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Spring values mirrored from Material 3's expressive and standard motion schemes.
 *
 * Material3 1.4.0 marks `MotionScheme` and both of its factories `internal`, so the values
 * cannot be obtained from the library at this version. These mirror the generated token
 * values (`ExpressiveMotionTokens` / `StandardMotionTokens`) and are applied through
 * [CabMotion] rather than `MaterialTheme.motionScheme`, which is also internal.
 */
@Immutable
data class CabSpringTokens(
    val defaultSpatialDamping: Float,
    val defaultSpatialStiffness: Float,
    val fastSpatialDamping: Float,
    val fastSpatialStiffness: Float,
    val slowSpatialDamping: Float,
    val slowSpatialStiffness: Float,
    val defaultEffectsDamping: Float,
    val defaultEffectsStiffness: Float,
    val fastEffectsDamping: Float,
    val fastEffectsStiffness: Float,
    val slowEffectsDamping: Float,
    val slowEffectsStiffness: Float,
)

private fun <T> springOrSnap(
    reduced: Boolean,
    dampingRatio: Float,
    stiffness: Float,
): FiniteAnimationSpec<T> = if (reduced) {
    snap()
} else {
    spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * Motion tokens for the whole app.
 *
 * The expressive scheme is the default for entry, hero, and container transitions. The
 * throttle, speed numerals, and arc use the [standard] scheme because overshoot in those
 * places would imply a physical speed change that did not happen.
 */
@Suppress("MaxLineLength")
@Immutable
data class CabMotion(
    val reduced: Boolean = false,
    val expressive: CabSpringTokens = ExpressiveSprings,
    val standard: CabSpringTokens = StandardSprings,
) {
    fun <T> expressiveSpatialDefault(): FiniteAnimationSpec<T> = springOrSnap(reduced, expressive.defaultSpatialDamping, expressive.defaultSpatialStiffness)

    fun <T> expressiveSpatialFast(): FiniteAnimationSpec<T> = springOrSnap(reduced, expressive.fastSpatialDamping, expressive.fastSpatialStiffness)

    fun <T> expressiveSpatialSlow(): FiniteAnimationSpec<T> = springOrSnap(reduced, expressive.slowSpatialDamping, expressive.slowSpatialStiffness)

    fun <T> expressiveEffectsDefault(): FiniteAnimationSpec<T> = springOrSnap(reduced, expressive.defaultEffectsDamping, expressive.defaultEffectsStiffness)

    fun <T> standardSpatialDefault(): FiniteAnimationSpec<T> = springOrSnap(reduced, standard.defaultSpatialDamping, standard.defaultSpatialStiffness)

    fun <T> standardEffectsDefault(): FiniteAnimationSpec<T> = springOrSnap(reduced, standard.defaultEffectsDamping, standard.defaultEffectsStiffness)

    fun <T> duration(
        durationMillis: Int,
        easing: Easing = FastOutSlowInEasing,
    ): FiniteAnimationSpec<T> = if (reduced) {
        snap()
    } else {
        tween(durationMillis = durationMillis, easing = easing)
    }

    /** The arc eases so a stop is visible; the numeral itself never animates. */
    val arcFill: FiniteAnimationSpec<Float>
        get() = if (reduced) snap() else tween(durationMillis = 120, easing = LinearEasing)

    /** Short shape/colour feedback on press. */
    val pressFeedback: FiniteAnimationSpec<Float>
        get() = if (reduced) snap() else tween(durationMillis = 90, easing = FastOutSlowInEasing)

    /** No lateral slide: transitions fade through so they do not imply hierarchy. */
    val fadeThrough: FiniteAnimationSpec<Float>
        get() = duration(durationMillis = 300)

    val reconnectIn: FiniteAnimationSpec<Float>
        get() = duration(durationMillis = 200)

    val reconnectOut: FiniteAnimationSpec<Float>
        get() = duration(durationMillis = 150)

    companion object {
        val ExpressiveSprings =
            CabSpringTokens(
                defaultSpatialDamping = 0.8f,
                defaultSpatialStiffness = 380f,
                fastSpatialDamping = 0.6f,
                fastSpatialStiffness = 800f,
                slowSpatialDamping = 0.8f,
                slowSpatialStiffness = 200f,
                defaultEffectsDamping = 1f,
                defaultEffectsStiffness = 1600f,
                fastEffectsDamping = 1f,
                fastEffectsStiffness = 3800f,
                slowEffectsDamping = 1f,
                slowEffectsStiffness = 800f,
            )

        val StandardSprings =
            CabSpringTokens(
                defaultSpatialDamping = 0.9f,
                defaultSpatialStiffness = 700f,
                fastSpatialDamping = 0.9f,
                fastSpatialStiffness = 1400f,
                slowSpatialDamping = 0.9f,
                slowSpatialStiffness = 300f,
                defaultEffectsDamping = 1f,
                defaultEffectsStiffness = 1600f,
                fastEffectsDamping = 1f,
                fastEffectsStiffness = 3800f,
                slowEffectsDamping = 1f,
                slowEffectsStiffness = 800f,
            )

        val Default: CabMotion = CabMotion()
    }
}

internal val LocalCabMotion = staticCompositionLocalOf { CabMotion.Default }

/**
 * Reads the system animator duration scale once per composition. A scale of zero means the
 * user (or the OS) asked for reduced motion; springs collapse to instant transitions.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale =
            runCatching {
                Settings.Global.getFloat(
                    context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f,
                )
            }.getOrDefault(1f)
        scale == 0f
    }
}

@Composable
fun rememberCabMotion(reducedMotion: Boolean = rememberReducedMotion()): CabMotion = remember(reducedMotion) {
    CabMotion(reduced = reducedMotion)
}
