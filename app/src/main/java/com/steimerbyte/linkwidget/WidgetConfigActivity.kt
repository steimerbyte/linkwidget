package com.steimerbyte.linkwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Konfigurationsdialog, der beim Platzieren des Widgets erscheint.
 *
 * Zwei Dinge passieren hier automatisch und ohne Bestaetigung:
 *
 *  - die eingegebene URL wird gespeichert, sobald sie sich aendert
 *  - der Abruf schreibt jeden einzelnen Schritt in eine
 *    Terminal-Ausgabe, die sich kopieren laesst
 *
 * Der Grund fuer die Ausgabe: ein Fehlschlag ist sonst nicht
 * eingrenzbar. "Kein verwendbares Icon gefunden" kann bedeuten, dass
 * das Geraet den Host nicht aufloest, dass die Verbindung blockiert
 * wird, oder dass die Seite kein Icon ausliefert. Die Ausgabe
 * unterscheidet diese Faelle.
 */
class WidgetConfigActivity : Activity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var logText: TextView
    private lateinit var logScroll: ScrollView
    private val logLines = mutableListOf<String>()

    /**
     * Enthaelt das aktuelle Feld den Wert, der bereits gespeichert
     * ist? Verhindert, dass beim Tippen dieselbe URL Dutzende Male
     * geschrieben wird und das Terminal voller Wiederholungen steht.
     */
    private var lastSavedUrl: String? = null
    private var lastSavedTitle: String? = null

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
        val copyButton = findViewById<Button>(R.id.button_copy_log)
        val previewImage = findViewById<ImageView>(R.id.preview_image)
        val statusText = findViewById<TextView>(R.id.preview_status)
        logText = findViewById(R.id.log_text)
        val showTitleSwitch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_show_title)
        val showUrlSwitch = findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_show_url)
        logScroll = findViewById(R.id.log_scroll)

        cancelButton.setOnClickListener { finish() }

        // Bereits gespeicherte Werte laden, damit die Aenderung
        // am Feld verglichen werden kann.
        // get() gibt nie null zurueck, sondern leere Strings. Ein
        // leerer Wert bedeutet "noch nichts gespeichert".
        val store = LinkStore.get(this, widgetId)
        lastSavedUrl = store.url.ifEmpty { null }
        lastSavedTitle = store.title.ifEmpty { null }


        // Die Anzeigeoptionen starten im gespeicherten Zustand. Beim
        // Rekonfigurieren eines vorhandenen Widgets sind sie damit
        // bereits korrekt eingestellt.
        showTitleSwitch.isChecked = store.showTitle
        showUrlSwitch.isChecked = store.showUrl

        // Die Schalter wirken sofort: es wird gespeichert und das
        // Widget neu gezeichnet. Ohne das muesste der Nutzer erst
        // auf "Speichern" tippen, damit die Aenderung sichtbar wird.
        val toggleListener = { _: android.widget.CompoundButton, checked: Boolean ->
            val url = UrlNormalizer.normalize(urlInput.text.toString().trim())
            val title = titleInput.text.toString().trim()
            persist(url, title, showTitleSwitch.isChecked, showUrlSwitch.isChecked)
        }
        showTitleSwitch.setOnCheckedChangeListener { button, checked -> toggleListener(button, checked) }
        showUrlSwitch.setOnCheckedChangeListener { button, checked -> toggleListener(button, checked) }
        val saveUrlWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

            override fun afterTextChanged(s: android.text.Editable?) {
                // Speichern erst ab einer plausiblen Laenge. Sonst
                // wuerde jeder Zwischenstand beim Tippen gesichert.
                val normalized = UrlNormalizer.normalize(s?.toString()?.trim().orEmpty())
                if (normalized.length < "https://".length + 3) return
                if (normalized == lastSavedUrl) return
                persist(normalized, titleInput.text.toString().trim(), showTitleSwitch.isChecked, showUrlSwitch.isChecked)
            }
        }
        urlInput.addTextChangedListener(saveUrlWatcher)

        val saveTitleWatcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

            override fun afterTextChanged(s: android.text.Editable?) {
                val url = UrlNormalizer.normalize(urlInput.text.toString().trim())
                if (url.length < "https://".length + 3) return
                val title = s?.toString()?.trim().orEmpty()
                if (title == lastSavedTitle) return
                persist(url, title, showTitleSwitch.isChecked, showUrlSwitch.isChecked)
            }
        }
        titleInput.addTextChangedListener(saveTitleWatcher)

        copyButton.setOnClickListener {
            val text = logLines.joinToString("\n")
            if (text.isBlank()) {
                toast(getString(R.string.log_nothing_to_copy))
                return@setOnClickListener
            }
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("linkwidget-log", text))
            toast(getString(R.string.log_copied))
        }

        fetchButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            if (rawUrl.isEmpty()) {
                urlInput.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }
            val url = UrlNormalizer.normalize(rawUrl)

            // Auch ohne Abruf wird gespeichert: die URL soll nicht
            // verloren gehen, wenn nur das Icon scheitert.
            persist(url, titleInput.text.toString().trim(), showTitleSwitch.isChecked, showUrlSwitch.isChecked)

            fetchButton.isEnabled = false
            clearLog()
            appendLog("Abruf startet fuer $url")
            previewImage.setImageDrawable(null)
            previewImage.visibility = android.view.View.INVISIBLE

            // force = true: der Cache wird ignoriert. Sonst wuerde ein
            // alter, falscher Eintrag das Ergebnis verdecken.
            FaviconLoader.fetchWithProgress(
                context = this,
                url = url,
                force = true,
                onProgress = { message -> mainHandler.post { appendLog(message) } },
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
                                    appendLog("Ergebnis: ${result.bitmap.width}x${result.bitmap.height} Pixel")
                                } else {
                                    // Die Datei liegt da, liess sich aber
                                    // nicht dekodieren. Genau das waere
                                    // im Widget unsichtbar geblieben.
                                    statusText.text = getString(R.string.fetch_not_decodable)
                                    appendLog("Ergebnis: Datei vorhanden, aber nicht als Bild lesbar")
                                }
                            }

                            is FaviconLoader.FetchResult.Failed -> {
                                statusText.text = getString(R.string.fetch_failed, result.reason)
                                appendLog("Ergebnis: FEHLGESCHLAGEN - ${result.reason}")
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

            // Auch der Speichern-Knopf schreibt mit, damit auch ohne
            // Texteingabe-Aenderung garantiert gespeichert wird.
            val url = UrlNormalizer.normalize(rawUrl)
            val title = titleInput.text.toString().trim()
            persist(url, title, showTitleSwitch.isChecked, showUrlSwitch.isChecked)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }

    /**
     * Schreibt URL, Titel und Anzeigeoptionen in den Speicher und
     * zeichnet das Widget neu. Wird bei jeder Aenderung aufgerufen,
     * nicht erst beim Speichern-Knopf.
     */
    private fun persist(
        url: String,
        title: String,
        showTitle: Boolean = true,
        showUrl: Boolean = true
    ) {
        LinkStore.set(this, widgetId, title, url, showTitle, showUrl)
        lastSavedUrl = url
        lastSavedTitle = title

        val manager = AppWidgetManager.getInstance(this)
        val provider = LinkWidgetProvider()
        provider.onUpdate(this, manager, intArrayOf(widgetId))

        appendLog("gespeichert  $url")
        if (title.isNotEmpty()) appendLog("            Titel: $title")
        appendLog(
            "            Anzeige: Titel ${if (showTitle) "an" else "aus"}, " +
                "URL ${if (showUrl) "an" else "aus"}"
        )
    }

    /**
     * Haengt eine Zeile an die Ausgabe an und scrollt zum Ende.
     *
     * Zeilenweise, nicht als Block: sonst steht am Ende alles auf
     * einmal da und der Verlauf des Abrufs geht verloren.
     */
    private fun appendLog(line: String) {
        logLines.add(line)
        logText.text = logLines.joinToString("\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun clearLog() {
        logLines.clear()
        logText.text = ""
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}