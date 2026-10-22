package com.steimerbyte.linkwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.os.Bundle
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
            // Die aktuelle Groesse des Widgets wird uebergeben, damit das
            // Layout passend zur Breite gewaehlt werden kann.
            val options = appWidgetManager.getAppWidgetOptions(widgetId)
            appWidgetManager.updateAppWidget(widgetId, buildViews(context, link, options))

            // Das Favicon wird hier geladen, nicht nur beim Speichern der
            // URL in der Konfiguration. Sonst bekommen Widgets, die
            // anders entstanden sind (z.B. nach einem App-Update), nie
            // ihr Icon - und ein fehlgeschlagener Abruf wird nie
            // wiederholt.
            if (link.url.isNotBlank()) {
                FaviconLoader.fetchAsync(context, link.url) {
                    val refreshed = AppWidgetManager.getInstance(context)
                    val freshOptions = refreshed.getAppWidgetOptions(widgetId)
                    refreshed.updateAppWidget(
                        widgetId,
                        buildViews(context, LinkStore.get(context, widgetId), freshOptions)
                    )
                }
            }
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
            val options = manager.getAppWidgetOptions(widgetId)
            manager.updateAppWidget(
                widgetId,
                buildViews(context, LinkStore.get(context, widgetId), options)
            )
        }
    }

    /**
     * Wird aufgerufen, wenn der Nutzer das Widget groesser oder kleiner
     * zieht. Ohne diesen Callback wuerde das Layout nach dem Resize nicht
     * neu gezeichnet.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val link = LinkStore.get(context, appWidgetId)
        appWidgetManager.updateAppWidget(
            appWidgetId,
            buildViews(context, link, newOptions)
        )
    }

    private fun buildViews(
        context: Context,
        link: LinkData,
        options: Bundle? = null
    ): RemoteViews {
        // Bei schmalen Widgets (< ~120dp) reicht der Platz nicht fuer URL
        // und Hinweis. Dann wird die kompakte Variante benutzt, die nur
        // Punkt und Titel zeigt. Sonst das volle Layout.
        val minWidth = options?.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            Int.MAX_VALUE
        ) ?: Int.MAX_VALUE
        val isCompact = minWidth < COMPACT_WIDTH_THRESHOLD_DP

        val layoutId = if (isCompact) R.layout.widget_link_compact else R.layout.widget_link
        val views = RemoteViews(context.packageName, layoutId)

        // Anzeigename: der gesetzte Titel, sonst der Host ohne Schema.
        // Niemals die volle URL - die steht sonst zusaetzlich in der
        // unteren Zeile und wird doppelt angezeigt.
        //
        // Der Titel laesst sich ausblenden. Dann bleibt nur das Icon.
        views.setTextViewText(R.id.widget_title, link.title.ifBlank { displayHost(link.url) })
        views.setViewVisibility(
            R.id.widget_title,
            if (link.showTitle) android.view.View.VISIBLE else android.view.View.GONE
        )

        // Favicon anzeigen, falls eines im Cache liegt. Sonst bleibt die
        // ImageView leer und der Punkt uebernimmt die visuelle Rolle.
        val favicon = FaviconLoader.loadBitmap(context, link.url)
        if (favicon != null) {
            views.setImageViewBitmap(R.id.widget_icon, favicon)
            views.setViewVisibility(R.id.widget_icon, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_icon, android.view.View.GONE)
        }

        // Der Punkt laesst sich ausblenden. Er ist die Nothing-typische
        // Textur unter dem Icon und markiert das Widget als aktiv.
        views.setViewVisibility(
            R.id.widget_dot,
            if (link.showDot) android.view.View.VISIBLE else android.view.View.GONE
        )

        if (!isCompact) {
            // Die untere URL-Zeile entfaellt, wenn kein eigener Titel
            // gesetzt ist. In dem Fall zeigt die Titelzeile bereits
            // den Host, und die vollstaendige Adresse waere eine
            // doppelte Information. Zusaetzlich laesst sich die
            // Zeile ueber die Einstellungen ausblenden.
            val hasTitle = link.title.isNotBlank()
            views.setTextViewText(R.id.widget_url, link.url)
            views.setViewVisibility(
                R.id.widget_url,
                if (hasTitle && link.showUrl) android.view.View.VISIBLE
                else android.view.View.GONE
            )
            views.setTextViewText(R.id.widget_hint, context.getString(R.string.widget_hint))
            views.setViewVisibility(
                R.id.widget_hint,
                if (hasTitle) android.view.View.VISIBLE else android.view.View.GONE
            )
        }

        val pendingIntent = buildTapIntent(context, link.id)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        return views
    }

    /**
     * Der Host ohne Schema - "omp.steimercloud.xyz" statt
     * "https://omp.steimercloud.xyz". Als Titel gesetzt, wirkt es
     * lesbarer als die vollstaendige Adresse.
     */
    private fun displayHost(url: String): String {
        return try {
            val host = java.net.URL(url).host
            if (host.isNullOrBlank()) url else host.removePrefix("www.")
        } catch (e: Exception) {
            url
        }
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
        // Unterhalb dieser Breite (dp) wird das kompakte Layout benutzt.
        const val COMPACT_WIDTH_THRESHOLD_DP = 120
    }
}
