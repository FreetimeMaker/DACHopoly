"""Generate offline game artwork and its static Kotlin catalogue.

Run from any directory with: py media-resources/prepare_game_art.py
Requires Pillow. Only art_*.webp and GameArt.kt are written.
"""

import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "media-resources/pics"
OUTPUT = ROOT / "app/src/main/res/drawable-nodpi"
KOTLIN = ROOT / "app/src/main/java/com/freetime/dachopoly/GameArt.kt"


def main():
    groups = {}
    for key, folder, count, label in (
        ("chance", "Chance-Karten", 32, "Chance-Karte"),
        ("kanzlei", "Kanzlei-Karten", 32, "Kanzlei-Karte"),
        ("properties", "Grundstücks-Karten", 44, "Grundstückskarte"),
    ):
        groups[key] = []
        for number in range(1, count + 1):
            side = (
                ("Eigentum" if number % 2 == 0 else "Verpfändet")
                if key == "properties"
                else ("Vorderseite" if number % 2 == 0 else "Rückseite")
            )
            groups[key].append(
                (
                    SOURCE / folder / f"{number}.png",
                    f"art_{key}_{number:02}",
                    f"{label} {number} – {side}",
                    key != "properties",
                )
            )
    groups["money"] = [
        (
            SOURCE / f"{amount} Freuken.png",
            f"art_money_{number:02}",
            f"{amount} Freuken",
            False,
        )
        for number, amount in enumerate(
            (
                "50´000",
                "100´000",
                "200´000",
                "500´000",
                "1´000´000",
                "2´000´000",
                "5´000´000",
                "10´000´000",
            ),
            1,
        )
    ]
    groups["symbols"] = [
        (
            SOURCE / "Design ohne Titel" / f"{number}.png",
            f"art_symbol_{number:02}",
            title,
            False,
        )
        for number, title in enumerate(
            (
                "Truhe",
                "GO-Symbol",
                "Gefängnis",
                "Polizist",
                "Parkplatz",
                "Fragezeichen",
                "Ring",
            ),
            1,
        )
    ] + [
        (SOURCE / "Design ohne Titel.png", "art_symbol_tax", "Steuer-Diamant", False),
        (
            SOURCE / "Nimm 2 Mio. Freuken.png",
            "art_symbol_go",
            "GO – Nimm 2 Mio. Freuken",
            False,
        ),
    ]
    groups["center"] = [
        (SOURCE / "Your paragraph text.png", "art_center", "Spielbrett-Mitte", False)
    ]
    entries = [entry for group in groups.values() for entry in group]
    expected = {entry[0] for entry in entries}
    actual = set(SOURCE.rglob("*.png"))
    if expected != actual or len(entries) != 126:
        raise ValueError(
            f"PNG inventory mismatch: missing={expected - actual}, unexpected={actual - expected}"
        )
    OUTPUT.mkdir(parents=True, exist_ok=True)
    declarations = []
    total_bytes = 0
    for key, group in groups.items():
        rows = []
        for source, name, title, rotate in group:
            with Image.open(source) as original:
                image = original.convert(
                    "RGBA" if "A" in original.getbands() else "RGB"
                )
                if rotate:
                    image = image.transpose(Image.Transpose.ROTATE_90)
                image.thumbnail((1200, 1200), Image.Resampling.LANCZOS)
                size = image.size
                target = OUTPUT / f"{name}.webp"
                image.save(target, "WEBP", quality=92, method=6)
            with Image.open(target) as check:
                check.load()
                if check.format != "WEBP" or check.size != size or max(size) > 1200:
                    raise ValueError(f"Invalid generated resource: {target}")
            total_bytes += target.stat().st_size
            rows.append(
                f"GameArtwork({json.dumps(title, ensure_ascii=False)}, R.drawable.{name}, {size[0] / size[1]:.8f}f)"
            )
        if key == "center":
            declarations.append(f"    val center: GameArtwork = {rows[0]}")
        else:
            declarations.append(
                f"    val {key}: List<GameArtwork> = listOf(\n"
                + "\n".join(f"        {row}," for row in rows)
                + "\n    )"
            )
    mapping = {
        1: 2,
        3: 4,
        5: 6,
        6: 8,
        8: 10,
        9: 12,
        11: 14,
        12: 16,
        13: 18,
        14: 20,
        15: 22,
        17: 24,
        19: 26,
        20: 28,
        22: 30,
        23: 32,
        24: 34,
        25: 36,
        27: 38,
        29: 40,
        31: 42,
        33: 44,
    }
    cases = "\n".join(
        f"        {tile} -> properties[{deed - 1}]" for tile, deed in mapping.items()
    )
    kotlin = (
        HEADER
        + "\n\n".join(declarations)
        + "\n\n    // Rough board-index mapping to the supplied even-numbered deeds.\n    fun property(tileIndex: Int): GameArtwork? = when (tileIndex) {\n"
        + cases
        + "\n        else -> null\n    }\n}\n"
        + UI
    )
    KOTLIN.write_text(kotlin, encoding="utf-8")
    print(
        f"Generated and decoded {len(entries)} WebP resources ({total_bytes / 1024 / 1024:.2f} MiB), plus {KOTLIN.name}."
    )


HEADER = """// Generated by media-resources/prepare_game_art.py; edit the generator instead.
package com.freetime.dachopoly

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class GameArtwork(val title: String, val resource: Int, val aspectRatio: Float)

object GameArt {
"""

UI = """
@Composable
internal fun ArtworkImage(art: GameArtwork, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(art.resource),
        contentDescription = art.title,
        modifier = modifier.aspectRatio(art.aspectRatio),
    )
}

@Composable
internal fun GameArtGallery(onDismiss: () -> Unit) {
    val categories = remember {
        listOf(
            "Chance" to GameArt.chance,
            "Kanzlei" to GameArt.kanzlei,
            "Grundstücke" to GameArt.properties,
            "Geld" to GameArt.money,
            "Symbole" to GameArt.symbols,
            "Mitte" to listOf(GameArt.center),
        )
    }
    var category by rememberSaveable { mutableIntStateOf(0) }
    // Category is an input: switching categories resets the index before rendering.
    var index by rememberSaveable(category) { mutableIntStateOf(0) }
    val artworks = categories[category].second
    val art = artworks[index]
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()
                    .heightIn(max = maxHeight - 24.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Original-Spielmaterial", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onDismiss) { Text("Schließen") }
                    }
                    Text(
                        "Die Originalbilder verwenden Freuken. Das Spiel rechnet derzeit vereinfacht in €.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEachIndexed { categoryIndex, (title, items) ->
                            FilterChip(
                                selected = category == categoryIndex,
                                onClick = { category = categoryIndex },
                                label = { Text("$title (${items.size})") },
                            )
                        }
                    }
                    Text(art.title, style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { index-- }, enabled = index > 0) { Text("Zurück") }
                        Text(
                            "${index + 1} / ${artworks.size}",
                            Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        TextButton(onClick = { index++ }, enabled = index < artworks.lastIndex) { Text("Weiter") }
                    }
                    // Keying disposes the previous painter; only one resource is decoded at a time.
                    key(art.resource) {
                        ArtworkImage(art, Modifier.fillMaxWidth())
                    }
                    Text("126 Originalbilder · offline verfügbar", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
"""

if __name__ == "__main__":
    main()
