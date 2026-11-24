package com.zirakusa.redes

import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.KeyValues
import com.zirakusa.redes.data.LatencyLog
import com.zirakusa.redes.data.LatencyMeter
import com.zirakusa.redes.data.Roaming
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Settings
import com.zirakusa.redes.data.SignalLog

/** The app's one speed-test runner, shared by the screen and the MCP tools (so only one test runs at a time). */
object Tests {
    @Volatile private var made: Runner? = null
    fun runner(values: KeyValues): Runner = made ?: synchronized(this) { made ?: Runner(History(values), Cloudflare, Settings(values)).also { made = it } }
}

/**
 * The readings of «Señal» shared by the screen and the MCP tools: the signal and latency (only in memory, while Redes
 * runs) and the one roaming watcher (its log in the phone's preferences).
 */
object Live {
    val signal = SignalLog()
    val latency = LatencyMeter(Local, LatencyLog())
    @Volatile private var roam: Roaming? = null
    fun roaming(values: KeyValues): Roaming = roam ?: synchronized(this) { roam ?: Roaming(values).also { roam = it } }
}
