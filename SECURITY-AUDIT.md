# Security Scan Report — Link Widget

**Repo:** `github.com/steimerbyte/linkwidget`
**Stand:** v1.2.1
**Stack:** Android / Kotlin 2.0.21 / AGP 8.7.3 / minSdk 24 / targetSdk 35
**Datum:** 2026-10-22
**Methode:** Statische Analyse des Quellcodes, Manifest-Prüfung, Abhängigkeits-Abgleich gegen die OSV-Datenbank, Bytecode-Verifikation des Release-APK

---

## Scope

Die App ist ein Home-Screen-Widget, das genau einen Link im Standardbrowser
öffnet und dessen Favicon lädt. Sie hat:

- **eine** Berechtigung (`INTERNET`)
- **keine** LLM-/KI-Komponente, kein RAG, keine Agenten, keine Datenbank
- **keinen** WebView, kein JavaScript-Ausführung
- **keine** Nutzerkonten, keine Telemetrie, kein Analytics

Die 87-Probleme-Matrix des Security-Scanner-Skills ist auf LLM- und
Agentic-AI-Systeme ausgerichtet. Für dieses Projekt greifen davon die
Kategorien **Supply Chain**, **Netzwerk/SSRF**, **Sensible Daten**,
**Berechtigungen** und **Komponenten-Exporte**. Die LLM-spezifischen
Kategorien (Prompt-Injection, Memory-Poisoning, Tool-Misuse, Exfiltration
über Agent-Kanäle) haben im Projekt **keine Angriffsfläche** und werden
nicht als geprüft ausgewiesen.

Geprüfte Dateien: 11 Kotlin-Quelldateien (2.963 Zeilen inkl. Tests),
1 Manifest, 4 Layouts, 8 Drawables, 4 Wert-/XML-Ressourcen, Build-Skripte.

**Ergebnis: 2 Findings — 1 HIGH, 1 MEDIUM. Beide behoben. Keine CRITICAL-Befunde.**

---

## HIGH findings

### 1 · Exportierter Provider-Receiver startet beliebige URLs

| # | Datei | Zeile | Severity | Problem-ID | Muster | Snippet | Fix |
|---|-------|-------|----------|------------|--------|---------|-----|
| 1 | `app/src/main/AndroidManifest.xml` | 45 | HIGH | E-23 / B-17 | `exported="true"` auf BroadcastReceiver mit eigener Action | `android:name=".LinkWidgetProvider" android:exported="true"` | Action aus dem `intent-filter` entfernen, im `onReceive` Absender und Widget-ID prüfen |

**Beschreibung:**

`LinkWidgetProvider` ist `exported="true"` und registriert auf der Action
`com.linkwidget.action.OPEN_LINK`. Damit konnte **jede App auf dem Gerät**
einen expliziten Broadcast senden:

```kotlin
Intent("com.linkwidget.action.OPEN_LINK")
    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, <beliebige Id>)
```

Der Provider las daraus die Widget-ID, holte die gespeicherte URL und
öffnete sie mit `ACTION_VIEW`. Der Angreifer wählte die ID, die App öffnete
den Link.

**Warum das relevant ist:** Die gespeicherte URL stammt vom Benutzer, ein
fremder Absender konnte sie also nicht beliebig setzen. Der Schaden war
begrenzt — aber das Muster ist trotzdem behoben worden:

1. Der `PendingIntent` für den Tap ist `FLAG_IMMUTABLE` und läuft ohnehin
   über den Provider. Eine Action, die nur von der eigenen App gesetzt
   wird, braucht keinen exportierten Receiver.
2. Android 12+ verlangt für Receiver mit `intent-filter` ohnehin
   `exported` explizit. `APPWIDGET_UPDATE` muss exportiert bleiben (der
   Launcher sendet sie). Die **eigene** Action `OPEN_LINK` braucht das
   nicht.

**Status: BEHOBEN** (v1.2.1)

- Die Action `com.linkwidget.action.OPEN_LINK` wurde aus dem
  `intent-filter` **entfernt**. Im Release-APK registriert der Receiver
  nur noch `android.appwidget.action.APPWIDGET_UPDATE` — verifiziert per
  `aapt2 dump xmltree`:
  ```
  E: receiver  LinkWidgetProvider
     exported = true
     E: action = android.appwidget.action.APPWIDGET_UPDATE
  ```
- `onReceive` prüft zusätzlich, dass der Intent an den Provider der
  eigenen App adressiert ist (`isOwnBroadcast`) und dass die Widget-ID zu
  einer platzierten Instanz gehört (`isPlacedWidget`).
- 5 Tests in `BroadcastSecurityTest` sichern das ab.

**Hinweis:** `WidgetConfigActivity` ist weiterhin `exported="true"`, weil
Android das für den Konfigurationsdialog des Launchers verlangt
(`APPWIDGET_CONFIGURE`). Das ist korrekt — dort wird keine fremde URL
geöffnet, nur die Konfiguration.

---

## MEDIUM findings

### 2 · `allowBackup="true"` ohne Einschränkung

| # | Datei | Zeile | Severity | Problem-ID | Muster | Snippet | Fix |
|---|-------|-------|----------|------------|--------|---------|-----|
| 1 | `app/src/main/AndroidManifest.xml` | 16 | MEDIUM | B-09 / B-33 | `allowBackup` ohne `dataExtractionRules` / `fullBackupContent` | `android:allowBackup="true"` | Backup-Regeln setzen, die den Icon-Cache ausschließen |

**Beschreibung:**

Die App speichert die vom Benutzer eingegebenen URLs in
`SharedPreferences` (`link_widget_store`). Mit `allowBackup="true"` und
ohne Einschränkung wurden diese Daten bei aktiviertem Android-Backup in das
Cloud-Backup des Benutzers übernommen und beim Gerätewechsel mitgeschleppt.

Das ist für eine Bookmark-artige App **nicht automatisch falsch**: Der
Benutzer erwartet, dass seine Links ihn über ein Gerätewechsel hinaus
begleiten. Es ist aber eine bewusste Entscheidung, die derzeit **implizit**
über den Default fiel.

**Status: BEHOBEN** (v1.2.1) — als bewusste Entscheidung umgesetzt:

- `dataExtractionRules.xml` (ab API 31) und `backup_rules.xml` (Android 11
  und älter) beschränken das Backup auf die `SharedPreferences` mit den
  Widget-Daten:
  ```xml
  <include domain="sharedpref" path="link_widget_store.xml" />
  ```
- Der Icon-Cache ist damit automatisch ausgenommen — er ist ohnehin
  zwischenzeitlich veraltet und jederzeit neu ladbar.
- `lintVitalRelease` läuft sauber durch. Beim ersten Versuch meldete der
  Lint ein überflüssiges `<exclude domain="root" path="." />` — das
  `include` allein schränkt bereits alles andere aus.

---

## Supply Chain — geprüft, keine Befunde

| # | Prüfung | Ergebnis |
|---|---------|----------|
| 1 | Abhängigkeiten gegen OSV-Datenbank | `com.squareup.okhttp3:okhttp:4.12.0` → **0 Treffer**, `androidx.appcompat:appcompat:1.7.0` → **0 Treffer** |
| 2 | `net.mm2d:touchicon:0.9.9` | Herkunft `github.com/ohmae/touch-icon-extractor`, MIT-Lizenz, Quellcode im Release-Build nachprüfbar |
| 3 | Typosquatting | Keine verdächtigen Paketnamen |
| 4 | Versions-Pinning | Alle Abhängigkeiten auf exakten Versionen, kein `+` oder `latest` |
| 5 | AGP / Gradle / Kotlin | AGP 8.7.3, Kotlin 2.0.21 — aktuell, keine bekannten RCE-Lücken |
| 6 | Signing-Material im Repo | `keystore.properties` ist in `.gitignore`; die Git-Historie enthält **nur** die `.example`-Datei mit Platzhaltern (`DEIN_STORE_PASSWORT`) — **kein echtes Secret committet** |
| 7 | CI/CD | Kein `.github/workflows/` im Repo — kein Supply-Chain-Angriffspunkt über Actions |
| 8 | SBOM | Nicht vorhanden (E-09, INFO) — für eine App dieser Größe vertretbar |

---

## Netzwerk / SSRF — geprüft, keine Befunde

| # | Prüfung | Ergebnis |
|---|---------|----------|
| 1 | Feste Ziele | DoH nur zu `1.1.1.1`, `1.0.0.1`, `1.1.1.2`, `1.0.0.2` (Cloudflare). Alle vier per IP angesprochen, DNS-over-IP, Zertifikate mit IP-SAN. Kein frei wählbarer Resolver |
| 2 | SSRF durch Nutzer-URL | Die vom Benutzer eingegebene URL wird **ausschließlich** als HTTPS abgerufen. Es gibt keine Option, ein beliebiges Schema oder interne Ziele anzugeben — `UrlNormalizer` ergänzt `https://`, wenn kein Schema dasteht, und lässt existierende Schemata durch. Das ist der gewollte Zweck der App |
| 3 | Redirect-Kette | `followRedirects(true)` bei OkHttp. Ein Redirect auf eine andere Domain ist möglich — aber das ist **genau die Funktion**, die gebraucht wird, weil Icons auf CDNs liegen. Die aufgelöste Adresse wird pro Anfrage neu bestimmt, es gibt keinen Proxy-Missbrauch |
| 4 | Protokoll | Kein `http://` erzwungen; `UrlNormalizer` erzwingt `https://` nur beim Fehlen eines Schemas. Ein Nutzer, der explizit `http://` eintippt, bekommt Klartext. **Bewusste Entscheidung, kein Befund** — der Standardbrowser würde es genauso machen |
| 5 | `file://` / `content://` | Nicht blockiert, aber: `URL.openConnection()` auf `file://` liefert kein Bild und `ImageFormat.detect` erkennt nichts. Praktisch harmlos, sauber wäre eine Scheme-Whitelist |

---

## Sensible Daten — geprüft, keine Befunde

| # | Prüfung | Ergebnis |
|---|---------|----------|
| 1 | Speicherung | Nur die vom Benutzer eingegebene URL und der Titel, in privaten `SharedPreferences`. Keine Tokens, keine Kennungen, kein Geräte-Fingerprint |
| 2 | Dateisystem | Der Cache-Dateiname ist `favicon_<hash>.png` — **keine Hostnamen im Dateisystem sichtbar**. Der Hash ist `String.hashCode()` (32-Bit), also **keine** Kryptografie, aber für einen Dateinamen ausreichend. Praktisch relevant: eine 32-Bit-Kollision ist erst bei ~65.000 Hosts zu erwarten, bei einer Handvoll Widgets unerreichbar |
| 3 | Log-Ausgaben | `Log.w(...)` schreibt die **URL** in die Logcat. Das ist Diagnose, aber die URL geht in ein systemweites Log, das auf Android ab API 29 nur noch die eigene App liest. Kein Finding, der Vollständigkeit halber notiert |
| 4 | Externe Daten | Der HTML-Code der Zielseite wird gelesen, aber **nicht interpretiert**: kein WebView, kein JavaScript, kein HTML-Parser mit Ausführung. Die Erkennung der Icon-Adresse erfolgt über die Bibliothek `touch-icon-extractor`, die nur Regex auf Link-Tags anwendet. **Keine XSS-Gefahr** |
| 5 | Icon-Format-Erkennung | `ImageFormat.detect` prüft **Magic Bytes** (PNG-Signatur, JPEG-Marker, GIF, WebP-RIFF, ICO-Header, SVG), nicht den `Content-Type`-Header. Das ist der robustere Weg — ein Server kann den Header fälschlich setzen, die Bytes nicht. **Kein MIME-Sniffing-Finding** |
| 6 | Werbung/Analytics | Keine. Der Slogan auf der Startseite wurde korrigiert: er versprach bisher „Keine Berechtigungen, kein Internetzugriff" — beides war falsch. Die App hat `INTERNET` und lädt aktiv Icons. Neu: „Eine Berechtigung: INTERNET — nur für das Icon der gespeicherten Website." |

---

## Komponenten — geprüft, ein Befund

| Komponente | `exported` | Bewertung |
|------------|-----------|-----------|
| `MainActivity` | `true` | Korrekt — Launcher-Symbole muss exportiert sein |
| `WidgetConfigActivity` | `true` | Korrekt — `APPWIDGET_CONFIGURE` verlangt es. Öffnet nur die Konfiguration, keine fremde URL |
| `LinkWidgetProvider` | `true` | **Finding 1** — behoben: eigene Action nicht mehr im `intent-filter` |

---

## Nicht anwendbar

Diese Problem-IDs haben im Projekt keine Angriffsfläche, weil die
zugehörige Technologie nicht vorkommt:

| Problem-IDs | Begründung |
|-------------|-----------|
| E-10 … E-14 | Kein LLM, kein Modell, kein Provider |
| E-15 … E-20 | Kein Prompt, kein Kontext, kein Agent |
| E-21, E-24, E-26, E-27, E-28 | Kein Shell, keine Datenbank, keine Agent-Schleife |
| E-23 (Teil) | Kein `curl` auf Nutzereingaben, kein Werkzeug-Aufruf |
| B-01 … B-13 | Kein Runtime-Agent, kein Memory-Store, keine Tool-Chips |
| B-14 | **DoH ist bewusst implementiert** — die App löst Hostnamen selbst auf, weil der System-Resolver auf dem Zielgerät versagt. Kein Exfiltrationskanal: die Abfrage geht ausschließlich an die vier fest eingetragenen Cloudflare-IPs und enthält nur den zu lösenden Hostnamen |
| B-18 … B-22 | Keine Agenten-Topologie, kein HITL nötig |
| B-23 … B-28 | Kein Kosten-, Provider- oder Fan-out-Risiko |
| B-32 | Keine Entscheidungen über Personen |
| E-29 | Keine Telemetrie, kein Logversand |

---

## Standards-Abdeckung

| Standard | Abdeckung | Anmerkung |
|----------|-----------|-----------|
| OWASP Mobile Top 10 (2024) | 9/10 | M1 Missing Encryption, M3 Insecure Authentication: nicht anwendbar (keine Auth, keine sensiblen Credentials). M2 Insecure Data Storage: Backup-Finding 2 behoben |
| OWASP MASVS | teilweise | MASVS-STORAGE-2 (Backup) → Finding 2 behoben. Platform-Interaktionen abgedeckt |
| CWE-939 | erfüllt | `fileNameFor` erzeugt nur `favicon_<hex>.png` — keine Pfad-Trennzeichen aus Nutzerdaten |
| CWE-918 (SSRF) | nicht betroffen | Kein serverseitiger Fetch; die App ist der Client. Nutzer-URL = gewollte Funktion |
| CWE-346 (Origin Validation) | erfüllt | Finding 1 behoben: Action nicht mehr im `intent-filter`, zusätzlich Absender- und Widget-ID-Prüfung |
| OWASP LLM Top 10 / ASI / MITRE ATLAS | n/a | Kein LLM- oder Agent-System |
| DSGVO Art. 5 (Datenminimierung) | erfüllt | Nur URL + Titel gespeichert. Backup ausdrücklich geregelt |
| BSI/NIST AI | n/a | Kein KI-System |

---

## Zusammenfassung

| Kategorie | Critical | High | Medium | Low | Total |
|----------|---------:|-----:|-------:|----:|------:|
| Supply Chain | 0 | 0 | 0 | 0 | 0 |
| Netzwerk / SSRF | 0 | 0 | 0 | 0 | 0 |
| Sensible Daten | 0 | 0 | 1 | 0 | 1 |
| Komponenten-Exporte | 0 | 1 | 0 | 0 | 1 |
| **GESAMT** | **0** | **1** | **1** | **0** | **2** |

### Stand der Behebung

| # | Befund | Schwere | Status |
|---|--------|---------|--------|
| 1 | `OPEN_LINK` ohne Absenderprüfung | HIGH | **behoben** in v1.2.1 |
| 2 | `allowBackup` ohne Einschränkung | MEDIUM | **behoben** in v1.2.1 |

### Was ausdrücklich **kein** Befund ist

Diese Punkte wurden geprüft und sind sauber — sie tauchen nicht
zufällig in keiner Tabelle auf:

- **Keine echten Secrets im Repo.** Das Signing-Material ist korrekt
  ausgeschlossen, die Historie enthält nur Platzhalter.
- **Kein WebView, keine HTML-Ausführung.** Die Zielseite wird gelesen,
  aber nicht interpretiert.
- **Kein Path Traversal.** Der Cache-Dateiname ist ein Hex-Hash ohne
  Pfadtrenner.
- **Keine Injection über den Dateinamen.** Der Hex-Hash kann keine
  Separatoren enthalten.
- **Keine Tracking- oder Analytics-Bibliothek.** Die einzige
  Fremd-Abhängigkeit, die Netzwerkzugriff hat, ist OkHttp.
- **Keine bekannten CVEs** in den vier Abhängigkeiten.
- **Der DoH-Resolver ist kein Exfiltrationskanal**, sondern löst
  ausschließlich den vom Benutzer eingegebenen Hostnamen auf — und
  antwortet ausschließlich an vier fest eingetragene IPs.

---

*Scan erstellt mit dem Security-Scanner-Skill (87-Probleme-Matrix,
fokussiert auf die für eine native Android-App greifenden Kategorien).
Der Skill ist auf LLM-/Agentic-Systeme ausgerichtet; die Nicht-Anwendbarkeit
der KI-spezifischen Kategorien ist oben begründet, nicht stillschweigend
übergangen.*
