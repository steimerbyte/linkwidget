package com.steimerbyte.linkwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText

/**
 * Konfigurationsdialog, der beim Platzieren des Widgets erscheint.
 * Fragt URL und optionalen Titel ab.
 */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_widget_config)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val urlInput = findViewById<EditText>(R.id.input_url)
        val titleInput = findViewById<EditText>(R.id.input_title)
        val saveButton = findViewById<Button>(R.id.button_save)
        val cancelButton = findViewById<Button>(R.id.button_cancel)

        cancelButton.setOnClickListener { finish() }

        saveButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            if (rawUrl.isEmpty()) {
                urlInput.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }

            val url = UrlNormalizer.normalize(rawUrl)
            val title = titleInput.text.toString().trim()
            LinkStore.set(this, widgetId, title, url)

            // Widget sofort mit den neuen Werten zeichnen.
            val manager = AppWidgetManager.getInstance(this)
            val provider = LinkWidgetProvider()
            provider.onUpdate(this, manager, intArrayOf(widgetId))

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }
}
