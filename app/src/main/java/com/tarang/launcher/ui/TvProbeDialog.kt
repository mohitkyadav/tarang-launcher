package com.tarang.launcher.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.tarang.launcher.data.TV_LISTINGS_PERMISSION
import com.tarang.launcher.data.TvContentProbe
import com.tarang.launcher.data.TvProbeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Diagnostic overlay for the "can apps feed a content carousel?" spike. Runs [TvContentProbe] and
 * reports, in plain language, whether the TvProvider rows are readable, blocked, or empty on this
 * device. Sideload onto the real Chromecast and open it to get the verdict.
 */
@Composable
fun TvProbeDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val context = LocalContext.current
        var reloadKey by remember { mutableIntStateOf(0) }
        var granted by remember { mutableStateOf(false) }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted = it; reloadKey++ }

        val result by produceState<TvProbeResult?>(initialValue = null, key1 = reloadKey) {
            value = withContext(Dispatchers.IO) { TvContentProbe.run(context) }
        }
        val firstFocus = remember { FocusRequester() }

        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .heightIn(max = 480.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF141417))
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("TV content probe", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

                val r = result
                if (r == null) {
                    Text("Scanning…", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                } else {
                    val verdict = verdictOf(r)
                    Text(verdict.first, color = verdict.second, fontSize = 16.sp, fontWeight = FontWeight.Medium)

                    Mono("READ_TV_LISTINGS: ${if (r.permissionGranted) "granted" else "NOT granted"}")
                    Mono("preview channels: ${fmt(r.previewChannels)}    preview programs: ${fmt(r.previewPrograms)}    watch-next: ${fmt(r.watchNext)}")
                    r.error?.let { Mono("error: $it", Color(0xFFFF8A80)) }

                    if (r.perPackage.isNotEmpty()) {
                        Mono("by app:")
                        r.perPackage.take(8).forEach { (pkg, n) -> Mono("  • $pkg — $n") }
                    }
                    if (r.samples.isNotEmpty()) {
                        Mono("samples [P=poster V=video I=intent]:")
                        r.samples.forEach { s ->
                            val flags = buildString {
                                if (s.poster) append("P"); if (s.video) append("V"); if (s.intent) append("I")
                            }
                            Mono("  • [${flags.ifEmpty { "-" }}] ${s.pkg}: ${s.title.take(40)}")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                    ProbeButton("Rescan", Modifier.focusRequester(firstFocus)) { reloadKey++ }
                    if (r != null && !r.permissionGranted) {
                        ProbeButton("Request permission") { permLauncher.launch(TV_LISTINGS_PERMISSION) }
                    }
                    ProbeButton("Close") { onDismiss() }
                }
            }
        }

        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun Mono(text: String, color: Color = Color.White.copy(alpha = 0.75f)) {
    Text(text, color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
}

private fun fmt(n: Int) = if (n < 0) "n/a" else n.toString()

private fun verdictOf(r: TvProbeResult): Pair<String, Color> {
    val red = Color(0xFFFF8A80)
    val amber = Color(0xFFFFE082)
    val green = Color(0xFF80E27E)
    return when {
        r.blocked ->
            "Blocked: this launcher can't read other apps' rows here (permission restricted)." to red
        r.previewPrograms > 0 ->
            "Works — ${r.previewPrograms} programs from ${r.perPackage.size} app(s). A carousel is feasible here." to green
        !r.permissionGranted ->
            "Grant READ_TV_LISTINGS, then rescan (the read needs it)." to amber
        else ->
            "Readable, but nothing published yet (apps haven't added rows, or Google TV serves them server-side)." to amber
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ProbeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(percent = 50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2A2A2E),
            focusedContainerColor = Color(0xFF3A6FF2),
        ),
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
}
