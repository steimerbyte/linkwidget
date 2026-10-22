package com.steimerbyte.linkwidget

import android.content.Context

/** Ein gespeicherter Link, wie er fuer ein Widget hinterlegt ist. */
data class LinkData(
    val id: Int,
    val title: String,
    val url: String,
    /**
     * Ob der Titel (bzw. der Host als Titel) angezeigt wird.
     *
     * Standard: sichtbar. Wer die Beschriftung ausblendet, sieht nur
     * das Icon.
     */
    val showTitle: Boolean = true,
    /**
     * Ob die vollstaendige Adresse unter dem Titel angezeigt wird.
     *
     * Standard: sichtbar, aber nur wenn ein eigener Titel gesetzt
     * ist - sonst waere die Adresse doppelt.
     */
    val showUrl: Boolean = true
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
    private const val KEY_SHOW_TITLE = "show_title_"
    private const val KEY_SHOW_URL = "show_url_"

    fun get(context: Context, widgetId: Int): LinkData {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return LinkData(
            id = widgetId,
            title = prefs.getString(KEY_TITLE + widgetId, "").orEmpty(),
            url = prefs.getString(KEY_URL + widgetId, "").orEmpty(),
            showTitle = prefs.getBoolean(KEY_SHOW_TITLE + widgetId, true),
            showUrl = prefs.getBoolean(KEY_SHOW_URL + widgetId, true)
        )
    }

    /**
     * Speichert den Link samt Anzeigeoptionen.
     *
     * @param showTitle Titel (bzw. Host) anzeigen
     * @param showUrl die vollstaendige Adresse anzeigen
     */
    fun set(
        context: Context,
        widgetId: Int,
        title: String,
        url: String,
        showTitle: Boolean = true,
        showUrl: Boolean = true
    ) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_TITLE + widgetId, title)
            .putString(KEY_URL + widgetId, url)
            .putBoolean(KEY_SHOW_TITLE + widgetId, showTitle)
            .putBoolean(KEY_SHOW_URL + widgetId, showUrl)
            .apply()
    }

    fun remove(context: Context, widgetId: Int) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TITLE + widgetId)
            .remove(KEY_URL + widgetId)
            .remove(KEY_SHOW_TITLE + widgetId)
            .remove(KEY_SHOW_URL + widgetId)
            .apply()
    }
}