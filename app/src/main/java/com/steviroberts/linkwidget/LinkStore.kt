package com.steviroberts.linkwidget

import android.content.Context

/** Ein gespeicherter Link, wie er fuer ein Widget hinterlegt ist. */
data class LinkData(
    val id: Int,
    val title: String,
    val url: String
)

/**
 * Persistenz der Widget-Konfiguration.
 *
 * Bewusst SharedPreferences statt Room oder Datenbank: die Datenmenge ist
 * minimal (ein Eintrag pro Widget-Instanz), und die App kommt ohne
 * zusatzliche Abhaengigkeiten aus.
 */
object LinkStore {

    private const val PREFS_NAME = "link_widget_store"
    private const val KEY_TITLE = "title_"
    private const val KEY_URL = "url_"

    fun get(context: Context, widgetId: Int): LinkData {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LinkData(
            id = widgetId,
            title = prefs.getString(KEY_TITLE + widgetId, "").orEmpty(),
            url = prefs.getString(KEY_URL + widgetId, "").orEmpty()
        )
    }

    fun set(context: Context, widgetId: Int, title: String, url: String) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TITLE + widgetId, title)
            .putString(KEY_URL + widgetId, url)
            .apply()
    }

    fun remove(context: Context, widgetId: Int) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TITLE + widgetId)
            .remove(KEY_URL + widgetId)
            .apply()
    }
}
