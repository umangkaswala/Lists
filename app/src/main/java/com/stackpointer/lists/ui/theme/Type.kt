package com.stackpointer.lists.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stackpointer.lists.R

/**
 * Roboto Flex, the face the design is drawn in.
 *
 * One *variable* font file covering every weight, rather than five static
 * files: the design uses 300/400/500/600/700 and shipping five cuts would cost
 * far more than the single variable file does.
 *
 * Each weight is registered explicitly. Without that, Compose has only one
 * outline to work with and fakes the rest by smearing the glyphs — which is
 * exactly what a "600" heading is supposed to avoid. The variation setting is
 * what actually moves the axis; the [FontWeight] beside it is only how Compose
 * decides which entry to pick.
 *
 * Bundled rather than fetched through downloadable Google Fonts, so the app
 * never renders a frame in the wrong face, and works on a phone with no Play
 * Services at all.
 */
private fun robotoFlex(weight: Int, compose: FontWeight) = Font(
    resId = R.font.roboto_flex,
    weight = compose,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val RobotoFlex = FontFamily(
    robotoFlex(300, FontWeight.Light),
    robotoFlex(400, FontWeight.Normal),
    robotoFlex(500, FontWeight.Medium),
    robotoFlex(600, FontWeight.SemiBold),
    robotoFlex(700, FontWeight.Bold)
)

// Explicit sizes/weights from the design spec (Roboto Flex, emphasized scale).
val ListsTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold
        ),
        titleLarge = base.titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold
        ),
        bodyLarge = base.bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = base.bodyMedium.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp
        ),
        labelSmall = base.labelSmall.copy(
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}.withFamily(RobotoFlex)

/**
 * Applies one family to all fifteen styles.
 *
 * Written out rather than done with a loop because [Typography] has no
 * iterable form — and a style missed here is a screen that silently renders in
 * the system font while everything around it doesn't, which is far harder to
 * spot than a wholesale failure.
 */
private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)
