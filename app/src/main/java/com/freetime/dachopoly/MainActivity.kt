package com.freetime.dachopoly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freetime.core.FreetimeCore
import com.freetime.design.AppTheme
import com.freetime.design.ThemeMode
import com.freetime.warn.FreetimeWarn
import com.freetime.warn.FreetimeWarnContent
import com.freetime.warn.rememberFreetimeWarnState

private val tokens = listOf(Color(0xFFB84437), Color(0xFF2875AB), Color(0xFF8862AF), Color(0xFFC08A20))
private val groups = listOf(Color(0xFF78887B), Color(0xFFBD7959), Color(0xFF68A5B7), Color(0xFFE1AF54), Color(0xFF889B65), Color(0xFFAF7DA0))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = GameStore(this)
        setContent {
            AppTheme(
                            themeMode = ThemeMode.AUTO_TIME,
                            dynamicColor = true,
                            floatingBottomNavigationGlassEnabled = false,
                        ) {
                            val warning = rememberFreetimeWarnState(
                                context = this@MainActivity,
                                appName = "DACHopoly",
                                versionCode = 1L,
                            )
                var game by remember { mutableStateOf(store.load()) }
                var count by rememberSaveable { mutableIntStateOf(2) }
                var rules by rememberSaveable { mutableStateOf(false) }
                var restart by rememberSaveable { mutableStateOf(false) }
                fun update(next: GameState) { store.save(next); game = next }
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("DACHopoly", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                                Text("GROSSE STÄDTE. GROSSE PLÄNE.", fontSize = 10.sp, letterSpacing = 2.sp)
                            }
                            TextButton(onClick = { rules = true }) { Text("Regeln") }
                        }
                        val state = game
                        if (state == null) {
                            Text("Ein Spielbrett. Eure nächste Reise.", style = MaterialTheme.typography.headlineMedium)
                            Text("Kauft Städte in Deutschland, Österreich und der Schweiz. Baut Häuser und bleibt als Letzte im Spiel.")
                            Card(
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Gemeinsam an einem Gerät", style = MaterialTheme.typography.titleLarge)
                                    Text("Wie viele spielen mit?")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        (2..4).forEach { n -> FilterChip(selected = count == n, onClick = { count = n }, label = { Text("$n Personen") }) }
                                    }
                                    Button(onClick = { update(GameRules.newGame(count)) }, modifier = Modifier.fillMaxWidth()) { Text("Spiel starten") }
                                    Text("100 % offline · Kein Konto · Automatisch gespeichert", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            state.players.forEachIndexed { i, p ->
                                Row(Modifier.fillMaxWidth().background(if (i == state.current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("● ${i + 1}", color = tokens[i], fontWeight = FontWeight.Bold)
                                    Text("  ${p.name}${if (p.jailed) " · im Gefängnis" else ""}", Modifier.weight(1f))
                                    Text(if (p.bankrupt) "Bankrott" else "${p.cash} €", fontWeight = FontWeight.Bold)
                                }
                            }
                            Board(state)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(state.winner?.let { "${it.name} gewinnt!" } ?: "${state.active.name} ist am Zug", style = MaterialTheme.typography.titleLarge)
                                    Text("Würfel: ${state.dice[0]} + ${state.dice[1]}   ·   ${GameRules.board[state.active.position].name}")
                                    if (state.winner == null) {
                                        Button(onClick = { update(if (state.rolled) GameRules.endTurn(state) else GameRules.roll(state)) }, modifier = Modifier.fillMaxWidth()) {
                                            Text(if (state.rolled) "Zug beenden · Gerät weitergeben" else if (state.active.jailed) "Gefängnisrunde aussetzen" else "Würfeln")
                                        }
                                        val tile = GameRules.board[state.active.position]
                                        if (state.rolled && !state.active.bankrupt && tile.kind == TileKind.PROPERTY && state.owners[state.active.position] == -1) {
                                            OutlinedButton(onClick = { update(GameRules.buy(state)) }, enabled = state.active.cash >= tile.price, modifier = Modifier.fillMaxWidth()) { Text("${tile.name} kaufen · ${tile.price} €") }
                                        }
                                    }
                                    Text(state.log.last(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Text("Deine Grundstücke", style = MaterialTheme.typography.titleLarge)
                            val owned = GameRules.board.indices.filter { state.owners[it] == state.current }
                            if (owned.isEmpty()) Text("Noch keine Städte. Lande auf einem freien Grundstück und kaufe es.")
                            owned.forEach { index ->
                                val tile = GameRules.board[index]
                                Card(Modifier.fillMaxWidth()) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tile.name, fontWeight = FontWeight.Bold)
                                            Text("${state.houses[index]} Häuser · Miete ${GameRules.rent(state, index)} €")
                                        }
                                        OutlinedButton(onClick = { update(GameRules.build(state, index)) }, enabled = GameRules.build(state, index) != state) { Text("+ Haus ${tile.price / 2} €") }
                                    }
                                }
                            }
                            Text("Spielverlauf", style = MaterialTheme.typography.titleLarge)
                            state.log.takeLast(8).reversed().forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { restart = true }) { Text("Neues Spiel") }
                        }
                        Text(
                            "Freetime Core ${FreetimeCore.SDK_VERSION} · Material 3 Expressive · Material You",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FreetimeWarn(
                    state = warning,
                    content = FreetimeWarnContent(
                        title = "Hinweis zur Android-Plattform",
                        message = { appName -> "Android-Plattformanforderungen können künftig beeinflussen, wie $appName auf manchen Geräten installiert oder weiter genutzt werden kann. Das Spiel selbst benötigt weder ein Konto noch eine Internetverbindung." },
                        acknowledgeLabel = "Verstanden",
                    ),
                )
                if (rules) AlertDialog(onDismissRequest = { rules = false }, title = { Text("So spielt ihr") }, text = {
                    Text("2–4 Personen spielen abwechselnd an diesem Gerät, ohne Computergegner. Alle starten mit 1.500 €.\n\nWürfle, kaufe freie Städte oder zahle automatisch Miete. Über Los gibt es 200 €. Ein vollständiges Farbset verdoppelt die Grundmiete und erlaubt bis zu drei Häuser pro Stadt. Jedes Haus kostet den halben Kaufpreis und erhöht die Miete. Tippe ein Brettfeld an, um es anzusehen.\n\nSteuern kosten 200 €. Ereignisse bringen oder kosten Geld. Im Gefängnis setzt du eine Runde aus. Ein Pasch gibt keinen Extrazug.\n\nWer eine Zahlung nicht leisten kann, scheidet aus; Grundstücke gehen an den Gläubiger oder die Bank, Häuser verfallen. Die letzte verbleibende Person gewinnt. Kein Handel, keine Auktionen oder Hypotheken.\n\nDer Spielstand wird nach jeder Aktion auf diesem Gerät gespeichert.")
                }, confirmButton = { TextButton(onClick = { rules = false }) { Text("Los geht’s") } })
                if (restart) AlertDialog(onDismissRequest = { restart = false }, title = { Text("Neues Spiel beginnen?") }, text = { Text("Der aktuelle Spielstand wird gelöscht.") }, confirmButton = { TextButton(onClick = { store.clear(); game = null; restart = false }) { Text("Neues Spiel") } }, dismissButton = { TextButton(onClick = { restart = false }) { Text("Abbrechen") } })
            }
        }
    }
}

@Composable
private fun Board(state: GameState) {
    var selected by remember { mutableStateOf<Int?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraLarge)) {
        val cell = maxWidth / 7
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("DACH", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("DEUTSCHLAND · ÖSTERREICH", fontSize = 9.sp)
            Text("SCHWEIZ", fontSize = 9.sp)
            Spacer(Modifier.height(12.dp))
            Text("${state.dice[0]}  •  ${state.dice[1]}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Felder antippen für Details", fontSize = 9.sp)
        }
        GameRules.board.forEachIndexed { i, tile ->
            val (x, y) = when {
                i <= 6 -> (6 - i) to 6
                i <= 12 -> 0 to (12 - i)
                i <= 18 -> (i - 12) to 0
                else -> 6 to (i - 18)
            }
            Column(Modifier.offset(cell * x, cell * y).size(cell).padding(1.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)).clickable { selected = i }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(groups[tile.group]))
                Text(tile.name, Modifier.weight(1f).padding(horizontal = 1.dp), fontSize = 8.sp, lineHeight = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
                Row {
                    state.players.forEachIndexed { p, player -> if (!player.bankrupt && player.position == i) Text("${p + 1}", color = tokens[p], fontWeight = FontWeight.Black, fontSize = 11.sp) }
                }
                if (state.owners[i] >= 0) Box(Modifier.fillMaxWidth().height(3.dp).background(tokens[state.owners[i]]))
            }
        }
    }
    selected?.let { i ->
        val tile = GameRules.board[i]
        AlertDialog(onDismissRequest = { selected = null }, title = { Text(tile.name) }, text = {
            Text(if (tile.kind == TileKind.PROPERTY) "Kaufpreis: ${tile.price} €\nBesitzer: ${state.players.getOrNull(state.owners[i])?.name ?: "Bank"}\nHäuser: ${state.houses[i]}\nAktuelle Miete: ${GameRules.rent(state, i)} €\nHauspreis: ${tile.price / 2} €" else when (tile.kind) {
                TileKind.START -> "Beim Überqueren oder Erreichen erhältst du 200 €."
                TileKind.EVENT -> "Eine zufällige Karte bringt oder kostet Geld."
                TileKind.TAX -> "Zahle 200 € an die Bank."
                TileKind.JAIL -> "Nur zu Besuch – außer du wurdest hierher geschickt."
                TileKind.GO_TO_JAIL -> "Gehe direkt ins Gefängnis und setze nächste Runde aus."
                else -> "Verschnaufpause. Hier passiert nichts."
            })
        }, confirmButton = { TextButton(onClick = { selected = null }) { Text("Schließen") } })
    }
}
