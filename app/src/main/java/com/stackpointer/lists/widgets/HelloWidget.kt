package com.stackpointer.lists.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Minimal Glance app widget used only to prove the widget toolchain
 * (manifest wiring, res/xml provider metadata, KSP/Compose interop)
 * works in this project ahead of the real widgets built in Phase 10/11.
 */
class HelloWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                HelloWidgetContent()
            }
        }
    }
}

@Composable
private fun HelloWidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(12.dp)
    ) {
        Text(
            text = "Lists",
            style = TextStyle(color = GlanceTheme.colors.onSurface)
        )
        Text(
            text = "Widget plumbing works",
            style = TextStyle(color = GlanceTheme.colors.onSurface)
        )
    }
}
