# Fotografiertes DACH-Spielbrett

Die fünf Originalfotos liegen unter `media-resources/pics/`.
`IMG_20250124_114319.jpg` zeigt das gesamte Brett; die übrigen Fotos dienen
zur Kontrolle der Feldfolge und der lesbaren Beschriftungen.

## Darstellung

`prepare_board.py` entzerrt den äußeren Brettrand anhand gemessener Eckpunkte,
dreht GO nach rechts unten und erzeugt die gebündelte Android-Ressource
`app/src/main/res/drawable/board_reference.webp`. Es werden keine Bilder
zur Laufzeit heruntergeladen. Die Perspektivkorrektur ist eine angenäherte
Vierecktransformation; Falten und ungleichmäßige handgemachte Feldbreiten
bleiben bewusst sichtbar.

```sh
py -m pip install Pillow
py media-resources/prepare_board.py
```

Pillow wird nur für die Asset-Erzeugung gebraucht, nicht für den Android-Build
oder zum Spielen. Das generierte WebP ist bereits enthalten.

`PhotoBoard.kt` legt gemessene antippbare Bereiche, nummerierte Spielfiguren,
Besitzstreifen und Häuser über das Foto. Die Vergrößerung erlaubt horizontales
Scrollen; das gesamte Brett bleibt über die normale Seitenbewegung erreichbar.
Die App-Oberfläche und Dialoge behalten Freetime Design / Material 3 Expressive
und Material You. Das Foto folgt absichtlich nicht den dynamischen Themefarben.

## Rekonstruktion und Grenzen

Die Vorlage hat 34 Felder: zehn Schritte entlang der unteren Kante und acht
entlang jeder anderen Kante. Eckfelder: GO (0), Gefängnis (10), Frei Parken (18),
Gehe ins Gefängnis (26). Die Feldfolge steht in `GameRules.board` und ist getestet.

Lesbar sind unter anderem Europa-Park, Lufthansa, SBB, DB, ÖBB, Alpenzoo,
Regionalwerke Baden und Energieversorgung Mittelrhein. Nicht eindeutig
identifizierbare Fotomotive erhalten beschreibende Namen. Preise, Mietstaffeln
und Ereignisse sind vereinfachte Spielregeln, da auf den Fotos keine lesbaren
Grundstückskarten, vollständigen Kartenstapel oder Regeltexte zu sehen sind.
Chance und Kanzlei sind als getrennte Feldbezeichnungen umgesetzt, nutzen
jedoch dieselbe vereinfachte lokale Zufallsmechanik.

Die Speicherformatversion ist nun 2. Spielstände des vorherigen 24-Felder-Bretts
werden nicht geladen oder stillschweigend auf andere Grundstücke umgedeutet.
Ein neues Spiel ersetzt den alten lokalen Spielstand.
