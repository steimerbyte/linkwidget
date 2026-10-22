# Link Widget

Eine Nothing-OS-inspirierte Android-App, die genau einen Use-Case abbildet:
**ein Home-Screen-Widget, das einen Link im Standardbrowser öffnet.**

Kein Bookmark-Manager. Kein Konto. Keine Cloud. Eine Berechtigung: INTERNET
fuer das Favicon der gespeicherten Website - sonst nichts.

---

## Was das Widget tut

Tippen auf das Widget öffnet die hinterlegte URL im Browser, den du in
Android als Standardbrowser eingestellt hast — genau so, wie ein normaler
Link im Chat oder in einer App funktioniert.

Das ist technisch der entscheidende Punkt, und er ist in der App
bewusst so umgesetzt:

```kotlin
// Implicit Intent: kein setPackage, kein setComponent.
val openIntent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
context.startActivity(openIntent)
```

Sobald `setPackage(paket)` gesetzt wäre, würde Android genau diese App
öffnen und deine Browsereinstellung ignorieren. Ein Weg über
`setPackage` fehlt in diesem Projekt **komplett** — verifiziert im
kompilierten Bytecode, nicht nur im Quelltext.

## Installation

Aktuelle Version: **v1.0.1** — https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.1

1. APK aus `app/build/outputs/apk/release/app-release.apk` auf das Gerät übertragen
   (oder aus dem GitHub-Release herunterladen)
2. Datei antippen → Play Protect meldet „App aus unbekannter Quelle" → **Installieren**
3. Long-press auf dem Home-Screen → **Widgets** → **Link Widget** auf den Bildschirm ziehen
4. URL eingeben, optional Titel
5. **Icon laden** → die Vorschau zeigt das Favicon, die Statuszeile meldet jeden Schritt
6. **Speichern**

Mehrere Links: das Widget mehrfach platzieren. Jede Instanz speichert
ihren eigenen Link.

### Der Abruf-Button

Der Favicon-Abruf läuft nicht blind im Hintergrund. Beim Platzieren des
Widgets gibt es einen Button **„Icon laden"**, der:

- den Abruf auf Zuruf startet und den Cache dabei ignoriert
- das geladene Icon in Originalgröße als Vorschau zeigt
- jeden Schritt in der Statuszeile meldet: welche Adresse abgerufen
  wird, welches Format erkannt wurde, wie viele Bytes, ob gespeichert
  wurde

Wichtig ist die Unterscheidung zwischen zwei Fehlerfällen, die im Widget
beide unsichtbar wären:

- **nichts gefunden** — die Seite liefert kein Icon
- **Datei da, aber nicht als Bild lesbar** — die Datei liegt im Cache,
  lässt sich aber nicht dekodieren (etwa ein exotisches Format)

Die Statuszeile nennt den konkreten Grund, sodass sich der Fall
eingrenzen lässt, ohne das Widget zu prüfen.

## Berechtigungen

`aapt2 dump permissions` auf dem Release-APK liefert:

```
uses-permission: name='android.permission.INTERNET'
uses-permission: name='com.steimerbyte.linkwidget.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

**INTERNET** wird ausschliesslich fuer das Laden des Favicons der vom
Benutzer eingegebenen Website gebraucht. Es wird **nur** die Icon-Adresse
dieser einen Seite abgerufen - kein Tracking, keine Analytics, keine
Weitergabe an Dritte. Der Abruf erfolgt direkt von der Website selbst,
nicht ueber einen Dienst wie Google oder DuckDuckGo.

Die zweite Berechtigung ist eine interne AndroidX-Sperre gegen exportierte
Empfaenger, kein Zugriff auf Daten. Kein Speicherzugriff, kein Standort.

**Oeffnen verursacht keinen Netzwerkzugriff.** Der Link wird im
`ACTION_VIEW`-Intent direkt uebergeben. Netzwerk wird nur beim Speichern
beruehrt, und nur um das Icon zu holen.

## Favicons

Beim Speichern der URL wird das Icon der Website geladen und auf dem
Widget angezeigt - so wie ein Browser es tut.

Der Ablauf:

1. Die Seite wird abgerufen
2. Aus dem `<head>` wird das passende `<link rel="icon">` ausgelesen
3. Das Icon wird von der dort genannten Adresse geladen

Es wird **kein Drittanbieter-Dienst** eingeschaltet. Die einzige
aufgerufene Domain ist die, die der Benutzer selbst eingegeben hat.

**Alle Kandidaten werden gesammelt und nach ihrer tatsaechlichen Groesse
sortiert** - so wie ein Browser es macht. Das ist wichtig, weil viele
Seiten mehrere Icons anbieten:

```html
<link rel="icon" href="/favicon.ico" sizes="16x16"/>
<link rel="icon" href="/icons/icon-192.png" sizes="192x192"/>
```

Ohne diese Sortierung gewinnt das erste Icon im Dokument, also das
16x16-Icon - und das Widget zeigt ein pixeliges Miniaturbild statt des
echten Logos. Die Reihenfolge der Auswahl: Groesse, dann rel-Rang
(`fluid-icon`, `apple-touch-icon`, `shortcut icon`, `icon`,
`mask-icon`), dann Dokumentreihenfolge.

**Formate:** Android kann kein SVG und kein ICO dekodieren. Beides
kommt in freier Wildbahn vor (Hacker News liefert `y18.svg`, viele
Server liefern `favicon.ico`). Der Loader erkennt das Format und
handelt entsprechend:

| Format | Vorgehen |
|---|---|
| PNG, JPEG, GIF, WebP | direkt verwendet |
| ICO | groesstes enthaltenes Bild wird herausgeloest und in BMP umgewandelt |
| SVG | nicht dekodierbar - Ausweichen auf `/favicon.ico` der Domain |
| keines | Ausweichen auf `/favicon.ico`, dann `/favicon.png` |

Bei ICO ist `biHeight` doppelt so gross, weil die AND-Maske mit
eingerechnet wird. Der Extractor korrigiert das und schneidet die
Maske ab - ein 48x48-Icon wird ein 48x48-Bild, nicht 48x96.

Das Icon wird lokal im App-Cache gehalten, unter einem Hash-Namen -
im Dateisystem sind keine Hostnamen sichtbar.

## Groesse und Layout

Das Widget belegt **eine Zelle** (`minWidth`/`minHeight` = 40dp) und
laesst sich frei in beide Richtungen ziehen. Bei 110dp - dem Wert der
ersten Version - berechnete der Launcher 2 Zellen, eine schmalere
Platzierung war deshalb nicht moeglich.

Je nach Breite waehlt die App das Layout selbst:

| Breite | Layout | Inhalt |
|---|---|---|
| < 120dp | kompakt | Icon, Titel |
| ab 120dp | voll | Icon, Titel, URL, Hinweistext |

Der Wechsel passiert automatisch beim Ziehen, weil
`onAppWidgetOptionsChanged` das Widget bei jeder Groessenaenderung neu
zeichnet.


## Nothing-OS-Optik

- reines Schwarz (`#000000`), reines Weiß, ein einziger Akzent
- Monospace für URLs und Labels, Sans-Serif für Titel
- Punktraster als Textur, gerundete Ecken, keine Verläufe, keine Schatten
- `monochrome`-Icon für Themed Icons auf Android 13+

## Bauen

Voraussetzungen: JDK 17+, Android SDK mit Platform 35 und Build-Tools 35.

```bash
export ANDROID_HOME=$HOME/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
gradle testDebugUnitTest assembleRelease
```

Ergebnis: `app/build/outputs/apk/release/app-release.apk`

### Tests

42 Unit-Tests in sieben Dateien:

| Datei | Tests | Deckt ab |
|---|---|---|
| `UrlNormalizerTest` | 12 | `mailto:`, `tel:`, Custom Schemes, `example.com/a:b` (Doppelpunkt ist **kein** Schema) |
| `ImageFormatTest` | 12 | PNG/JPEG/GIF/WebP/SVG/ICO-Erkennung, ICO-Extraktion |
| `FaviconUrlTest` | 7 | Aufloesung relativer und wurzel-relativer Icon-Pfade |
| `IconSelectionTest` | 8 | Icon-Auswahl: groesstes gewinnt, `sizes`-Auswertung |
| `FetchWithProgressTest` | 1 | kompletter Abrufweg gegen eine echte Seite |
| `FaviconFetchTest` | 1 | Integration gegen eine echte Website (netzwerkabhaengig) |
| `RealIcoTest` | 1 | ICO-Extraktion an einem echten ICO aus der Wildbahn (netzwerkabhaengig) |

Die Tests pruefen die echten Produktionsklassen, keine nachgebauten
Kopien. Die beiden netzwerkabhaengigen Tests laufen gegen
`https://omp.steimercloud.xyz`, das ein ICO mit drei Bildgroessen
liefert - ein Fall, den keine Standardseite abdeckt.

```bash
gradle testDebugUnitTest
```

### Signierung

Das Release-APK wird mit einem Keystore signiert, dessen Zugangsdaten
**nicht** im Repository liegen. Sie werden aus `keystore.properties`
gelesen — die Datei steht in `.gitignore`.

Zum Bauen eines eigenen Release-APKs:

```bash
cp keystore.properties.example keystore.properties
# Werte eintragen
gradle assembleRelease
```

Keystore erzeugen:

```bash
keytool -genkeypair -v -keystore linkwidget.keystore \
  -alias linkwidget -keyalg RSA -keysize 2048 -validity 10000
```

## Projektstruktur

```
app/src/main/java/com/steimerbyte/linkwidget/
├── LinkWidgetProvider.kt    # Widget, Intent-Auslösung, Layout-Wahl
├── WidgetConfigActivity.kt  # Konfigurationsdialog beim Platzieren
├── MainActivity.kt          # Optionale Übersicht + Test-Button
├── LinkStore.kt             # Persistenz (SharedPreferences)
├── UrlNormalizer.kt         # URL-Normalisierung (getestet)
├── FaviconLoader.kt         # Icon holen, cachen, anzeigen
├── ImageFormat.kt           # Format-Erkennung (PNG/JPEG/SVG/ICO/...)
├── IcoExtractor.kt          # Bild aus ICO-Container lösen
└── LinkData                 # Datenmodell (in LinkStore.kt)
```

## Bekannte Grenzen

- **Kein Emulator-Test möglich** auf dem Build-Host (kein `/dev/kvm`).
  Verifiziert wurden: Bytecode-Analyse des signierten Release-APK,
  Manifest-Prüfung via `aapt2`, Widget-Registrierung (`widgetCategory`,
  `configure`), Signaturprüfung via `apksigner` und die Unit-Tests.
  Der tatsächliche Tap auf dem Gerät ist damit **nicht** geprüft.
- Der Link-Titel wird nur angezeigt, nicht als Lesezeichen gespeichert.
- Keine Mehrfach-Links in *einem* Widget — dafür mehrere Widget-Instanzen.
- **SVG-Favicons werden nicht angezeigt.** Die Android-Plattform hat keinen
  SVG-Decoder. In dem Fall weicht die App auf `/favicon.ico` der Domain aus.
  Wer unbedingt das SVG-Icon will, braucht eine eigene SVG-Rendering-Bibliothek.
- Die Bitmap-Dekodierung selbst ist **nicht** per Unit-Test geprueft:
  `android.graphics.BitmapFactory` ist in lokalen Unit-Tests nicht mockbar.
  Geprueft ist die Struktur der erzeugten Datei (BMP-Header, quadratische
  Masse, Bit-Tiefe) sowie die Extraktion gegen ein echtes ICO.
## Lizenz

Apache License 2.0 — siehe [LICENSE](LICENSE).

**Permissiv.** Jeder darf den Code verwenden, ändern, verbreiten und
kommerziell nutzen, auch in geschlossener Form. Es gibt keine
Offenlegungspflicht für Projekte, die Link Widget einbauen — auch
Firmen dürfen das ohne Lizenz-Kosten und ohne Code offenlegen zu müssen.

Die Apache-2.0-Patentklausel (Abschnitt 3) schützt beide Seiten: Beitragende
geben Patente aus ihren eigenen Beiträgen frei, und Nutzer erhalten einen
Patent-Schutz gegen Ansprüche aus der Nutzung des Projekts.

Kein Copyleft: Link Widget wird bewusst **nicht** unter GPL/AGPL/LGPL
gestellt. Copyleft würde Firmen und Nutzern vorschreiben, ihr gesamtes
Projekt offen zu legen. Das widerspricht der Vorgabe, dass jeder frei
nutzen darf, wie er will.

Copyright 2026 steimerbyte.

Mitwirkende: Minimax (Co-Author im Commit-Verlauf).

### KI-Beitrag

Dieser Code wurde mit einem KI-System erstellt.

Genauer: mit den KI-Systemen der Serie **Minimax**, unter Anleitung und
Entscheidung des Auftraggebers. Die KI hat den Quellcode geschrieben,
gebaut und getestet.

Das Projekt ist frei verwendbar. Wer es forkerst und Änderungen erzeugt,
sollte die KI-Beteiligung im Commit-Verlauf dokumentieren.
