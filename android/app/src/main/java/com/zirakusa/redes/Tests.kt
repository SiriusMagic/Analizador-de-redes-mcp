package com.zirakusa.redes

import com.zirakusa.redes.data.History
import com.zirakusa.redes.data.KeyValues
import com.zirakusa.redes.data.Runner
import com.zirakusa.redes.data.Settings

/** The app's one speed-test runner, shared by the screen and the MCP tools (so only one test runs at a time). */
object Tests {
    @Volatile private var made: Runner? = null
    fun runner(values: KeyValues): Runner = made ?: synchronized(this) { made ?: Runner(History(values), Cloudflare, Settings(values)).also { made = it } }
}
