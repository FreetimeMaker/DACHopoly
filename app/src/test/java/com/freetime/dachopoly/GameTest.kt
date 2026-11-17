package com.freetime.dachopoly

import org.junit.Assert.*
import org.junit.Test

class GameTest {
    private fun game(position: Int = 0, cash: Int = 1500): GameState =
        GameRules.newGame(3).let { state ->
            state.copy(players = state.players.mapIndexed { i, player ->
                if (i == 0) player.copy(position = position, cash = cash) else player
            })
        }

    @Test fun boardAndInitialState() {
        val state = GameRules.newGame(2)
        assertEquals(24, GameRules.board.size)
        assertEquals(listOf(TileKind.START, TileKind.JAIL, TileKind.PARK, TileKind.GO_TO_JAIL),
            listOf(0, 6, 12, 18).map { GameRules.board[it].kind })
        assertEquals(1500, state.active.cash)
        assertEquals(0, state.current)
        assertNull(state.winner)
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
        for (dice in listOf(0 to 1, 7 to 1, 1 to 0, 1 to 7)) {
            assertEquals(state, GameRules.roll(state, dice.first, dice.second))
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
        for (position in listOf(22, 23)) {
            val moved = GameRules.roll(game(position), 1, 1)
            assertEquals((position + 2) % 24, moved.active.position)
            assertEquals(1700, moved.active.cash)
        }
    }

    @Test fun purchaseRequiresRollUnownedPropertyAndEnoughCash() {
        val state = game(position = 2)
        assertEquals(state, GameRules.buy(state))
        val landed = GameRules.roll(game(), 1, 1)
        val bought = GameRules.buy(landed)
        assertEquals(0, bought.owners[2])
        assertEquals(1380, bought.active.cash)
        assertEquals(bought, GameRules.buy(bought))
        val poor = GameRules.roll(game(cash = 119), 1, 1)
        assertEquals(poor, GameRules.buy(poor))
        val park = GameRules.roll(game(10), 1, 1)
        assertEquals(park, GameRules.buy(park))
        val owned = landed.copy(owners = landed.owners.mapIndexed { i, owner -> if (i == 2) 1 else owner })
        assertEquals(owned, GameRules.buy(owned))
    }

    @Test fun jailSkipsExactlyOneTurnWithoutMovement() {
        val jailed = GameRules.roll(game(16), 1, 1)
        assertTrue(jailed.active.jailed)
        assertEquals(6, jailed.active.position)
        assertEquals(1500, jailed.active.cash)
        val skipped = GameRules.roll(jailed.copy(rolled = false), 6, 6)
        assertFalse(skipped.active.jailed)
        assertTrue(skipped.rolled)
        assertEquals(6, skipped.active.position)
        assertEquals(skipped, GameRules.roll(skipped, 1, 1))
        assertEquals(8, GameRules.roll(skipped.copy(rolled = false), 1, 1).active.position)
        val visiting = GameRules.roll(game(4), 1, 1)
        assertFalse(visiting.active.jailed)
    }

    @Test fun buildingRequiresWholeGroupAndStopsAtThree() {
        val state = game().copy(owners = List(24) { if (it == 1) 0 else -1 })
        assertEquals(state, GameRules.build(state, 1))
        val complete = state.copy(owners = List(24) { if (it in listOf(1, 2, 4)) 0 else -1 })
        assertEquals(20, GameRules.rent(complete, 1))
        var built = complete
        repeat(3) { built = GameRules.build(built, 1) }
        assertEquals(3, built.houses[1])
        assertEquals(1350, built.active.cash)
        assertEquals(100, GameRules.rent(built, 1))
        assertEquals(built, GameRules.build(built, 1))
        for (index in listOf(-1, 24, 0, 7)) assertEquals(complete, GameRules.build(complete, index))
        val poor = complete.copy(players = complete.players.map { it.copy(cash = 49) })
        assertEquals(poor, GameRules.build(poor, 1))
        val jailed = complete.copy(players = complete.players.map { it.copy(jailed = true) })
        assertEquals(jailed, GameRules.build(jailed, 1))
    }

    @Test fun rentTransfersCashButNotForOwnProperty() {
        val state = game().copy(owners = List(24) { if (it == 2) 1 else -1 })
        assertEquals(12, GameRules.rent(state, 2))
        val landed = GameRules.roll(state, 1, 1)
        assertEquals(1488, landed.active.cash)
        assertEquals(1512, landed.players[1].cash)
        val own = state.copy(owners = List(24) { if (it == 2) 0 else -1 })
        assertEquals(1500, GameRules.roll(own, 1, 1).active.cash)
        for (index in listOf(-1, 24, 0, 1)) assertEquals(0, GameRules.rent(state, index))
    }

    @Test fun bankruptcyTransfersAssetsAndOnlyAvailableCashToCreditor() {
        val state = game(cash = 5).copy(
            owners = List(24) { when (it) { 1 -> 0; 2 -> 1; else -> -1 } },
            houses = List(24) { if (it == 1) 2 else 0 },
        )
        val bankrupt = GameRules.roll(state, 1, 1)
        assertTrue(bankrupt.active.bankrupt)
        assertEquals(0, bankrupt.active.cash)
        assertEquals(1505, bankrupt.players[1].cash)
        assertEquals(1, bankrupt.owners[1])
        assertEquals(0, bankrupt.houses[1])
        assertEquals(bankrupt, GameRules.buy(bankrupt))
        assertEquals(bankrupt, GameRules.build(bankrupt, 1))
        assertEquals(1, GameRules.endTurn(bankrupt).current)
    }

    @Test fun taxBankruptcyReturnsAssetsToBankAndExactPaymentIsSolvent() {
        val state = game(position = 3, cash = 199).copy(
            owners = List(24) { if (it == 1) 0 else -1 },
            houses = List(24) { if (it == 1) 3 else 0 },
        )
        val bankrupt = GameRules.roll(state, 1, 1)
        assertTrue(bankrupt.active.bankrupt)
        assertEquals(-1, bankrupt.owners[1])
        assertEquals(0, bankrupt.houses[1])
        val exact = GameRules.roll(game(3, 200), 1, 1)
        assertEquals(0, exact.active.cash)
        assertFalse(exact.active.bankrupt)
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
        assertEquals(state, GameRules.build(state, 1))
        assertEquals(state, GameRules.endTurn(state))
        assertNull(state.copy(players = state.players.map { it.copy(bankrupt = true) }).winner)
    }
}
