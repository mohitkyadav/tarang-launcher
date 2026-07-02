package com.tarang.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope

/**
 * Runs [block] only while the launcher is at least STARTED (on-screen), cancelling it when the app is
 * stopped — screen off, or another app in the foreground — and restarting it on return. Use it for
 * periodic tickers (a Frame Art / artwork slideshow, a minute clock) so they don't keep spinning —
 * and, for the slideshows, keep decoding full-size bitmaps on a timer — while nothing is visible.
 *
 * [keys] behave like [LaunchedEffect] keys: a change restarts the loop (e.g. a new slideshow folder
 * or interval); no keys means it only ever restarts across a stop/start.
 */
@Composable
fun RunWhileStarted(vararg keys: Any?, block: suspend CoroutineScope.() -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(*keys, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED, block)
    }
}
