# 📚 Benny's Library – ein gemütlicher E-Book-Reader für Android

Benny's Library ist ein E-Book-Reader, in dem deine Bücher in **selbst eingerichteten Zimmern** wohnen.
Du stehst im Flur, wischst von Tür zu Tür und trittst ein: ins Kaminzimmer, Klavierzimmer, Wohnzimmer, Schlafzimmer,
die Bibliothek … In jedem Zimmer stehen echte Bücherregale mit deinen Büchern – und du richtest alles selbst ein.

| Flur | Zimmer | Regal-Nahansicht | Übersicht |
|---|---|---|---|
| ![](docs/screenshots/02_home.png) | ![](docs/screenshots/03_room.png) | ![](docs/screenshots/04_bookcase_closeup.png) | ![](docs/screenshots/04c_room_overview.png) |

| Einrichten | Lesen | Kaminlicht-Modus | Vorlesestimme |
|---|---|---|---|
| ![](docs/screenshots/04d_room_catalog.png) | ![](docs/screenshots/07_reader.png) | ![](docs/screenshots/10_reader_cozy.png) | ![](docs/screenshots/09b_voice_sheet.png) |

## Flur & Zimmer
- **Flur mit Zimmertüren**: wischen von Tür zu Tür, 6 Türstile (Kassette, Rundbogen, Glastür mit Blick ins Zimmer, Flügeltür, Landhaus, Scheunentor), Namensschild, Licht unter der Tür, Tag-/Nachtbeleuchtung. Antippen öffnet die Tür mit Animation, langes Drücken öffnet die Einstellungen.
- **Begehbare Zimmer**: breiter als der Bildschirm – wischen zum Umschauen, Übersicht per Zoom.
- **Einrichtungsmodus**: alles frei verschieben, mit zwei Fingern skalieren, spiegeln, nach vorn/hinten, umfärben, Varianten wählen, löschen – und aus einem Katalog mit über 30 Dingen hinzufügen: Kamin, Klavier, Sofa, Ohrensessel, Bett, Schreibtisch, Standuhr, Plattenspieler, Bibliotheksleiter, Teleskop, Globus, Stehlampe, Kronleuchter, Wandleuchte, Kerzen, Lichterkette, Fenster (auch Rundbogen), Gemälde, **eigene Fotos im Rahmen**, Spiegel, Wanduhr, Pflanzen, Hängepflanze, Blumen, Teppiche, Katze, Hund …
- **Lebendig**: Kaminfeuer, Kerzenflackern, Regen/Schnee/Gewitter am Fenster, echte Uhrzeit, Pendel, atmende Haustiere. Antippen: Licht an/aus, Wetter wechseln, Plattenspieler starten, Katze streicheln (♥).
- Wände, Tapeten, Boden, Licht, Zimmergröße und Raumklang (Kaminknistern, Regen, Wind, Meer, Uhr, Klavier – offline erzeugt).

## Regale & Bücher
- **Regale stehen wirklich im Zimmer** – 8 Bauformen: Bücherschrank, breites Regal, Sideboard, Wandbretter, Leiterregal, Vitrine mit Glastüren, Bogenregal, Würfelregal; 8 Holzarten
- **Anordnung der Bücher**: ordentlich, Regenbogen (nach Farben), gemütlich gemischt (Stapel, schräg lehnende Bücher, Deko wie Kerzen, Eulen, Sanduhr), Cover nach vorn oder Stapel
- Antippen eines Regals zoomt in die Nahansicht mit lesbaren Buchrücken
- Mehrere Regale pro Zimmer, jedes mit eigenen Ordnern (Android-Ordnerfreigabe, inkl. Unterordner) und Dateitypen
- Formate: **EPUB, PDF, TXT, Markdown, HTML, FB2, DOCX, ODT, RTF**
- Cover aus der Datei, online (Open Library / Google Books), aus der Galerie oder als generierter Stoffeinband
- Cover- oder Buchrücken-Ansicht, Holzarten, Sortierung, Lesebändchen, „Neu“- und „Gelesen“-Markierungen
- Animation beim Öffnen: Das Buch wird aus dem Regal gezogen und schlägt auf

## Lesen
- Echter Buchsatz: Blocksatz, Silbentrennung, Einzüge, Bilder, Kapitel; Blättern (Umblättern, Schieben, Stapel, Überblenden) oder Scrollen
- 9 Lese-Schriften + eigene TTF/OTF; Größe, Stärke, Zeilen-, Zeichen- & Absatzabstand, Ränder
- 8 Farbwelten (Papier, Sepia, Pergament, Salbei, Kaminlicht, Dämmerung, Mitternachtsblau, OLED-Nacht), automatischer Nachtmodus, Papierstruktur, Wärmefilter, Helligkeit
- **Gemütlicher Lesemodus** mit flackerndem Kerzenlicht und Vignette
- Lesezeichen, farbige Markierungen, Unterstreichungen, Notizen (in Handschrift-Optik)
- **Gefällt mir**: Auswahl, Satz/Zeile, Passage oder ganze Seite in Sammlungen aufbewahren und im Vollbild-Modus **wiederlesen** (auch zufällig / automatisch)
- Volltextsuche, Inhaltsverzeichnis, Restzeit im Kapitel, Uhr, Fortschritt
- **Vorlesen** mit Wort-Hervorhebung und Schlaftimer:
  - alle installierten Gerätestimmen mit Hörprobe, Stimmhöhe (tief ↔ hoch), Tempo und Presets („Tief & warm“, „Gutenachtgeschichte“, „Hörbuch“ …)
  - **Erzählmodus**: natürliche Pausen an Sätzen/Absätzen, wörtliche Rede mit eigener Stimme oder Tonhöhe, Betonung von Fragen und Ausrufen
  - optional **KI-Hörbuchstimme** (OpenAI, eigener API-Schlüssel): versteht den Text und liest wie ein Sprecher – mit wählbarem Vortragsstil
- Der Klang des Zimmers, aus dem das Buch stammt, spielt beim Lesen leise weiter
- Blättern mit Lautstärketasten, Tippzonen, Vollbild, Bildschirm anlassen
- PDF: Originalansicht mit Zoom, Sepia/Nacht-Filter, Seiten-Lesezeichen & -Notizen – oder Textmodus mit allen Reader-Funktionen

## Außerdem
- Zitate als **Bildkarte teilen** (Papier, Kaminlicht, Nachtblau)
- Lesestatistik mit Tagesziel, Lesesträhne und Diagramm
- Notizen-Übersicht mit Markdown-Export
- Sicherung & Wiederherstellung (JSON)

## Bauen
Voraussetzungen: JDK 17+, Android SDK (Plattform 35).

```bash
./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # Parser- und Screenshot-Tests (Robolectric)
```

Jeder Push baut die APK außerdem per GitHub Actions („Android APK bauen“ → Artefakt *BennysLibrary-debug-apk*).

Technik: Kotlin, Jetpack Compose (Material 3), Room, Coil, Jsoup, PdfBox-Android. Mindestens Android 8.0.

Schriften (SIL Open Font License): Literata, EB Garamond, Lora, Crimson Pro, Atkinson Hyperlegible, Nunito, Playfair Display, Caveat.
