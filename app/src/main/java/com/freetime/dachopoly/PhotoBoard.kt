package com.freetime.dachopoly

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Player colours are game identities, not application theme colours.
internal val playerColors = listOf(Color(0xFFB84437), Color(0xFF2875AB), Color(0xFF8862AF), Color(0xFFC08A20))

private data class FieldBounds(val x: Float, val y: Float, val width: Float, val height: Float)

/** Measured on the rectified photograph: the handmade board has unequal sides. */
private fun fieldBounds(index: Int): FieldBounds {
    val bottom = listOf(0f, .10f, .15f, .245f, .31f, .41f, .52f, .605f, .71f, .82f, .90f, 1f)
    val left = listOf(0f, .13f, .19f, .31f, .40f, .54f, .65f, .79f, .88f, 1f)
    val top = listOf(0f, .16f, .29f, .35f, .42f, .51f, .70f, .81f, .90f, 1f)
    val right = listOf(0f, .16f, .29f, .43f, .53f, .59f, .69f, .77f, .88f, 1f)
    return when {
        index <= 10 -> { val n = 10 - index; FieldBounds(bottom[n], .88f, bottom[n + 1] - bottom[n], .12f) }
        index < 18 -> { val n = 18 - index; FieldBounds(0f, left[n], .16f, left[n + 1] - left[n]) }
        index <= 26 -> { val n = index - 18; FieldBounds(top[n], 0f, top[n + 1] - top[n], .16f) }
        else -> { val n = index - 26; FieldBounds(.89f, right[n], .11f, right[n + 1] - right[n]) }
    }
}

@Composable
internal fun PhotoBoard(state: GameState) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var enlarged by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Euer DACH-Spielbrett", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            FilterChip(selected = enlarged, onClick = { enlarged = !enlarged }, label = { Text("Vergrößern") })
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val boardSize = if (enlarged) maxOf(maxWidth, 720.dp) else maxWidth
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                Box(Modifier.size(boardSize)) {
                    Image(painterResource(R.drawable.board_reference), contentDescription = "Originales DACH-Spielbrett mit Deutschland-, Österreich- und Schweiz-Motiv sowie Chance und Kanzlei", modifier = Modifier.fillMaxSize())
                    GameRules.board.forEachIndexed { index, tile ->
                        val bounds = fieldBounds(index)
                        val occupants = state.players.indices.filter { !state.players[it].bankrupt && state.players[it].position == index }
                        val owner = state.owners[index]
                        val active = state.active.position == index
                        Box(
                            Modifier.offset(boardSize * bounds.x, boardSize * bounds.y)
                                .size(boardSize * bounds.width, boardSize * bounds.height)
                                .then(if (active) Modifier.border(3.dp, MaterialTheme.colorScheme.primary) else Modifier)
                                .clickable { selected = index }
                                .semantics { contentDescription = "Feld ${index + 1}: ${tile.name}${if (owner >= 0) ", Besitzer ${state.players[owner].name}" else ""}" },
                        ) {
                            if (owner >= 0) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp).background(playerColors[owner]))
                            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                                occupants.chunked(2).forEach { row ->
                                    Row {
                                        row.forEach { player ->
                                            Box(Modifier.padding(1.dp).size(if (enlarged) 27.dp else 18.dp).background(playerColors[player], CircleShape).border(1.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                                Text("${player + 1}", color = Color.White, fontSize = if (enlarged) 14.sp else 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                if (state.houses[index] > 0) Text("⌂".repeat(state.houses[index]), color = Color.White, modifier = Modifier.background(Color(0xFF245039)), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("Foto-Vorlage · Feld antippen für Details${if (enlarged) " · Seitlich wischen" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    selected?.let { index ->
        val tile = GameRules.board[index]
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(tile.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Feld ${index + 1} von ${GameRules.board.size}", style = MaterialTheme.typography.labelLarge)
                    Text(if (tile.kind == TileKind.PROPERTY) {
                        "Kaufpreis: ${tile.price} €\nBesitzer: ${state.players.getOrNull(state.owners[index])?.name ?: "Bank"}\nMiete: ${GameRules.rent(state, index)} €" +
                            if (tile.canBuild) "\nHäuser: ${state.houses[index]}\nHauspreis: ${tile.price / 2} €" else "\nBahn, Fluglinie oder Versorgung: kein Häuserbau."
                    } else when (tile.kind) {
                        TileKind.START -> "Beim Erreichen oder Überqueren von GO erhältst du 200 €."
                        TileKind.EVENT -> "${tile.name}: Eine lokale Zufallskarte bringt oder kostet Geld. In dieser Partie gelten vereinfachte Ereignisse statt der aufgedruckten Originaleffekte."
                        TileKind.TAX -> "Zahle 200 € an die Bank."
                        TileKind.JAIL -> "Nur zu Besuch, außer du wurdest hierher geschickt. Dann setzt du eine Runde aus."
                        TileKind.GO_TO_JAIL -> "Gehe direkt ins Gefängnis und setze nächste Runde aus."
                        else -> "Frei Parken: Hier wird nichts bezahlt."
                    })
                    val artwork = GameArt.property(index) ?: when (tile.kind) {
                        TileKind.START -> GameArt.symbols[1]
                        TileKind.EVENT -> if (tile.name == "Chance") GameArt.chance.first() else GameArt.kanzlei.first()
                        TileKind.TAX -> GameArt.symbols[if (tile.name == "Zusatzsteuer") 6 else 7]
                        TileKind.JAIL -> GameArt.symbols[2]
                        TileKind.GO_TO_JAIL -> GameArt.symbols[3]
                        TileKind.PARK -> GameArt.symbols[4]
                        TileKind.PROPERTY -> null
                    }
                    artwork?.let { ArtworkImage(it, Modifier.fillMaxWidth().heightIn(max = 360.dp)) }
                    Text("Die Abbildung zeigt das Originalmaterial. Für diese Partie gelten die oben angezeigten Spielregeln und Euro-Beträge.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Schließen") } },
        )
    }
}
