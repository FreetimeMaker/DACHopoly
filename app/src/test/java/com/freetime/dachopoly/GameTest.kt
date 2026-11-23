package com.freetime.dachopoly

import org.junit.Assert.*
import org.junit.Test

class GameTest {
    private val board get() = GameRules.board
    private val size get() = board.size
    private fun index(name: String) = board.indexOfFirst { it.name == name }.also { require(it >= 0) }
    private fun game(position: Int = 0, cash: Int = 1500): GameState =
        GameRules.newGame(3).let { state ->
            state.copy(players = state.players.mapIndexed { i, p ->
                if (i == 0) p.copy(position = position, cash = cash) else p
            })
        }
    private fun land(name: String, cash: Int = 1500) =
        GameRules.roll(game((index(name) - 2 + size) % size, cash), 1, 1)

    @Test fun boardAndInitialState() {
        assertEquals(34, size)
        val corners = listOf(0, 10, 18, 26)
        assertEquals(listOf(TileKind.START, TileKind.JAIL, TileKind.PARK, TileKind.GO_TO_JAIL),
            corners.map { board[it].kind })
        assertEquals(listOf(10, 8, 8, 8),
            (corners + size).zipWithNext { start, end -> end - start })
        assertEquals(listOf("Los", "Bergwasserfall", "Kanzlei", "Verwaltungsgebäude",
            "Einkommensteuer", "ÖBB", "Brandenburger Tor", "Chance", "Dom", "Altstadt am Fluss",
            "Gefängnis", "Marktplatz", "Energieversorgung Mittelrhein", "Alpenpass", "DB",
            "Stadthaus", "Kanzlei", "Alpenzoo", "Frei Parken", "Stephansdom", "Wasserfall",
            "Chance", "SBB", "Schloss", "Regionalwerke Baden", "Bergsee", "Gehe ins Gefängnis",
            "Rheinfall", "Kanzlei", "Lufthansa", "Chance", "Europa-Park", "Zusatzsteuer",
            "Altstadt"), board.map { it.name })
        assertEquals(listOf(0, 1, 0, 1, 0, 9, 2, 0, 2, 3, 0, 3, 10, 4, 9, 4, 0,
            5, 0, 5, 6, 0, 9, 6, 10, 7, 0, 7, 0, 9, 0, 8, 0, 8), board.map { it.group })
        val events = setOf(2, 7, 16, 21, 28, 30)
        val taxes = setOf(4, 32)
        board.indices.filter { it !in corners }.forEach { i ->
            assertEquals(when (i) {
                in events -> TileKind.EVENT
                in taxes -> TileKind.TAX
                else -> TileKind.PROPERTY
            }, board[i].kind)
        }
        val state = GameRules.newGame(2)
        assertEquals(1500, state.active.cash)
        assertEquals(0, state.current)
        assertNull(state.winner)
        assertEquals(size, state.owners.size)
        assertEquals(size, state.houses.size)
        assertTrue(state.owners.all { it == -1 })
        assertTrue(state.houses.all { it == 0 })
        for (count in listOf(-1, 0, 1, 7)) {
            try {
                GameRules.newGame(count)
                fail("Invalid player count accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rollingAndTurnGuards() {
        val state = game()
        assertEquals(state, GameRules.endTurn(state))
        for ((a, b) in listOf(0 to 1, 7 to 1, 1 to 0, 1 to 7)) {
            assertEquals(state, GameRules.roll(state, a, b))
        }
        val rolled = GameRules.roll(state, 1, 1)
        assertEquals(2, rolled.active.position)
        assertEquals(listOf(1, 1), rolled.dice)
        assertTrue(rolled.rolled)
        assertEquals(rolled, GameRules.roll(rolled, 2, 2))
        val ended = GameRules.endTurn(rolled)
        assertEquals(1, ended.current)
        assertFalse(ended.rolled)
    }

    @Test fun passingAndLandingOnStartPay200() {
        for (position in listOf(size - 2, size - 1)) {
            val moved = GameRules.roll(game(position), 1, 1)
            assertEquals((position + 2) % size, moved.active.position)
            assertEquals(1700, moved.active.cash)
        }
    }

    @Test fun purchaseRequiresRollUnownedPropertyAndEnoughCash() {
        val property = index("Europa-Park")
        val price = board[property].price
        val state = game(property)
        assertEquals(state, GameRules.buy(state))
        val landed = land("Europa-Park")
        val bought = GameRules.buy(landed)
        assertEquals(0, bought.owners[property])
        assertEquals(1500 - price, bought.active.cash)
        assertEquals(bought, GameRules.buy(bought))
        val poor = land("Europa-Park", price - 1)
        assertEquals(poor, GameRules.buy(poor))
        val park = land("Frei Parken")
        assertEquals(park, GameRules.buy(park))
        val owned = landed.copy(owners = List(size) { if (it == property) 1 else -1 })
        assertEquals(owned, GameRules.buy(owned))
    }

    @Test fun jailSkipsExactlyOneTurnWithoutMovement() {
        val jail = index("Gefängnis")
        val jailed = land("Gehe ins Gefängnis")
        assertTrue(jailed.active.jailed)
        assertEquals(jail, jailed.active.position)
        assertEquals(1500, jailed.active.cash)
        val skipped = GameRules.roll(jailed.copy(rolled = false), 6, 6)
        assertFalse(skipped.active.jailed)
        assertTrue(skipped.rolled)
        assertEquals(jail, skipped.active.position)
        assertEquals(skipped, GameRules.roll(sk
ipped, 1, 1))
        assertEquals(jail + 2, GameRules.roll(skipped.copy(rolled = false), 1, 1).active.position)
        assertFalse(land("Gefängnis").active.jailed)
    }

    @Test fun buildingRequiresWholeGroupAndStopsAtThree() {
        val property = index("Rheinfall")
        val tile = board[property]
        val state = game().copy(owners = List(size) { if (it == property) 0 else -1 })
        assertEquals(state, GameRules.build(state, property))
        val complete = state.copy(owners = List(size) { if (board[it].group == tile.group) 0 else -1 })
        assertEquals(tile.price / 10 * 2, GameRules.rent(complete, property))
        var built = complete
        repeat(3) { built = GameRules.build(built, property) }
        assertEquals(3, built.houses[property])
        assertEquals(1500 - 3 * (tile.price / 2), built.active.cash)
        assertEquals(tile.price / 10 * 10, GameRules.rent(built, property))
        assertEquals(built, GameRules.build(built, property))
        for (i in listOf(-1, size, 0, index("Schloss"))) assertEquals(complete, GameRules.build(complete, i))
        val poor = complete.copy(players = complete.players.map { it.copy(cash = tile.price / 2 - 1) })
        assertEquals(poor, GameRules.build(poor, property))
        val jailed = complete.copy(players = complete.players.map { it.copy(jailed = true) })
        assertEquals(jailed, GameRules.build(jailed, property))
    }

    @Test fun transportsAndUtilitiesArePurchasableButNotBuildable() {
        val owned = game().copy(owners = List(size) { 0 })
        board.forEachIndexed { i, tile ->
            assertEquals(tile.kind == TileKind.PROPERTY && tile.group in 1..8, tile.canBuild)
            if (tile.group in 9..10) {
                assertEquals(0, GameRules.buy(land(tile.name)).owners[i])
                assertEquals(owned, GameRules.build(owned, i))
                assertEquals(GameRules.rent(owned, i),
                    GameRules.rent(owned.copy(houses = List(size) { 3 }), i))
            }
        }
    }

    @Test fun eventTextUsesTheLandedDeckName() {
        board.forEachIndexed { i, tile ->
            if (tile.kind == TileKind.EVENT) {
                val landed = GameRules.roll(game(i - 2), 1, 1)
                assertTrue(landed.log.any { it.startsWith(tile.name + ": ") })
            }
        }
    }

    @Test fun rentTransfersCashButNotForOwnProperty() {
        val property = index("Europa-Park")
        val state = game(property - 2).copy(owners = List(size) { if (it == property) 1 else -1 })
        val rent = board[property].price / 10
        assertEquals(rent, GameRules.rent(state, property))
        val landed = GameRules.roll(state, 1, 1)
        assertEquals(1500 - rent, landed.active.cash)
        assertEquals(1500 + rent, landed.players[1].cash)
        val own = state.copy(owners = List(size) { if (it == property) 0 else -1 })
        assertEquals(1500, GameRules.roll(own, 1, 1).active.cash)
        for (i in listOf(-1, size, 0, index("Altstadt"))) assertEquals(0, GameRules.rent(state, i))
    }

    @Test fun bankruptcyTransfersAssetsAndOnlyAvailableCashToCreditor() {
        val property = index("Europa-Park")
        val asset = index("Altstadt")
        val state = game(property - 2, 5).copy(
            owners = List(size) { when (it) { asset -> 0; property -> 1; else -> -1 } },
            houses = List(size) { if (it == asset) 2 else 0 },
        )
        val bankrupt = GameRules.roll(state, 1, 1)
        assertTrue(bankrupt.active.bankrupt)
        assertEquals(0, bankrupt.active.cash)
        assertEquals(1505, bankrupt.players[1].cash)
        assertEquals(1, bankrupt.owners[asset])
        assertEquals(0, bankrupt.houses[asset])
        assertEquals(bankrupt, GameRules.buy(bankrupt))
        assertEquals(bankrupt, GameRules.build(bankrupt, asset))
        assertEquals(1, GameRules.endTurn(bankrupt).current)
    }

    @Test fun taxBankruptcyReturnsAssetsToBankAndExactPaymentIsSolvent() {
        val asset = index("Altstadt")
        for (tax in board.indices.filter { board[it].kind == TileKind.TAX }) {
            val state = game(tax - 2, 199).copy(
                owners = List(size) { if (it == asset) 0 else -1 },
                houses = List(size) { if (it == asset) 3 else 0 },
            )
            val bankrupt = GameRules.roll(state, 1, 1)
            assertTrue(bankrupt.active.bankrupt)
            assertEquals(-1, bankrupt.owners[asset])
            assertEquals(0, bankrupt.houses[asset])
            val exact = GameRules.roll(game(tax - 2, 200), 1, 1)
            assertEquals(0, exact.active.cash)
            assertFalse(exact.active.bankrupt)
        }
    }

    @Test fun endTurnSkipsBankruptPlayersAndWraps() {
        val state = game().copy(rolled = true).let { it.copy(players = listOf(
            it.players[0], it.players[1].copy(bankrupt = true), it.players[2],
        )) }
        val next = GameRules.endTurn(state)
        assertEquals(2, next.current)
        assertEquals(0, GameRules.endTurn(next.copy(rolled = true)).current)
    }

    @Test fun winnerStopsEveryActionAndNoSolventPlayerIsNotAWinner() {
        val state = game().let { it.copy(rolled = true, players = it.players.mapIndexed { i, p ->
            p.copy(bankrupt = i != 0)
        }) }
        assertEquals(state.active, state.winner)
        assertEquals(state, GameRules.roll(state, 1, 1))
        assertEquals(state, GameRules.buy(state))
        assertEquals(state, GameRules.build(state, index("Altstadt")))
        assertEquals(state, GameRules.endTurn(state))
        assertNull(state.copy(players = state.players.map { it.copy(bankrupt = true) }).winner)
    }
}
