package com.steimerbyte.linkwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView

/**
 * Konfigurationsdialog, der beim Platzieren des Widgets erscheint.
 *
 * Fragt URL und optionalen Titel ab. Mit dem Button "Icon laden" wird
 * das Favicon abgerufen - mit sichtbarer Vorschau und Statuszeile, damit
 * klar ist, was beim Widget tatsaechlich landet.
 */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val mainHandler = Handler(Looper.getMainLooper())

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
        val fetchButton = findViewById<Button>(R.id.button_fetch)
        val previewImage = findViewById<ImageView>(R.id.preview_image)
        val statusText = findViewById<TextView>(R.id.preview_status)

        cancelButton.setOnClickListener { finish() }

        fetchButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            if (rawUrl.isEmpty()) {
                urlInput.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }
            val url = UrlNormalizer.normalize(rawUrl)

            fetchButton.isEnabled = false
            statusText.text = getString(R.string.fetch_starting)
            previewImage.setImageDrawable(null)
            previewImage.visibility = android.view.View.INVISIBLE

            // force = true: der Cache wird ignoriert. Sonst wuerde ein
            // alter, falscher Eintrag das Ergebnis verdecken.
            FaviconLoader.fetchWithProgress(
                context = this,
                url = url,
                force = true,
                onProgress = { message ->
                    mainHandler.post { statusText.text = message }
                },
                onResult = { result ->
                    mainHandler.post {
                        fetchButton.isEnabled = true
                        when (result) {
                            is FaviconLoader.FetchResult.Success -> {
                                if (result.bitmap != null) {
                                    previewImage.setImageBitmap(result.bitmap)
                                    previewImage.visibility = android.view.View.VISIBLE
                                    statusText.text = getString(
                                        R.string.fetch_success,
                                        result.bitmap.width,
                                        result.bitmap.height
                                    )
                                } else {
                                    // Die Datei liegt da, liess sich aber
                                    // nicht dekodieren. Genau das waere
                                    // im Widget unsichtbar geblieben.
                                    statusText.text = getString(R.string.fetch_not_decodable)
                                }
                            }

                            is FaviconLoader.FetchResult.Failed -> {
                                statusText.text = getString(R.string.fetch_failed, result.reason)
                            }
                        }
                    }
                }
            )
        }

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
