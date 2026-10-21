package com.steimerbyte.linkwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * Home-Screen-Widget, das genau einen Link im Standardbrowser oeffnet.
 *
 * Kernpunkt: der Intent wird OHNE setPackage und OHNE setComponent gebaut.
 * Android loest einen impliziten ACTION_VIEW-Intent ueber die
 * Browsereinstellung des Geraets auf. Sobald setPackage(paket) gesetzt
 * waere, wuerde der Standardbrowser ignoriert und immer genau diese App
 * geoeffnet - das waere genau das Verhalten, das wir vermeiden wollen.
 */
class LinkWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            val link = LinkStore.get(context, widgetId)
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, link))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            LinkStore.remove(context, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_OPEN_LINK) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

            val link = LinkStore.get(context, widgetId)
            if (link.url.isBlank()) return

            // Impliziter Intent: kein setPackage, kein setComponent.
            // Nur so respektiert Android die Einstellung "Standardbrowser".
            val openIntent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(link.url))
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(openIntent)
            return
        }

        if (intent.action == ACTION_REFRESH) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(widgetId, buildViews(context, LinkStore.get(context, widgetId)))
        }
    }

    private fun buildViews(context: Context, link: LinkData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_link)

        views.setTextViewText(R.id.widget_title, link.title.ifBlank { link.url })
        views.setTextViewText(R.id.widget_url, link.url)
        views.setTextViewText(R.id.widget_hint, context.getString(R.string.widget_hint))

        // Sichtbarkeit des Hinweistexts abhaengig davon, ob ein Label gesetzt ist.
        views.setViewVisibility(
            R.id.widget_hint,
            if (link.title.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
        )

        val pendingIntent = buildTapIntent(context, link.id)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    private fun buildTapIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, LinkWidgetProvider::class.java).apply {
            action = ACTION_OPEN_LINK
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Daten (und damit die URL) nicht in das Intent-Extras legen,
            // sondern per Widget-ID im Store nachschlagen. So bleibt das
            // PendingIntent klein und update-faehig.
            data = Uri.parse("linkwidget://open/$widgetId")
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_OPEN_LINK = "com.linkwidget.action.OPEN_LINK"
        const val ACTION_REFRESH = "com.linkwidget.action.REFRESH"
    }
}
