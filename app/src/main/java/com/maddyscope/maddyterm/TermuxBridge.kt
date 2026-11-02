package com.maddyscope.maddyterm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow

data class CmdResult(
    val id: Int,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val error: String?
)

/** Process-wide channel from the BroadcastReceiver to the ViewModel. */
object ResultBus {
    val flow = MutableSharedFlow<CmdResult>(extraBufferCapacity = 64)
}

/** Talks to Termux through its official RUN_COMMAND intent API. */
object TermuxBridge {
    const val PKG = "com.termux"
    const val PERM = "com.termux.permission.RUN_COMMAND"
    const val EXTRA_ID = "maddy_id"
    const val MARK = "\n__MADDY_CWD__"

    private const val FILES = "/data/data/com.termux/files"
    const val HOME = "$FILES/home"
    private const val BASH = "$FILES/usr/bin/bash"

    const val SETUP_CMD =
        "mkdir -p ~/.termux && grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null " +
            "|| echo 'allow-external-apps=true' >> ~/.termux/termux.properties"

    // $1 = working dir, $2 = command. Prints final cwd on exit so we can track `cd`.
    private const val WRAP =
        "trap 'printf \"\\n__MADDY_CWD__%s\" \"\$(pwd)\"' EXIT; " +
            "cd \"\$1\" 2>/dev/null || cd ~; eval \"\$2\""

    fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0); true
    } catch (e: Exception) { false }

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, PERM) == PackageManager.PERMISSION_GRANTED

    /** Runs [command] in the background in Termux. Returns an error message, or null if dispatched. */
    fun run(ctx: Context, id: Int, command: String, cwd: String): String? {
        if (!isInstalled(ctx)) return "Termux is not installed."
        if (!hasPermission(ctx)) return "RUN_COMMAND permission not granted. Open Setup."
        return try {
            val cb = Intent(ctx, ResultReceiver::class.java).putExtra(EXTRA_ID, id)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pi = PendingIntent.getBroadcast(ctx, id, cb, flags)

            val i = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(PKG, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", WRAP, "_", cwd, command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
            }
            ContextCompat.startForegroundService(ctx, i)
            null
        } catch (e: SecurityException) {
            "Termux refused the request. Run the setup command in Termux, then fully restart Termux."
        } catch (e: Exception) {
            "Could not reach Termux: ${e.message}"
        }
    }

    /** Opens a real interactive Termux session running [command] (for vim, nano, htop, ...). */
    fun runInTermuxSession(ctx: Context, command: String): String? {
        if (!isInstalled(ctx)) return "Termux is not installed."
        if (!hasPermission(ctx)) return "RUN_COMMAND permission not granted. Open Setup."
        return try {
            val i = Intent("com.termux.RUN_COMMAND").apply {
                setClassName(PKG, "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", BASH)
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", "$command; exec bash -l"))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            }
            ContextCompat.startForegroundService(ctx, i)
            null
        } catch (e: SecurityException) {
            "Termux refused the request. Run the setup command in Termux, then fully restart Termux."
        } catch (e: Exception) {
            "Could not reach Termux: ${e.message}"
        }
    }
}
