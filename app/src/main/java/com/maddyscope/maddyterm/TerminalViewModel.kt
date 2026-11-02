package com.maddyscope.maddyterm

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

enum class Kind { INFO, CMD, OUT, ERR }
data class Line(val text: String, val kind: Kind)

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    val lines = mutableStateListOf<Line>()
    var cwd by mutableStateOf(TermuxBridge.HOME); private set
    var pending by mutableIntStateOf(0); private set

    private val history = mutableListOf<String>()
    private var hIdx = 0
    private var nextId = 1
    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")

    init {
        add("MaddyTerm — front-end for Termux. Type a command and press Send.", Kind.INFO)
        add("Interactive programs (vim, nano, htop): use the Termux button.", Kind.INFO)
        viewModelScope.launch {
            ResultBus.flow.collect { r ->
                try { onResult(r) } catch (e: Exception) { add("Internal error: ${e.message}", Kind.ERR) }
            }
        }
    }

    fun promptDir(): String =
        if (cwd.startsWith(TermuxBridge.HOME)) "~" + cwd.removePrefix(TermuxBridge.HOME) else cwd

    private fun add(text: String, kind: Kind) {
        lines.add(Line(text, kind))
        if (lines.size > 3000) lines.removeRange(0, lines.size - 2500)
    }

    fun submit(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return
        history.add(cmd); hIdx = history.size
        add("${promptDir()} $ $cmd", Kind.CMD)
        if (cmd == "clear") { lines.clear(); pending = 0; return }
        val id = nextId++
        val err = TermuxBridge.run(getApplication(), id, cmd, cwd)
        if (err != null) add(err, Kind.ERR) else pending++
    }

    fun openInTermux(raw: String) {
        val cmd = raw.trim().ifEmpty { "bash -l" }
        add("↗ Termux session: $cmd", Kind.INFO)
        TermuxBridge.runInTermuxSession(getApplication(), cmd)?.let { add(it, Kind.ERR) }
    }

    fun clear() { lines.clear(); pending = 0 }

    fun historyPrev(): String? {
        if (history.isEmpty()) return null
        hIdx = (hIdx - 1).coerceAtLeast(0)
        return history[hIdx]
    }

    fun historyNext(): String {
        if (hIdx < history.size - 1) { hIdx++; return history[hIdx] }
        hIdx = history.size
        return ""
    }

    private fun onResult(r: CmdResult) {
        pending = (pending - 1).coerceAtLeast(0)
        if (r.error != null) {
            add(r.error, Kind.ERR)
            return
        }
        var out = r.stdout
        val idx = out.lastIndexOf(TermuxBridge.MARK)
        if (idx >= 0) {
            val newCwd = out.substring(idx + TermuxBridge.MARK.length).trim()
            if (newCwd.startsWith("/")) cwd = newCwd
            out = out.substring(0, idx)
        }
        emitText(out, Kind.OUT)
        emitText(r.stderr, Kind.ERR)
        val code = r.exitCode
        if (code != null && code != 0) add("[exit $code]", Kind.INFO)
    }

    private fun emitText(text: String, kind: Kind) {
        if (text.isBlank()) return
        ansi.replace(text, "").replace("\r", "").trimEnd('\n').split('\n')
            .takeLast(2000).forEach { add(it, kind) }
    }
}
