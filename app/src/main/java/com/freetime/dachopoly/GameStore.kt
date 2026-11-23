package com.freetime.dachopoly

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, device-local save; no server or network is involved. */
class GameStore(context: Context) {
    private val preferences = context.getSharedPreferences("dachopoly", Context.MODE_PRIVATE)

    fun save(state: GameState) {
        val json = JSONObject().put("version", 2)
            .put("players", JSONArray().apply {
                state.players.forEach { p -> put(JSONObject().put("name", p.name).put("cash", p.cash)
                    .put("position", p.position).put("jailed", p.jailed).put("bankrupt", p.bankrupt)) }
            })
            .put("current", state.current).put("owners", JSONArray(state.owners))
            .put("houses", JSONArray(state.houses)).put("rolled", state.rolled)
            .put("dice", JSONArray(state.dice)).put("log", JSONArray(state.log.takeLast(100)))
        preferences.edit().putString("game", json.toString()).apply()
    }

    fun load(): GameState? = runCatching {
        val json = JSONObject(preferences.getString("game", null) ?: return null)
        // The photographed board has a different field order from the initial prototype.
                require(json.getInt("version") == 2)
        val players = json.getJSONArray("players")
        val state = GameState(
            players = List(players.length()) { i -> players.getJSONObject(i).let {
                Player(it.getString("name"), it.getInt("cash"), it.getInt("position"), it.getBoolean("jailed"), it.getBoolean("bankrupt"))
            } },
            current = json.getInt("current"),
            owners = json.getJSONArray("owners").ints(),
            houses = json.getJSONArray("houses").ints(),
            rolled = json.getBoolean("rolled"),
            dice = json.getJSONArray("dice").ints(),
            log = json.getJSONArray("log").let { a -> List(a.length()) { a.getString(it) } },
        )
        require(state.players.size in 2..4 && state.current in state.players.indices)
        require(state.players.all { it.position in GameRules.board.indices && it.cash >= 0 })
        require(state.owners.size == GameRules.board.size && state.owners.all { it in -1 until state.players.size })
        require(state.houses.size == GameRules.board.size && state.houses.all { it in 0..3 })
        require(state.dice.size == 2 && state.dice.all { it in 1..6 } && state.log.isNotEmpty())
        state
    }.getOrNull()

    fun clear() { preferences.edit().remove("game").apply() }
    private fun JSONArray.ints() = List(length()) { getInt(it) }
}
