# Changelog

Alle nennenswerten Änderungen an diesem Projekt.

Format: [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
Versionierung: [Semantic Versioning](https://semver.org/lang/de/).

## [1.2.1] — 2026-10-22

### Geändert
- Security-Scan durchgeführt und dokumentiert
  ([SECURITY-AUDIT.md](SECURITY-AUDIT.md)). Zwei Befunde, beide behoben:
  - **HIGH** — der exportierte `LinkWidgetProvider` registrierte die
    eigene Action `com.linkwidget.action.OPEN_LINK`. Damit konnte jede
    App auf dem Gerät über einen Broadcast einen gespeicherten Link
    öffnen lassen. Die Action ist aus dem `intent-filter` entfernt,
    `onReceive` prüft zusätzlich Absender und Widget-ID.
  - **MEDIUM** — `allowBackup` ohne Einschränkung. Die Backup-Regeln
    beschränken das Backup jetzt auf die Widget-Daten; der Icon-Cache
    ist ausgenommen.
- Der Slogan auf der Startseite versprach „Keine Berechtigungen, kein
  Internetzugriff". Beides war falsch — die App hat `INTERNET` und lädt
  aktiv Icons. Korrigiert.
- Die README nannte es „Keine Cloud" ohne das Android-System-Backup zu
  erwähnen. Der Backup-Abschnitt ist ergänzt.
- Toter Code entfernt: `ACTION_REFRESH` wurde nirgends ausgelöst.

## [1.2.0] — 2026-10-22

### Hinzugefügt
- Das Icon füllt den verfügbaren Platz. Die `ImageView` hatte eine feste
  Größe von 40 dp, das Bild blieb also klein, egal wie groß das Widget
  gezogen wurde.
- Neuer Schalter **Punkt anzeigen**: blendet den Punkt unter dem Icon aus.
- Dritter Anzeigeschalter in der Konfiguration.

## [1.1.0] — 2026-10-22

### Hinzugefügt
- Zwei Anzeigeschalter pro Widget: **Titel anzeigen** und
  **URL anzeigen**. Gilt pro Widget-Instanz, wirkt sofort, und ist auch
  nachträglich über „Bearbeiten" änderbar.

## [1.0.6] — 2026-10-22

### Behoben
- **TLS-SNI wiederhergestellt.** In 1.0.4 war die IP in die URL
  geschrieben worden, um die DNS-Auflösung zu umgehen. TLS-SNI wird aber
  aus dem URL-Hostnamen gebildet; Nginx antwortete darauf mit
  `TLSV1_ALERT_UNRECOGNIZED_NAME`.
- Die DNS-Auflösung gilt jetzt für die **ganze Domain** statt nur für
  einen Host. Verweist ein Icon auf eine Subdomain oder einen CDN-Host,
  scheiterte der Abruf vorher am System-Resolver.
- Alle HTTP-Zugriffe laufen über OkHttp — `HttpURLConnection` lässt sich
  kein eigener Resolver geben.

## [1.0.5] — 2026-10-22

### Hinzugefügt
- DNS über HTTPS (DoH) an Cloudflare, direkt über die IP-Adresse
  angesprochen. Löst das Problem, dass Mobilfunkbetreiber ausgehendes
  UDP/53 blockieren.

### Geändert
- Nur Cloudflare beantwortet DoH über die IP-Adresse mit
  `application/dns-json`; Google, Quad9 und OpenResolv nicht.

## [1.0.4] — 2026-10-22

### Hinzugefügt
- Eigene DNS-Auflösung per UDP an öffentliche Resolver, falls der
  System-Resolver auf dem Gerät versagt.

## [1.0.3] — 2026-10-22

### Hinzugefügt
- Die URL wird automatisch gespeichert, sobald sie sich ändert.
- Terminal-Ausgabe beim Icon-Abruf, Zeile für Zeile, mit Kopierknopf.
  Damit lässt sich ein Fehlschlag überhaupt erst eingrenzen.

### Behoben
- Zwei Stellen im Code haben die Fehlerursache verschluckt. Die
  Bibliothek liefert bei einem Netzfehler eine leere Liste statt einer
  Ausnahme — daraus wurde die falsche Meldung „Seite liefert kein
  Icon".

## [1.0.2] — 2026-10-22

### Geändert
- Die Icon-Ermittlung nutzt `touch-icon-extractor` statt eigenem
  HTML-Parser. Dadurch wird auch das **Web-App-Manifest** ausgewertet —
  dort liegt bei modernen Seiten das beste Icon (512×512 statt 192×192).

### Behoben
- Die Icon-Kandidaten werden nach Größe sortiert. Ohne Sortierung gewinnt
  das 16×16-`favicon.ico`, das bei den meisten Seiten zuerst im Dokument
  steht.

## [1.0.1] — 2026-10-22

### Hinzugefügt
- Abruf-Button in der Konfiguration mit Live-Vorschau und Statuszeile.

## [1.0.0] — 2026-10-21

### Hinzugefügt
- Erstes Release: Home-Screen-Widget, das einen Link im
  Standardbrowser öffnet.
- Favicon-Abruf mit ICO-Entpackung.
- Layout passt sich der Widget-Breite an (kompakt / voll).

[1.2.1]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.2.1
[1.2.0]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.2.0
[1.1.0]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.1.0
[1.0.6]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.6
[1.0.5]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.5
[1.0.4]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.4
[1.0.3]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.3
[1.0.2]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.2
[1.0.1]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.1
[1.0.0]: https://github.com/steimerbyte/linkwidget/releases/tag/v1.0.0