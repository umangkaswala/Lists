package com.stackpointer.lists.widgets

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Broadcast receiver entry point for [HelloWidget], the toolchain smoke-test
 * widget. Wired up in AndroidManifest.xml and res/xml/hello_widget_info.xml.
 */
class HelloWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HelloWidget()
}
