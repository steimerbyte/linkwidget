package com.steviroberts.linkwidget

/**
 * Normalisiert eine vom Benutzer eingegebene URL.
 *
 * Warum das noetig ist: Uri.parse interpretiert "example.com" als relativen
 * Pfad. Ein impliziter ACTION_VIEW-Intent mit relativem Pfad wuerde dann
 * keinen gueltigen Browser-Ziel-URI erzeugen - der Tap wuerde ins Leere
 * laufen. Deshalb wird ein Schema ergaenzt, wenn keines vorhanden ist.
 */
object UrlNormalizer {

    /**
     * Prueft, ob die Eingabe bereits ein Schema besitzt.
     *
     * Wichtig: nicht nur auf "://" pruefen. "mailto:a@b.com" hat kein
     * "://", ist aber ein vollstaendiges Schema. Eine reine Suche nach
     * "://" wuerde daraus "https://mailto:a@b.com" machen und damit die
     * Mail-Adresse zerstoeren. Deshalb wird die Form "scheme:" erkannt -
     * aber nur, wenn davor kein Slash steht, denn "/pfad:ohne scheme"
     * ist ein Pfad und kein Schema.
     */
    private fun hasScheme(value: String): Boolean {
        val colonIndex = value.indexOf(':')
        if (colonIndex <= 0) return false

        // "//" nach dem Doppelpunkt ist der typische Fall (http://, ftp://).
        if (value.startsWith("//", colonIndex + 1)) return true

        // Reines Schema wie "mailto:" oder "tel:".
        val scheme = value.substring(0, colonIndex)
        if (scheme.any { !it.isLetterOrDigit() && it != '+' && it != '-' && it != '.' }) {
            return false
        }
        // Ein Schraegstrich im "Schema" bedeutet: es ist ein Pfad, kein Schema.
        return !scheme.contains('/')
    }

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (hasScheme(trimmed)) trimmed else "https://$trimmed"
    }
}
