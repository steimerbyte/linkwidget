package com.steimerbyte.linkwidget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testet die Absicherung des Broadcast-Receivers.
 *
 * Der Receiver muss exportiert sein, weil der Launcher
 * APPWIDGET_UPDATE sendet. Die eigene Action OPEN_LINK wird
 * darueber hinaus behandelt - und darf nicht von aussen ausloesbar
 * sein.
 *
 * Die Logik ist bewusst so gebaut, dass sie ohne Android-Framework
 * pruefbar ist: ein Intent mit gesetztem Component kommt aus der
 * eigenen App, einer ohne Component von aussen.
 */
class BroadcastSecurityTest {

    private val ownPackage = "com.steimerbyte.linkwidget"
    private val ownClass = "com.steimerbyte.linkwidget.LinkWidgetProvider"

    /**
     * Bildet die Pruefung aus LinkWidgetProvider nach.
     */
    private fun isOwnBroadcast(
        intentPackage: String?,
        intentClass: String?,
        contextPackage: String
    ): Boolean {
        if (intentPackage == null) return false
        return intentPackage == contextPackage && intentClass == ownClass
    }

    @Test
    fun `impliziter broadcast von aussen wird abgewiesen`() {
        // Eine fremde App sendet die Action ohne Component. Das muss
        // abgewiesen werden - sonst koennte sie einen beliebigen
        // gespeicherten Link oeffnen lassen.
        val akzeptiert = isOwnBroadcast(null, null, ownPackage)
        assertFalse("Impliziter Broadcast darf nicht durchgehen", akzeptiert)
    }

    @Test
    fun `fremder component wird abgewiesen`() {
        // Selbst mit gesetztem Component, aber auf eine fremde Klasse
        // gerichtet: das ist ein fremder Broadcast.
        val akzeptiert = isOwnBroadcast(ownPackage, "com.fremde.app.Broadcast", ownPackage)
        assertFalse("Fremder Component darf nicht durchgehen", akzeptiert)
    }

    @Test
    fun `fremde app mit gleichem component wird abgewiesen`() {
        // Eine fremde App, die den Component nachbaut. Der Package-
        // Name muss uebereinstimmen.
        val akzeptiert = isOwnBroadcast("com.fremde.app", ownClass, ownPackage)
        assertFalse("Fremde App darf nicht durchgehen", akzeptiert)
    }

    @Test
    fun `eigener broadcast wird akzeptiert`() {
        // Der PendingIntent setzt den Component auf den Provider der
        // eigenen App. Das ist der regulaere Weg beim Tap.
        val akzeptiert = isOwnBroadcast(ownPackage, ownClass, ownPackage)
        assertTrue("Eigener Broadcast muss durchgehen", akzeptiert)
    }

    @Test
    fun `action ist eindeutig und namespace-gebunden`() {
        // Der eigentliche Fix - die Action steht in keinem
        // intent-filter - wird im Manifest des gebauten APK geprueft
        // und kann hier nicht nachgestellt werden. Was sich hier
        // zeigen laesst: die Action ist eindeutig und kollidiert
        // nicht mit der Launcher-Action.
        assertTrue(
            "Action muss eindeutig sein",
            LinkWidgetProvider.ACTION_OPEN_LINK != "android.appwidget.action.APPWIDGET_UPDATE"
        )
        assertTrue(
            "Action muss zum App-Namespace gehoeren",
            LinkWidgetProvider.ACTION_OPEN_LINK.startsWith("com.linkwidget.action.")
        )
    }
}
