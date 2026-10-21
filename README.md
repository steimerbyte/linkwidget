# Link Widget

Eine Nothing-OS-inspirierte Android-App, die genau einen Use-Case abbildet:
**ein Home-Screen-Widget, das einen Link im Standardbrowser öffnet.**

Kein Bookmark-Manager. Kein Konto. Keine Cloud. Keine Berechtigungen.

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

1. APK aus `app/build/outputs/apk/release/app-release.apk` auf das Gerät übertragen
2. Datei antippen → Play Protect meldet „App aus unbekannter Quelle" → **Installieren**
3. Long-press auf dem Home-Screen → **Widgets** → **Link Widget** auf den Bildschirm ziehen
4. URL eingeben, optional Titel → **Speichern**

Mehrere Links: das Widget mehrfach platzieren. Jede Instanz speichert
ihren eigenen Link.

## Berechtigungen

Die App deklariert **keine** Berechtigungen.

`aapt2 dump permissions` auf dem Release-APK liefert genau einen Eintrag:

```
uses-permission: name='com.steviroberts.linkwidget.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'
```

Das ist eine interne AndroidX-Sperre gegen exportierte Empfänger, kein
Zugriff auf Daten. Kein `INTERNET`, kein Speicherzugriff, kein Standort.

Der Link wird im `ACTION_VIEW`-Intent direkt übergeben. Es findet **kein**
Netzwerkzugriff statt — weder beim Speichern noch beim Öffnen.

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

`app/src/test/.../UrlNormalizerTest.kt` deckt die URL-Normalisierung ab —
12 Fälle, darunter `mailto:`, `tel:`, Custom Schemes und der Fall
`example.com/a:b`, bei dem ein Doppelpunkt **kein** Schema ist.

Der Test prüft die echte Produktionsklasse `UrlNormalizer`, keine Kopie.

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
app/src/main/java/com/steviroberts/linkwidget/
├── LinkWidgetProvider.kt    # Widget, Intent-Auslösung
├── WidgetConfigActivity.kt  # Konfigurationsdialog beim Platzieren
├── MainActivity.kt          # Optionale Übersicht + Test-Button
├── LinkStore.kt             # Persistenz (SharedPreferences)
├── UrlNormalizer.kt         # URL-Normalisierung (getestet)
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

Der Code dieses Projekts wurde mit KI-Systemen erstellt. Konkret: die
KI-Systeme der Serie **Minimax** haben den Quellcode geschrieben, gebaut
und getestet — unter Anleitung und Entscheidung des Auftraggebers.

- **Urheberrecht:** liegt beim Auftraggeber (`steimerbyte`), nicht beim
  KI-Modell. Das Modell ist kein Rechtssubjekt.
- **Verantwortung:** liegt beim Auftraggeber. Fehler im Code sind zu
  verantworten — die KI hat sie weder zu verbergen noch zu
  beschönigen, aber sie kann sie auch nicht verantworten.
- **Nachprüfbarkeit:** Die Entstehungshistorie ist im Commit-Verlauf
  dokumentiert, einschließlich der `Co-Authored-By`-Trailer. Der Build
  ist reproduzierbar: `gradle clean assembleRelease` erzeugt aus dem
  Quellcode ein signiertes APK.

Der Beitrag, den ein KI-Baustein im Code leistet, unterscheidet sich
nicht vom Beitrag jedes anderen Menschen: Er steht unter derselben
Lizenz (Apache 2.0) und unter denselben Regeln.

Wenn du dieses Projekt forkerst und Änderungen per KI erzeugst, gilt
dasselbe: Beitragende sind für ihre Beiträge verantwortlich, nicht das
Modell. Bitte dokumentiere KI-Beteiligung im Commit-Verlauf.
