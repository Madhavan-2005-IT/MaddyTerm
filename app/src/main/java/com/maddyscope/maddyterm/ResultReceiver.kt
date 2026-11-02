package com.maddyscope.maddyterm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val id = intent.getIntExtra(TermuxBridge.EXTRA_ID, -1)
            val b = intent.getBundleExtra("result")
            val res = if (b == null) {
                CmdResult(id, "", "", null, "No result bundle returned by Termux")
            } else {
                val errMsg = b.getString("errmsg")
                CmdResult(
                    id = id,
                    stdout = b.getString("stdout") ?: "",
                    stderr = b.getString("stderr") ?: "",
                    exitCode = if (b.containsKey("exitCode")) b.getInt("exitCode") else null,
                    error = if (errMsg.isNullOrBlank()) null else errMsg
                )
            }
            ResultBus.flow.tryEmit(res)
        } catch (_: Exception) {
            // Never crash from a result callback.
        }
    }
}
