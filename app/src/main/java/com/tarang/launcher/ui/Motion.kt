package com.tarang.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * The motion vocabulary for the launcher's transitions (enter/exit Frame Art, launch/return an app).
 *
 * Every transition is a Depth move: the home screen recedes into a painting on Frame Art, and dives
 * toward an app on launch. The launcher drives two chrome layers on shared timelines — the dock leads,
 * the top bar trails.
 *
 * Progress convention everywhere: 0f = home (chrome fully present), 1f = gone (Frame Art / app open).
 */

/** The two chrome layers the launcher animates independently. */
enum class ChromeLayer { TOP_BAR, DOCK }

// Easings. StandardEase is Material/iOS-ish accelerate-then-settle; AccelEase is ease-in (start slow,
// fly away); OvershootEase is a gentle ease-out-back that rides just past the target and settles — used
// on the settling direction so the chrome lands home with a subtle bounce. The overshoot stays
// invisible on the way out, where the chrome has already faded to alpha 0 before the tail.
private val StandardEase = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1f)
private val AccelEase = CubicBezierEasing(0.4f, 0.0f, 1f, 1f)

// A gentle ease-out-back: the value rides just past its target and settles back (the subtle Depth
// bounce). This is a CLOSED-FORM function of t, NOT a CubicBezierEasing: a bezier whose Y control
// point is above 1 (an overshoot) makes Compose's x-solver throw "has no solution" at t≈1 and CRASH
// the whole app on the settling frame of the return animation. A direct formula has no solver, so it
// can never crash. [OVERSHOOT] sets how far past the target it rides.
private const val OVERSHOOT = 0.9f
private val OvershootEase = Easing { t ->
    val u = t - 1f
    1f + (OVERSHOOT + 1f) * u * u * u + OVERSHOOT * u * u
}

// ---------------------------------------------------------------------------------------------------
// Timing — the AnimationSpecs the launcher's Animatables run on. Frame transitions are calm/slow; app
// launches are quick. Shared across both launch styles.
// ---------------------------------------------------------------------------------------------------

/** Master progress spec (drives clock reveal, art crossfade + all the gating). */
fun frameMasterSpec(): AnimationSpec<Float> = tween(1100, easing = StandardEase)

/** Dock layer during a Frame Art enter/exit (the dock leads). */
fun frameDockSpec(): AnimationSpec<Float> = tween(900, easing = OvershootEase)

/** Top bar layer during a Frame Art enter/exit (trails the dock). */
fun frameTopBarSpec(): AnimationSpec<Float> = tween(1100, easing = OvershootEase)

/** How long [LauncherScreen] waits after starting the launch animation before it actually starts the
 *  app. Without a hold a warm app appears within ~100ms and cuts the ripple off. Tuned a bit short of
 *  the dock ripple, since the app window takes a beat to appear anyway. */
const val DEPTH_LAUNCH_HOLD_MS = 300L

/** Dock layer during an app launch ([entering]) / return (!entering). */
fun launchDockSpec(entering: Boolean): AnimationSpec<Float> =
    tween(if (entering) 850 else 1000, easing = if (entering) AccelEase else OvershootEase)

/** Top bar layer during an app launch / return. */
fun launchTopBarSpec(entering: Boolean): AnimationSpec<Float> =
    tween(if (entering) 930 else 1140, easing = if (entering) AccelEase else OvershootEase)

// ---------------------------------------------------------------------------------------------------
// Chrome transforms — applied inside the chrome layers' graphicsLayer blocks. [frameP] recedes toward
// Frame Art (scale < 1); [launchP] approaches into an app (scale > 1). Only one is non-zero at a time.
// ---------------------------------------------------------------------------------------------------

/** Shape the chrome for the Depth transition. Called from the layer's `graphicsLayer {}`. */
fun GraphicsLayerScope.applyChrome(layer: ChromeLayer, frameP: Float, launchP: Float) {
    val p = maxOf(frameP, launchP)
    alpha = 1f - (p * 1.5f).coerceAtMost(1f)
    // No blur: an animated RenderEffect blur on these full-screen layers is far too heavy on weak TV
    // GPUs. The scale recede/approach + fade, plus the art rising forward, carry the depth on their own.
    val recede = 1f - 0.10f * frameP
    val approach = 1f + 0.14f * launchP
    val s = recede * approach
    scaleX = s
    scaleY = s
    when (layer) {
        ChromeLayer.TOP_BAR -> {
            translationY = -frameP * 24f - launchP * 30f
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
        ChromeLayer.DOCK -> {
            translationY = frameP * 16f + launchP * 26f
            transformOrigin = TransformOrigin(0.5f, 0.55f)
        }
    }
}

/** The incoming Frame Art's entrance scale (the art rises forward from a hair larger). [p] is the
 *  master frame progress. */
fun artEntryScale(p: Float): Float = 1.06f - 0.06f * p

// ---------------------------------------------------------------------------------------------------
// Dock launch ripple — launching from the dock, the chosen tile leads and its neighbours follow ring by
// ring (distance 1 on each side, then 2, …); the dock chrome (with the grid) trails last. On the return
// the mapping runs backwards, so the chrome re-forms first and the launched tile lands last. Grid
// launches keep the uniform chrome transform.
// ---------------------------------------------------------------------------------------------------

/** Fraction of the launch timeline across which the ripple's start times are spread; every element then
 *  ramps over the remaining (1 - spread), so the last one still finishes exactly on time. */
private const val RIPPLE_SPREAD = 0.45f

/** Floor for the master's return overshoot (OvershootEase dips the progress a hair below 0), kept so
 *  the landing bounce survives the per-slot clamping. */
private const val RIPPLE_DIP = -0.2f

/**
 * Maps the master dock-launch progress (read lazily via [progress], so it can be sampled per frame in a
 * graphicsLayer block) onto staggered per-tile ramps. [origin] is the launched tile's index in a dock
 * of [count] tiles. [style] chooses the per-tile transform.
 */
class DockRipple(
    val origin: Int,
    count: Int,
    private val progress: () -> Float,
) {

    // Ring slots 0..maxRing for the tiles; one more slot after them for the dock chrome.
    private val slots = maxOf(origin, count - 1 - origin) + 1

    private fun staged(slot: Int): Float {
        val p = progress()
        // At/past home (including the return's overshoot dip) everything moves together.
        if (p <= 0f) return p.coerceAtLeast(RIPPLE_DIP)
        val start = RIPPLE_SPREAD * slot / slots
        return ((p - start) / (1f - RIPPLE_SPREAD)).coerceIn(0f, 1f)
    }

    /** The staggered progress for dock tile [index]. */
    fun tileProgress(index: Int): Float = staged(abs(index - origin))

    /** The trailing progress for the dock chrome layer (frosted bar + grid). */
    fun chromeProgress(): Float = staged(slots)

    /** The per-tile transform, read lazily each frame in the tile's own layer. */
    fun tileLayer(index: Int): GraphicsLayerScope.() -> Unit =
        { applyDisperseTile(tileProgress(index), tileSpread(index), tileGrowth(index)) }

    // ---- Geometry -------------------------------------------------------------------------------

    /** How much tile [index] grows: the launched tile is the hero (1.5×); each ring outward carries
     *  less energy, so the far tiles mostly drift and dissolve. */
    private fun tileGrowth(index: Int): Float = growthOfRing(abs(index - origin))

    /**
     * Signed horizontal travel for tile [index], in tile widths: an anti-merge spread (each inner ring
     * pushes the tile outward by the room its growing inner neighbours consume) plus an accelerating
     * fly-out proportional to the distance from the hero (a zoom about the launched tile, so the wave
     * carries every other tile off the sides while the hero stays the vanishing point).
     */
    private fun tileSpread(index: Int): Float {
        val d = abs(index - origin)
        var sum = 0f
        for (k in 0 until d) {
            val pairGrowth = (growthOfRing(k) + growthOfRing(k + 1)) / 2f
            sum += pairGrowth * staged(k).coerceAtLeast(0f)
        }
        val p = staged(d)
        sum += d * RIPPLE_FLY * p * abs(p)
        return if (index >= origin) sum else -sum
    }

    // The wave loses energy as it spreads: ring 1 grows by RIPPLE_GROWTH, each further ring by
    // RIPPLE_DECAY of the previous one.
    private fun growthOfRing(ring: Int): Float = when (ring) {
        0 -> RIPPLE_GROWTH_ORIGIN
        else -> RIPPLE_GROWTH * RIPPLE_DECAY.pow(ring - 1)
    }
}

/** How much the first ring (the hero's direct neighbours) grows in DISPERSE. */
private const val RIPPLE_GROWTH = 0.22f

/** Growth carried over from each ring to the next in DISPERSE — the wave's energy falloff. */
private const val RIPPLE_DECAY = 0.65f

/** How much the launched tile itself grows — the hero of the move. */
private const val RIPPLE_GROWTH_ORIGIN = 0.50f

/** Fly-out distance per ring of separation from the hero, in tile widths at full progress (DISPERSE). */
private const val RIPPLE_FLY = 1.0f

/** Smoothstep — a soft 0→1 ease over [0,1], for dissolves that don't ramp linearly. */
private fun smoothstep(x: Float): Float = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** Extra mid-flight lift (an arc) for a dispersing tile, in tile heights. */
private const val DISPERSE_ARC = 0.12f

/**
 * Per-tile transform for the dock launch: the tile scales up in place first, then rises (on a curve)
 * and flies out to the side (see [DockRipple.tileSpread]), dissolving on a smooth curve. MULTIPLIES
 * into the tile's own graphicsLayer (alpha < 1 makes a layer composite offscreen clipped to its
 * bounds, so a separate wrapper layer would crop the focus-scale overflow into a square).
 */
fun GraphicsLayerScope.applyDisperseTile(p: Float, spread: Float, growth: Float) {
    val vp = p.coerceAtLeast(0f)
    // Smooth dissolve: opaque through the first third, then ease out (the fly-out and the screen edge
    // remove the tiles; the fade just cleans up whatever hasn't left the frame).
    alpha *= 1f - smoothstep((vp - 0.35f) / 0.55f)
    // Grow in place — no anticipation dip (a pre-shrink read as "shifting up before scaling").
    val s = 1f + growth * p
    scaleX *= s
    scaleY *= s
    // Lift ramps in AFTER the grow (quadratic, sign-preserving) so the tile pops toward you first and
    // only then rises — no upward shift before it scales. An extra hump at mid-flight arcs the exit
    // instead of sending it straight up.
    val arc = DISPERSE_ARC * size.height * (vp * (1f - vp) * 4f)
    translationY += -0.33f * size.height * (p * abs(p)) - arc
    translationX += spread * size.width
}

// ===================================================================================================
// Windows Metro motion — a separate motion language for the Metro home style. Its launch is the
// signature: the tapped tile zooms toward the viewer and dissolves (the app grows out of it) while
// every other tile flies radially off the screen; the return runs the mapping backwards so the tiles
// cascade back in. The Start screen also has an entrance cascade (tiles rise + fade in on a diagonal).
// ===================================================================================================

// A fast ease-out for the scatter (quick start, gentle settle). Its Y control points stay ≤ 1, so —
// unlike an overshoot bezier — it can never make Compose's solver throw. The return uses a plain
// ease-in-out. Neither overshoots.
private val MetroOutEase = CubicBezierEasing(0.12f, 0.85f, 0.25f, 1f)
private val MetroInEase = CubicBezierEasing(0.35f, 0.0f, 0.25f, 1f)

/** How long [LauncherScreen] waits after starting the Metro launch scatter before it starts the app,
 *  so the tiles are mostly gone before the app window covers them. */
const val METRO_LAUNCH_HOLD_MS = 320L

/** The Metro launch/return spec. Entering: a fast scatter out. Returning: a calmer cascade in. */
fun metroLaunchSpec(entering: Boolean): AnimationSpec<Float> =
    tween(if (entering) 420 else 560, easing = if (entering) MetroOutEase else MetroInEase)

/** The Metro Start-screen entrance-cascade spec. */
fun metroAppearSpec(): AnimationSpec<Float> = tween(720, easing = StandardEase)

// Launch geometry.
private const val METRO_HERO_ZOOM = 1.5f // how much the tapped tile grows on its way out
private const val METRO_FLY_BASE = 1.4f // base fly-out distance for a neighbour, in tile widths
private const val METRO_FLY_PER_RING = 0.35f // extra fly-out per unit of distance from the hero
private const val METRO_SCATTER_STAGGER = 0.05f // start-time offset per unit distance (the parting ripples out)

// Entrance-cascade geometry.
private const val METRO_APPEAR_STAGGER = 0.045f // start-time offset per (row + col) diagonal step
private const val METRO_APPEAR_RISE = 0.22f // how far a tile rises into place, in tile heights

/**
 * Maps the master Metro launch progress ([progress], sampled per frame in a graphicsLayer) onto a
 * per-tile scatter about the tapped tile. [origin] is the hero's index in a [columns]-wide grid.
 * Progress: 0f = home (tiles at rest), 1f = launched (hero zoomed away, neighbours off-screen).
 */
class MetroLaunch(
    val origin: Int,
    private val columns: Int,
    private val progress: () -> Float,
) {
    private val originRow = origin / columns
    private val originCol = origin % columns

    /** The per-tile transform for grid [index], read lazily each frame in the tile's own layer. */
    fun tileLayer(index: Int): GraphicsLayerScope.() -> Unit = {
        val p = progress().coerceIn(0f, 1f)
        if (index == origin) {
            applyMetroHero(p)
        } else {
            applyMetroScatter(p, index % columns - originCol, index / columns - originRow)
        }
    }
}

/** The hero tile: it zooms toward the viewer and dissolves — the app appears to grow out of it. */
private fun GraphicsLayerScope.applyMetroHero(p: Float) {
    val s = 1f + METRO_HERO_ZOOM * p
    scaleX *= s
    scaleY *= s
    alpha *= 1f - smoothstep((p - 0.15f) / 0.55f)
}

/** A neighbour tile: it flies radially outward from the hero (along its grid offset) and fades. */
private fun GraphicsLayerScope.applyMetroScatter(p: Float, dx: Int, dy: Int) {
    val dist = hypot(dx.toFloat(), dy.toFloat()).coerceAtLeast(1f)
    val start = (METRO_SCATTER_STAGGER * (dist - 1f)).coerceIn(0f, 0.35f)
    val local = ((p - start) / (1f - start)).coerceIn(0f, 1f)
    val fly = (METRO_FLY_BASE + METRO_FLY_PER_RING * dist) * local
    translationX += dx / dist * fly * size.width
    translationY += dy / dist * fly * size.height
    val shrink = 1f - 0.12f * local
    scaleX *= shrink
    scaleY *= shrink
    alpha *= 1f - smoothstep((local - 0.05f) / 0.5f)
}

/**
 * The Metro Start-screen entrance: tile at [row]/[col] rises into place and fades in, staggered on a
 * diagonal from the top-left. [p] is the appear progress (0 → nothing shown, 1 → all settled).
 * MULTIPLIES into the tile's own graphicsLayer, so it composes with the launch scatter and focus scale.
 */
fun GraphicsLayerScope.applyMetroAppear(p: Float, row: Int, col: Int) {
    if (p >= 1f) return
    val delay = (METRO_APPEAR_STAGGER * (row + col)).coerceAtMost(0.6f)
    val local = ((p - delay) / 0.4f).coerceIn(0f, 1f)
    val e = 1f - (1f - local) * (1f - local) // ease-out quad
    translationY += (1f - e) * METRO_APPEAR_RISE * size.height
    alpha *= e
    val s = 0.92f + 0.08f * e
    scaleX *= s
    scaleY *= s
}
