package com.vervan.chat.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vervan.chat.MainActivity
import com.vervan.chat.R

/**
 * Home screen widgets (Phase 7, spec §37) — Quick Ask and New Note. Both just launch
 * MainActivity with the same "vervan_shortcut" extra the launcher shortcuts already use
 * (spec §37.3), so no separate deep-link handling was needed in NavGraph.
 */
private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, layoutId: Int, shortcut: String) {
    val intent = Intent(context, MainActivity::class.java).apply {
        putExtra("vervan_shortcut", shortcut)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val pendingIntent = PendingIntent.getActivity(
        context, shortcut.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val views = RemoteViews(context.packageName, layoutId)
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}

class QuickAskWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id, R.layout.widget_quick_ask, "new_chat") }
    }
}

class NewNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id, R.layout.widget_new_note, "capture") }
    }
}
