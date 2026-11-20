package dev.opencab.core.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.opencab.core.designsystem.theme.CabTheme

/**
 * The six preview variants every shared component is checked against:
 *
 * - phone portrait, light, 100% font
 * - phone portrait, dark, 100% font
 * - phone portrait, light, 200% font
 * - phone landscape, dark, 100% font
 * - tablet, light, 100% font
 * - tablet, dark, 200% font
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@Preview(
    name = "Phone portrait · light · 100%",
    group = "OpenCab",
    device = "spec:width=360dp,height=800dp,dpi=420",
    showBackground = true,
)
@Preview(
    name = "Phone portrait · dark · 100%",
    group = "OpenCab",
    device = "spec:width=360dp,height=800dp,dpi=420",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Phone portrait · light · 200%",
    group = "OpenCab",
    device = "spec:width=360dp,height=800dp,dpi=420",
    fontScale = 2f,
    showBackground = true,
)
@Preview(
    name = "Phone landscape · dark · 100%",
    group = "OpenCab",
    device = "spec:width=800dp,height=360dp,dpi=420",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(
    name = "Tablet · light · 100%",
    group = "OpenCab",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true,
)
@Preview(
    name = "Tablet · dark · 200%",
    group = "OpenCab",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    fontScale = 2f,
    showBackground = true,
)
annotation class CabPreview

@Composable
fun CabPreviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    CabTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = CabTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.padding(16.dp),
                content = content,
            )
        }
    }
}
