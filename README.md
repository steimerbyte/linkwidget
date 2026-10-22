[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/steimerbyte)
> ⭐ If you find this useful, consider [supporting me on Ko-fi](https://ko-fi.com/steimerbyte)!
<img src="https://storage.ko-fi.com/cdn/generated/fhfuc7slzawvi/2026-04-23_rest-162bec27f642a562eb8401eb0ceb3940-onjpojl8.jpg" width="250" alt="steimerbyte" style="border-radius: 5%; margin: 16px 0; max-width: 100%;"/>

# Link Widget

Eine Nothing-OS-inspirierte Android-App, die genau einen Use-Case abbildet:
**ein Home-Screen-Widget, das einen Link im Standardbrowser öffnet.**

Kein Bookmark-Manager. Kein Konto. Kein Tracking, keine Analytics, keine
Weitergabe an Dritte. Eine Berechtigung: INTERNET fuer das Favicon der
gespeicherten Website — sonst nichts.

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

### Backup

Die gespeicherten URLs und Titel werden **gesichert**, wenn das
Android-Backup des Geraets aktiv ist. Das ist Absicht: die Widgets sollen
einen Geraetewechsel ueberleben.

Die Regeln dafuer liegen in `res/xml/data_extraction_rules.xml` (ab
Android 12) und `res/xml/backup_rules.xml` (aeltere Versionen). Sie
schraenken das Backup auf die SharedPreferences mit den Widget-Daten ein -
der Icon-Cache ist ausgenommen.

Wer das nicht moechte, kann das Android-Backup in den Systemeinstellungen
deaktivieren. Die App selbst hat keinen Einfluss darauf und sendet nichts
an eigene Server.

## Sicherheit

Ein vollstaendiger Scan liegt in **[SECURITY-AUDIT.md](SECURITY-AUDIT.md)**.
Zusammenfassung:

| Bereich | Befund |
|---|---|
| Secrets im Repo | keine — `keystore.properties` ist ausgeschlossen, die Historie enthaelt nur Platzhalter |
| Abhaengigkeiten | keine bekannten CVEs (geprueft gegen die OSV-Datenbank) |
| WebView / HTML-Ausfuehrung | keine — die Zielseite wird gelesen, aber nicht interpretiert |
| Path Traversal | nicht moeglich — der Cache-Dateiname ist ein Hex-Hash ohne Pfadtrenner |
| Tracking / Analytics | keine |
| Externe Receiver | die eigene Action ist nicht im `intent-filter` registriert; `onReceive` prueft Absender und Widget-ID |

## Favicons

Beim Speichern der URL wird das Icon der Website geladen und auf dem
Widget angezeigt - so wie ein Browser es tut.

### Die Terminal-Ausgabe

Der Button **„Icon laden"** schreibt jeden einzelnen Schritt in ein
Terminal-Feld, Zeile fuer Zeile. Das ist kein Beiwerk: ein
Fehlschlag ist sonst nicht eingrenzbar.

„Kein verwendbares Icon gefunden" konnte dreierlei bedeuten — das
Geraet kommt nicht ins Netz, der Hostname laesst sich nicht
aufloesen, oder die Seite liefert tatsaechlich kein Icon. Die
Ausgabe trennt diese Faelle:

```
--- Umgebung ---
Android 15 (API 35)
Geraet Nothing Phone (3a) Pro
Ziel https://omp.steimercloud.xyz
DNS  77.11.143.146
HTTP 302 von omp.steimercloud.xyz
Netzwerkzugriff moeglich

HTML wird gelesen ...
5 Icons erkannt, nach Groesse sortiert:
  MANIFEST 262144 px² .../icons/icon-512.png
  ICON 36864 px² .../icons/icon-192.png
  APPLE_TOUCH_ICON 32400 px² .../apple-touch-icon.png
  ICON 256 px² .../favicon.ico

[0] https://omp.steimercloud.xyz/icons/icon-512.png
      34195 Bytes, Format PNG
      -> 34195 Bytes gespeichert
Ergebnis: 512x512 Pixel
```

Bei einem Netzfehler nennt der DNS- oder HTTP-Schritt die Ausnahme
**mit ihrer Ursache**, weil dort die eigentliche Information steckt:

```
DNS  FEHLER: UnknownHostException: gibt-es-nicht.xyz
```

Die Ausgabe laesst sich ueber **Kopieren** in die Zwischenablage
uebernehmen und hier einfuegen.

### Eigene Namensaufloesung

Die App loest Hostnamen **nicht** ueber den System-Resolver, sondern
selbst. Grund ist ein Verhalten, das sich auf dem Zielgeraet gezeigt hat:
Der System-Resolver loeste dort den Hostnamen nicht auf, obwohl er
ueberall sonst aufloesbar war — im LTE-Netz antwortete kein
UDP-Resolver.

Die Reihenfolge:

1. **DNS ueber HTTPS** an `1.1.1.1`, `1.0.0.1`, `1.1.1.2`, `1.0.0.2`
   (Cloudflare), jeweils **direkt ueber die IP-Adresse** angesprochen.
   DoH laeuft ueber TCP/443 und wird von Mobilfunkbetreibern nicht
   blockiert.
2. **Rohe DNS-Abfrage** per UDP an dieselben und zwei weitere Resolver.
3. **System-Resolver** als letzte Moeglichkeit.

**Datenschutz:** Die Abfrage geht ausschliesslich an diese vier fest
eingetragenen IPs und enthaelt nur den zu aufloesenden Hostnamen. Es
werden keine Daten ueber den Nutzer uebertragen, keine anderen
Hostnamen mitgegeben und keine anderen Dienste angefragt. Nur diese
Cloudflare-Adressen sind es, die den Hostnamen der Zielseite zu Gesicht
bekommen — und das ist genau der, den der Nutzer ohnehin eingegeben hat.

**TLS:** Der Hostname wird **nie** durch die IP ersetzt. Er bleibt in
der URL, damit SNI und Host-Header ihn tragen. Die aufgeloeste Adresse
steckt ausschliesslich in der Namensaufloesung. Wuerde stattdessen die
IP in die URL geschrieben, antwortet Nginx mit
`TLSV1_ALERT_UNRECOGNIZED_NAME`.


### Automatisches Speichern

Die URL wird gespeichert, sobald sie sich aendert — nicht erst beim
Drücken von **Speichern**. Der Abruf-Button speichert ebenfalls, damit
die URL auch dann nicht verloren geht, wenn nur das Icon scheitert.


Der Ablauf:

1. Die Icon-Kandidaten liefert die Bibliothek
   [`touch-icon-extractor`](https://github.com/ohmae/touch-icon-extractor)
   (`net.mm2d:touchicon`). Sie holt die Seite, parst das `<head>` und
   wertet zusaetzlich das **Web-App-Manifest** aus.
2. Die Kandidaten werden nach Groesse sortiert, der beste zuerst.
3. Das Icon wird geladen und - falls noetig - in ein Format gebracht,
   das Android dekodieren kann.

Es wird **kein Drittanbieter-Dienst** eingeschaltet. Die einzige
aufgerufene Domain ist die, die der Benutzer selbst eingegeben hat.

### Warum die Bibliothek und nicht eigener Code

Der eigene HTML-Parser fand das Icon aus dem **Web-App-Manifest**
nicht. Bei einer Next.js-Anwendung ist genau dort das beste Icon
hinterlegt. Fuer `omp.steimercloud.xyz` liefert die Seite:

| Quelle | Icon | Groesse |
|---|---|---|
| `<link rel="icon">` | `favicon.ico` | 16x16 |
| `<link rel="icon">` | `icons/icon-192.png` | 192x192 |
| `<link rel="apple-touch-icon">` | `icons/apple-touch-icon.png` | 180x180 |
| Web-App-Manifest | `icons/icon-192.png` | 192x192 |
| **Web-App-Manifest** | **`icons/icon-512.png`** | **512x512** |

Der Manifest-Treffer ist die Vierfach-Groesse des besten
HTML-Icons. Ohne Manifest-Auswertung war das Icon auf dem Homescreen
deutlich unscharfer als noetig.

Die Bibliothek liefert die Kandidaten allerdings in
**Dokumentreihenfolge, nicht nach Guete sortiert**. Sie zu uebernehmen
ohne eigene Sortierung fuehrt genau zum Fehler, den die Bibliothek
nicht behebt: das 16x16-`favicon.ico` steht bei den meisten Seiten
zuerst im Dokument. Die App sortiert deshalb selbst, nach Flaeche
und dann nach `rel`-Prioritaet:

```kotlin
val sorted = icons.sortedWith(
    compareByDescending<Icon> { it.inferArea() }
        .thenBy { it.rel.priority }
)
```

Der Vergleich laeuft ueber `inferArea()` und `rel.priority`, nicht ueber
den Comparator der Bibliothek: dessen Felder sind in Version 0.9.9
nicht oeffentlich zugaenglich.

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

## Anzeigeoptionen

Jedes Widget hat drei Schalter in der Konfiguration:

| Schalter | Wirkung |
|---|---|
| **Titel anzeigen** | blendet die Beschriftung aus - es bleibt nur das Icon |
| **URL anzeigen** | blendet die vollstaendige Adresse unter dem Titel aus |
| **Punkt anzeigen** | blendet den Punkt unter dem Icon aus |

Die Optionen gelten **pro Widget-Instanz**. Jedes Widget im
Homescreen laesst sich einzeln einstellen.

Die Schalter wirken **sofort**: es wird gespeichert und das Widget
neu gezeichnet, ohne dass der Speichern-Knopf gedrueckt werden
muss.

Um die Einstellungen eines bereits platzierten Widgets zu aendern,
das Widget lange druecken und „Bearbeiten" waehlen. Der Launcher
oeffnet dann die Konfiguration erneut - moeglich ist das durch
`android:widgetFeatures="reconfigurable"`.

**Zur URL-Zeile:** Sie erscheint nur, wenn ein eigener Titel
gesetzt ist. Ohne Titel zeigt die Beschriftung bereits den Host -
die vollstaendige Adresse waere dann eine doppelte Information.
Mit ausgeblendeter Beschriftung entfaellt die Zeile ebenfalls.

**Das Icon fuellt den verfuegbaren Platz.** Die ImageView hat keine
feste Groesse mehr, sondern `layout_weight="1"` - sie waechst mit dem
Widget. Das Bild wird mittig darin skaliert (`scaleType="fitCenter"`),
behaelt also sein Seitenverhaeltnis.

Der Punkt ist die Nothing-OS-typische Textur unter dem Icon. Wer ihn
ausblendet, sieht nur noch das Icon selbst.



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

Copyright 2026 steimerbyte. Die Versionshistorie steht in
[CHANGELOG.md](CHANGELOG.md).

Mitwirkende: Minimax (Co-Author im Commit-Verlauf).

## Hinweis zur KI-Unterstützung

Dieser Code wurde mit einem KI-System erstellt.

Genauer: mit den KI-Systemen der Serie **Minimax**, unter Anleitung und
Entscheidung des Auftraggebers. Die KI hat den Quellcode geschrieben,
gebaut und getestet.

Das Projekt ist frei verwendbar. Wer es forkerst und Änderungen erzeugt,
sollte die KI-Beteiligung im Commit-Verlauf dokumentieren.
