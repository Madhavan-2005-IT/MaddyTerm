package com.maddyscope.maddyterm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { TerminalScreen() }
        }
    }
}

private val BG = Color(0xFF0D1117)
private val MONO = FontFamily.Monospace

private fun colorFor(k: Kind) = when (k) {
    Kind.INFO -> Color(0xFF8B949E)
    Kind.CMD -> Color(0xFF58A6FF)
    Kind.OUT -> Color(0xFFE6EDF3)
    Kind.ERR -> Color(0xFFFF7B72)
}

@Composable
fun TerminalScreen(vm: TerminalViewModel = viewModel()) {
    val ctx = LocalContext.current
    var installed by remember { mutableStateOf(TermuxBridge.isInstalled(ctx)) }
    var granted by remember { mutableStateOf(TermuxBridge.hasPermission(ctx)) }
    var showSetup by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        installed = TermuxBridge.isInstalled(ctx)
        granted = TermuxBridge.hasPermission(ctx)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(vm.lines.size) {
        if (vm.lines.isNotEmpty()) {
            try { listState.scrollToItem(vm.lines.size - 1) } catch (_: Exception) {}
        }
    }

    fun send() {
        val c = input; input = ""
        if (!installed || !granted) showSetup = true
        vm.submit(c)
    }

    Column(
        Modifier.fillMaxSize().background(BG).systemBarsPadding().imePadding()
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("MaddyTerm", color = Color(0xFF3FB950), fontFamily = MONO, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            if (vm.pending > 0) Text("⏳ ${vm.pending}", color = Color(0xFFD29922), fontFamily = MONO)
            TextButton(onClick = { showSetup = !showSetup }) { Text("Setup") }
        }

        if (showSetup || !installed || !granted) {
            SetupPanel(
                installed = installed,
                granted = granted,
                onGrant = { permLauncher.launch(TermuxBridge.PERM) },
                onCopy = {
                    try {
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("setup", TermuxBridge.SETUP_CMD))
                    } catch (_: Exception) {}
                },
                onOpenTermux = {
                    try {
                        ctx.packageManager.getLaunchIntentForPackage(TermuxBridge.PKG)
                            ?.let { ctx.startActivity(it) }
                    } catch (_: Exception) {}
                },
                onTest = { vm.submit("echo Termux link OK; uname -a") }
            )
        }

        SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.padding(horizontal = 12.dp)) {
                items(vm.lines) { line ->
                    Text(
                        line.text,
                        color = colorFor(line.kind),
                        fontFamily = MONO,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Quick keys
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            TextButton(onClick = { vm.historyPrev()?.let { input = it } }) { Text("↑") }
            TextButton(onClick = { input = vm.historyNext() }) { Text("↓") }
            TextButton(onClick = { input += "/" }) { Text("/") }
            TextButton(onClick = { input += "~" }) { Text("~") }
            TextButton(onClick = { input += " | " }) { Text("|") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.clear() }) { Text("Clear") }
            TextButton(onClick = { vm.openInTermux(input); input = "" }) { Text("Termux ↗") }
        }

        // Prompt
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF161B22)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${vm.promptDir()} $ ", color = Color(0xFF3FB950), fontFamily = MONO, fontSize = 14.sp)
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = MONO, fontSize = 14.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            TextButton(onClick = { send() }) { Text("Send") }
        }
    }
}

@Composable
private fun SetupPanel(
    installed: Boolean,
    granted: Boolean,
    onGrant: () -> Unit,
    onCopy: () -> Unit,
    onOpenTermux: () -> Unit,
    onTest: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).background(Color(0xFF161B22)).padding(12.dp)
    ) {
        val mono = TextStyle(color = Color(0xFFE6EDF3), fontFamily = MONO, fontSize = 12.sp)
        Text(
            (if (installed) "✔" else "✘") + " 1. Install Termux (F-Droid or GitHub build, not Play Store)",
            style = mono
        )
        Text(
            (if (granted) "✔" else "✘") + " 2. Grant the RUN_COMMAND permission",
            style = mono
        )
        Text("• 3. In Termux, run the setup command (Copy button), then force-stop and reopen Termux", style = mono)
        Text("• 4. Come back here and press Test", style = mono)
        Row {
            if (!granted) TextButton(onClick = onGrant) { Text("Grant") }
            TextButton(onClick = onCopy) { Text("Copy setup cmd") }
            TextButton(onClick = onOpenTermux) { Text("Open Termux") }
            TextButton(onClick = onTest) { Text("Test") }
        }
    }
}
