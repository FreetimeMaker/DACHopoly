package com.freetime.dachopoly

import kotlin.random.Random

enum class TileKind { START, PROPERTY, EVENT, TAX, JAIL, GO_TO_JAIL, PARK }

data class Tile(val name: String, val kind: TileKind, val price: Int = 0, val group: Int = 0) {
    val canBuild: Boolean get() = kind == TileKind.PROPERTY && group in 1..8
}

data class Player(
    val name: String,
    val cash: Int = 1500,
    val position: Int = 0,
    val jailed: Boolean = false,
    val bankrupt: Boolean = false,
)

data class GameState(
    val players: List<Player>,
    val current: Int = 0,
    val owners: List<Int> = List(GameRules.board.size) { -1 },
    val houses: List<Int> = List(GameRules.board.size) { 0 },
    val rolled: Boolean = false,
    val dice: List<Int> = listOf(1, 1),
    val log: List<String> = listOf("Willkommen bei DACHopoly!"),
) {
    val winner: Player? get() = players.singleOrNull { !it.bankrupt }
    val active: Player get() = players[current]
}

object GameRules {
    // Rectified photo, from bottom-right GO: left, up, right, down.
    // Unequal photographed edges: bottom 10 steps, left 8, top 8, right 8 (34 tiles).
    // The original photo has no readable prices; these are gameplay approximations.
    val board: List<Tile> = listOf(
        Tile("Los", TileKind.START),
        Tile("Bergwasserfall", TileKind.PROPERTY, 380, 1),
        Tile("Kanzlei", TileKind.EVENT),
        Tile("Verwaltungsgebäude", TileKind.PROPERTY, 360, 1),
        Tile("Einkommensteuer", TileKind.TAX), // Diamond symbol on the photo.
        Tile("ÖBB", TileKind.PROPERTY, 200, 9),
        Tile("Brandenburger Tor", TileKind.PROPERTY, 340, 2),
        Tile("Chance", TileKind.EVENT),
        Tile("Dom", TileKind.PROPERTY, 180, 2),
        Tile("Altstadt am Fluss", TileKind.PROPERTY, 320, 3),
        Tile("Gefängnis", TileKind.JAIL),
        Tile("Marktplatz", TileKind.PROPERTY, 300, 3),
        Tile("Energieversorgung Mittelrhein", TileKind.PROPERTY, 150, 10),
        Tile("Alpenpass", TileKind.PROPERTY, 280, 4),
        Tile("DB", TileKind.PROPERTY, 200, 9),
        Tile("Stadthaus", TileKind.PROPERTY, 260, 4),
        Tile("Kanzlei", TileKind.EVENT),
        Tile("Alpenzoo", TileKind.PROPERTY, 240, 5),
        Tile("Frei Parken", TileKind.PARK),
        Tile("Stephansdom", TileKind.PROPERTY, 220, 5),
        Tile("Wasserfall", TileKind.PROPERTY, 200, 6),
        Tile("Chance", TileKind.EVENT),
        Tile("SBB", TileKind.PROPERTY, 200, 9),
        Tile("Schloss", TileKind.PROPERTY, 180, 6),
        Tile("Regionalwerke Baden", TileKind.PROPERTY, 150, 10),
        Tile("Bergsee", TileKind.PROPERTY, 160, 7),
        Tile("Gehe ins Gefängnis", TileKind.GO_TO_JAIL),
        Tile("Rheinfall", TileKind.PROPERTY, 140, 7),
        Tile("Kanzlei", TileKind.EVENT),
        Tile("Lufthansa", TileKind.PROPERTY, 200, 9),
        Tile("Chance", TileKind.EVENT),
        Tile("Europa-Park", TileKind.PROPERTY, 120, 8),
        Tile("Zusatzsteuer", TileKind.TAX), // Ring symbol on the photo.
        Tile("Altstadt", TileKind.PROPERTY, 100, 8),
    )

    fun newGame(count: Int): GameState {
        require(count in 2..6) { "A game needs 2–6 players" }
        return GameState(List(count) { Player("Spieler ${it + 1}") })
    }

    private fun playable(state: GameState): Boolean =
        state.players.size >= 2 && state.current in state.players.indices &&
            state.owners.size == board.size && state.houses.size == board.size &&
            state.players.count { !it.bankrupt } > 1 && !state.active.bankrupt &&
            state.active.position in board.indices

    fun roll(
        state: GameState,
        first: Int = Random.nextInt(1, 7),
        second: Int = Random.nextInt(1, 7),
    ): GameState {
        if (!playable(state) || state.rolled || first !in 1..6 || second !in 1..6) return state
        val player = state.active
        var next = state.copy(rolled = true, dice = listOf(first, second))
        if (player.jailed) {
            return next.withPlayer(state.current, player.copy(jailed = false))
                .message("${player.name} setzt im Gefängnis aus.")
        }
        val destination = player.position + first + second
        val moved = player.copy(
            position = destination % board.size,
            cash = player.cash + if (destination >= board.size) 200 else 0,
        )
        next = next.withPlayer(state.current, moved)
            .message("${player.name} würfelt $first und $second: ${board[moved.position].name}.")
        return when (board[moved.position].kind) {
            TileKind.GO_TO_JAIL -> next.withPlayer(
                state.current,
                moved.copy(position = board.indexOfFirst { it.kind == TileKind.JAIL }, jailed = true),
            ).message("${player.name} muss ins Gefängnis.")
            TileKind.TAX -> charge(next, 200)
            TileKind.EVENT -> {
                val amount = listOf(-150, -100, 50, 100, 200).random()
                if (amount < 0) charge(next.message("${board[moved.position].name}: $amount €."), -amount)
                else next.withPlayer(state.current, moved.copy(cash = moved.cash + amount))
                    .message("${board[moved.position].name}: +$amount €.")
            }
            TileKind.PROPERTY -> {
                val owner = next.owners[moved.position]
                if (owner in next.players.indices && owner != state.current && !next.players[owner].bankrupt) {
                    charge(next, rent(next, moved.position), owner)
                } else next
            }
            else -> next
        }
    }

    fun buy(state: GameState): GameState {
        if (!playable(state) || !state.rolled || state.active.jailed) return state
        val index = state.active.position
        val tile = board[index]
        if (tile.kind != TileKind.PROPERTY || state.owners[index] != -1 || state.active.cash < tile.price) return state
        return state.withPlayer(state.current, state.active.copy(cash = state.active.cash - tile.price))
            .copy(owners = state.owners.mapIndexed { i, owner -> if (i == index) state.current else owner })
            .message("${state.active.name} kauft ${tile.name}.")
    }

    fun build(state: GameState, index: Int): GameState {
        if (!playable(state) || state.active.jailed || index !in board.indices) return state
        val tile = board[index]
        if (!tile.canBuild || state.owners[index] != state.current ||
            state.houses[index] !in 0..2 || state.active.cash < tile.price / 2 ||
            board.indices.any { board[it].kind == TileKind.PROPERTY && board[it].group == tile.group && state.owners[it] != state.current }
        ) return state
        return state.withPlayer(state.current, state.active.copy(cash = state.active.cash - tile.price / 2))
            .copy(houses = state.houses.mapIndexed { i, houses -> if (i == index) houses + 1 else houses })
            .message("${state.active.name} baut in ${tile.name}.")
    }

    fun endTurn(state: GameState): GameState {
        // Bankruptcy can happen during a roll, so a bankrupt current player may still end their turn.
        if (!state.rolled || state.current !in state.players.indices ||
            state.players.count { !it.bankrupt } <= 1
        ) return state
        val next = (1..state.players.size).map { (state.current + it) % state.players.size }
            .first { !state.players[it].bankrupt }
        return state.copy(current = next, rolled = false).message("${state.players[next].name} ist am Zug.")
    }

    fun rent(state: GameState, index: Int): Int {
        if (index !in board.indices || state.owners.size != board.size || state.houses.size != board.size) return 0
        val tile = board[index]
        val owner = state.owners[index]
        if (tile.kind != TileKind.PROPERTY || owner !in state.players.indices || state.players[owner].bankrupt) return 0
        val complete = board.indices.filter { board[it].kind == TileKind.PROPERTY && board[it].group == tile.group }
            .all { state.owners[it] == owner }
        val houses = if (tile.canBuild) state.houses[index].coerceIn(0, 3) else 0
        return (tile.price / 10) * if (houses > 0) 1 + houses * 3 else if (complete) 2 else 1
    }

    private fun charge(state: GameState, amount: Int, creditor: Int = -1): GameState {
        val player = state.active
        val paid = minOf(player.cash.coerceAtLeast(0), amount)
        val bankrupt = player.cash < amount
        var next = state.withPlayer(state.current, player.copy(cash = player.cash.coerceAtLeast(0) - paid, bankrupt = bankrupt))
        if (creditor in state.players.indices) {
            next = next.withPlayer(creditor, next.players[creditor].copy(cash = next.players[creditor].cash + paid))
        }
        if (bankrupt) {
            next = next.copy(
                owners = next.owners.map { if (it == state.current) creditor else it },
                houses = next.houses.mapIndexed { index, houses -> if (state.owners[index] == state.current) 0 else houses },
            ).message("${player.name} ist bankrott.")
        }
        return next.message("${player.name} zahlt $paid €.")
    }

    private fun GameState.withPlayer(index: Int, player: Player): GameState =
        copy(players = players.mapIndexed { i, old -> if (i == index) player else old })

    private fun GameState.message(text: String): GameState = copy(log = log + text)
}
