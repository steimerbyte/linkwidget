package com.steimerbyte.linkwidget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Haupt-Activity: kurze Anleitung, Test-Button und eine Liste der
 * konfigurierten Widgets. Die eigentliche Funktion passiert im Widget -
 * diese Activity ist optional.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val urlInput = findViewById<EditText>(R.id.input_url)
        val titleInput = findViewById<EditText>(R.id.input_title)
        val testButton = findViewById<Button>(R.id.button_test)
        val list = findViewById<LinearLayout>(R.id.link_list)

        testButton.setOnClickListener {
            val rawUrl = urlInput.text.toString().trim()
            if (rawUrl.isEmpty()) {
                urlInput.error = getString(R.string.error_empty_url)
                return@setOnClickListener
            }
            val url = UrlNormalizer.normalize(rawUrl)

            // Test: den Link direkt im Standardbrowser oeffnen.
            // Impliziter Intent, kein setPackage - sonst wuerde der
            // Standardbrowser ignoriert.
            val openIntent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(openIntent)
        }

        findViewById<Button>(R.id.button_add_widget).setOnClickListener {
            showHowToDialog()
        }

        renderList(list)
    }

    private fun showHowToDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.howto_title)
            .setMessage(R.string.howto_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun renderList(container: LinearLayout) {
        container.removeAllViews()
        val prefs = getSharedPreferences("link_widget_store", MODE_PRIVATE)
        val ids = prefs.all.keys
            .filter { it.startsWith("url_") }
            .map { it.removePrefix("url_").toIntOrNull() }
            .filterNotNull()
            .sorted()

        if (ids.isEmpty()) {
            val empty = TextView(this).apply {
                text = getString(R.string.no_widgets)
                setPadding(0, 24, 0, 24)
            }
            container.addView(empty)
            return
        }

        for (id in ids) {
            val link = LinkStore.get(this, id)
            val row = TextView(this).apply {
                text = getString(R.string.list_entry, link.title.ifBlank { link.url }, link.url)
                setPadding(0, 12, 0, 12)
            }
            container.addView(row)
        }
    }
}
