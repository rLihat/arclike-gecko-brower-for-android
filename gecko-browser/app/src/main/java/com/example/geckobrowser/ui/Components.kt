package com.example.geckobrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.geckobrowser.downExt
import com.example.geckobrowser.downKindLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import androidx.core.graphics.PathParser

/** One easing curve for every open/close animation in the app. */
internal val morphEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** The one enter animation for every popup/banner/pill notification in the app. */
internal fun popupEnter(reduceMotion: Boolean) = fadeIn(tween(if (reduceMotion) 0 else 160)) +
    scaleIn(tween(if (reduceMotion) 0 else 260, easing = morphEasing), initialScale = 0.96f) +
    slideInVertically(tween(if (reduceMotion) 0 else 260, easing = morphEasing)) { it / 6 }

/** The one exit animation for every popup/banner/pill notification in the app. */
internal fun popupExit(reduceMotion: Boolean) = fadeOut(tween(if (reduceMotion) 0 else 140))

/* ------------------------------------------------------------------ icons */

private fun p(block: Path.() -> Unit) = Path().apply(block)

/** Builds Compose paths from Lucide SVG path data. All values are on the 24dp grid. */
private fun lucide(vararg data: String): List<Path> =
    data.map { PathParser.createPathFromPathData(it) }

private val IcoPlus: List<Path> = lucide("M5 12h14", "M12 5v14")
private val IcoExpand: List<Path> = lucide("M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3")
private val IcoChevronUp: List<Path> = lucide("m18 15-6-6-6 6")
private val IcoArrowUp: List<Path> = lucide("m5 12 7-7 7 7", "M12 19V5")
private val IcoChevronLeft: List<Path> = lucide("m15 18-6-6 6-6")
private val IcoChevronRight: List<Path> = lucide("m9 18 6-6-6-6")
private val IcoChevronDown: List<Path> = lucide("m6 9 6 6 6-6")
private val IcoClose: List<Path> = lucide("M18 6 6 18", "m6 6 12 12")
private val IcoBack: List<Path> = lucide("m12 19-7-7 7-7", "M19 12H5")
private val IcoForward: List<Path> = lucide("M5 12h14", "m12 5 7 7-7 7")
private val IcoRefresh: List<Path> = lucide("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")
private val IcoCopy: List<Path> = lucide("M 8 8 H 22 V 22 H 8 Z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
private val IcoGlobe: List<Path> = lucide("M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20")
private val IcoTabs: List<Path> = lucide("M 3 3 H 21 V 21 H 3 Z", "M3 9h18")
private val IcoShare: List<Path> = lucide("M 15 5 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0", "M 3 12 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0", "M 15 19 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0", "M 8.59 13.51 L 15.42 17.49", "M 15.41 6.51 L 8.59 10.49")
private val IcoTranslate: List<Path> = lucide("m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6")
private val IcoFind: List<Path> = lucide("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M 9 12 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0", "m16 16-1.9-1.9")
private val IcoContrast: List<Path> = lucide("M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0", "M12 18a6 6 0 0 0 0-12v12z")
private val IcoPuzzle: List<Path> = lucide("M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z")
private val IcoMoon: List<Path> = lucide("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")
private val IcoTextSize: List<Path> = lucide("M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6")
private val IcoDownload: List<Path> = lucide("M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")
private val IcoClock: List<Path> = lucide("M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0", "M12 6v6l4 2")
private val IcoGrid: List<Path> = lucide("M15 3v18", "M3 12h18", "M9 3v18", "M 3 3 H 21 V 21 H 3 Z")
private val IcoGear: List<Path> = lucide("M11 10.27 7 3.34", "m11 13.73-4 6.93", "M12 22v-2", "M12 2v2", "M14 12h8", "m17 20.66-1-1.73", "m17 3.34-1 1.73", "M2 12h2", "m20.66 17-1.73-1", "m20.66 7-1.73 1", "m3.34 17 1.73-1", "m3.34 7 1.73 1", "M 10 12 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0", "M 4 12 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0")
private val IcoCheck: List<Path> = lucide("M20 6 9 17l-5-5")
private val IcoLink: List<Path> = lucide("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
private val IcoTrash: List<Path> = lucide("M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
private val IcoLock: List<Path> = lucide("M 3 11 H 21 V 22 H 3 Z", "M7 11V7a5 5 0 0 1 10 0v4")
private val IcoPerson: List<Path> = lucide("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M 8 7 a 4 4 0 1 0 8 0 a 4 4 0 1 0 -8 0")
private val IcoPin: List<Path> = lucide("M12 17v5", "M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z")
private val IcoOpen: List<Path> = lucide("M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
private val IcoImage: List<Path> = lucide("M 3 3 H 21 V 21 H 3 Z", "M 7 9 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")
private val IcoMask: List<Path> = lucide("M15 10v1", "M7.528 20.472a1.6 1.6 0 012.277 0l1.057 1.056a1.6 1.6 0 002.276 0l1.057-1.056a1.6 1.6 0 012.277 0l1.114 1.114a1.4 1.4 0 002.414-1V10a8 8 0 00-16 0v10.586a1.4 1.4 0 002.414 1z", "M9 10v1")
private val IcoPause: List<Path> = lucide("M 14 3 H 19 V 21 H 14 Z", "M 5 3 H 10 V 21 H 5 Z")
private val IcoPlay: List<Path> = lucide("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")
private val IcoPinOff: List<Path> = lucide("M12 17v5", "M15 9.34V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H7.89", "m2 2 20 20", "M9 9v1.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h11")
private val IcoLens: List<Path> = lucide("M15.725 7.296h.01", "M6 12a6 6 0 016-6", "M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0")
private val IcoZoomIn: List<Path> = lucide("M 3 11 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0", "M 21 21 L 16.65 16.65", "M 11 8 L 11 14", "M 8 11 L 14 11")
private val IcoZoomOut: List<Path> = lucide("M 3 11 a 8 8 0 1 0 16 0 a 8 8 0 1 0 -16 0", "M 21 21 L 16.65 16.65", "M 8 11 L 14 11")
private val IcoUserCog: List<Path> = lucide("M10 15H6a4 4 0 0 0-4 4v2", "m14.305 16.53.923-.382", "m15.228 13.852-.923-.383", "m16.852 12.228-.383-.923", "m16.852 17.772-.383.924", "m19.148 12.228.383-.923", "m19.53 18.696-.382-.924", "m20.772 13.852.924-.383", "m20.772 16.148.924.383", "M 15 15 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0", "M 5 7 a 4 4 0 1 0 8 0 a 4 4 0 1 0 -8 0")
private val IcoDiagramFlow: List<Path> = lucide("M8 12h.5a3.5 3.5 0 013.5 3.5 3.5 3.5 0 003.5 3.5h.5", "M8.5 12A3.5 3.5 0 0012 8.5 3.5 3.5 0 0115.5 5h.5", "M 16 16 H 22 V 22 H 16 Z", "M 16 2 H 22 V 8 H 16 Z", "M 2 9 H 8 V 15 H 2 Z")
private val IcoGhost: List<Path> = lucide("M15 10v1", "M7.528 20.472a1.6 1.6 0 012.277 0l1.057 1.056a1.6 1.6 0 002.276 0l1.057-1.056a1.6 1.6 0 012.277 0l1.114 1.114a1.4 1.4 0 002.414-1V10a8 8 0 00-16 0v10.586a1.4 1.4 0 002.414 1z", "M9 10v1")
private val IcoRotateCwFadingClock: List<Path> = lucide("M12 3a9.75 9.75 0 0 1 6.74 2.74", "M18.74 5.74 21 8", "M21 8V3", "M7.5 19.794c-6-3.464-6-12.124 0-15.588", "M7.5 4.206A9 9 0 0 1 12 3", "M12 7v5l4 2", "M14 20.775A9 9 0 0 1 12 21", "M19 17.656a9 9 0 0 1-1.5 1.456", "M21 12a9 9 0 0 1-.228 2", "M21 8h-5")
private val IcoCircleFadingArrowUp: List<Path> = lucide("M12 2a10 10 0 0 1 7.38 16.75", "m16 12-4-4-4 4", "M12 16V8", "M2.5 8.875a10 10 0 0 0-.5 3", "M2.83 16a10 10 0 0 0 2.43 3.4", "M4.636 5.235a10 10 0 0 1 .891-.857", "M8.644 21.42a10 10 0 0 0 7.631-.38")
private val IcoLightbulb: List<Path> = lucide("M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4")
private val IcoOpacity: List<Path> = lucide("M12 3h.01", "M12 9h.01", "M15 6h.01", "M18 3h.01", "M21 6v13a2 2 0 0 1-2 2H6", "M3 12h.01", "M3 18h.01", "M3 6h.01", "M6 15h.01", "M6 3h.01", "M6 9h.01", "M9 12h.01", "M9 6h.01")
private val IcoLayersMinus: List<Path> = lucide("M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 .83.18 2 2 0 0 0 .83-.18l8.58-3.9a1 1 0 0 0 0-1.832z", "M16 17h6", "M2.003 11.995a1 1 0 0 0 .597.915l8.58 3.91a2 2 0 0 0 .83.18", "M2.003 16.995a1 1 0 0 0 .597.915l8.58 3.91a2 2 0 0 0 .83.18 2 2 0 0 0 .83-.18l2.11-.96", "M22.018 12.004a1 1 0 0 1-.598.916l-.177.08")
private val IcoBadgeInfo: List<Path> = lucide("M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z", "M 12 16 L 12 12", "M 12 8 L 12.01 8")
private val IcoBadgeCheck: List<Path> = lucide("M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z", "m16 9-5.5 5.5L8 12")
private val IcoMic: List<Path> = lucide("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M 9 2 H 15 V 15 H 9 Z")
private val IcoPanelTop: List<Path> = lucide("M 3 3 H 21 V 21 H 3 Z", "M3 9h18")
private val IcoStretchVertical: List<Path> = lucide("M 4 2 H 10 V 22 H 4 Z", "M 14 2 H 20 V 22 H 14 Z")
private val IcoGrid3x2: List<Path> = lucide("M15 3v18", "M3 12h18", "M9 3v18", "M 3 3 H 21 V 21 H 3 Z")
private val IcoPalette: List<Path> = lucide("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z", "M 13 6.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0", "M 17 10.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0", "M 6 12.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0", "M 8 7.5 a 0.5 0.5 0 1 0 1 0 a 0.5 0.5 0 1 0 -1 0")
private val IcoVibrate: List<Path> = lucide("m2 8 2 2-2 2 2 2-2 2", "m22 8-2 2 2 2-2 2 2 2", "M 8 5 H 16 V 19 H 8 Z")
private val IcoShieldCheck: List<Path> = lucide("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4")
private val IcoShield: List<Path> = lucide("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")
private val IcoSparkles: List<Path> = lucide("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4", "M 2 20 a 2 2 0 1 0 4 0 a 2 2 0 1 0 -4 0")
private val IcoInfo: List<Path> = lucide("M 2 12 a 10 10 0 1 0 20 0 a 10 10 0 1 0 -20 0", "M12 16v-4", "M12 8h.01")
private val IcoFolder: List<Path> = lucide("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")
private val IcoExternalLink: List<Path> = lucide("M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
private val IcoChevronRightSmall: List<Path> = lucide("m9 18 6-6-6-6")
private val IcoCamera: List<Path> = lucide("M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z", "M 9 13 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0")
private val IcoBell: List<Path> = lucide("M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326")
private val IcoLanguages: List<Path> = lucide("m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6")
@Composable
fun Glyph(
    paths: List<Path>,
    tint: Color,
    size: Dp = 22.dp,
    strokeWidth: Float = 2.2f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach {
                drawPath(
                    it, tint,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

object Ico {
    val plus = IcoPlus
    val expand = IcoExpand
    val chevronUp = IcoChevronUp
    val arrowUp = IcoArrowUp
    val chevronLeft = IcoChevronLeft
    val chevronRight = IcoChevronRight
    val chevronDown = IcoChevronDown
    val close = IcoClose
    val back = IcoBack
    val forward = IcoForward
    val refresh = IcoRefresh
    val copy = IcoCopy
    val globe = IcoGlobe
    val tabs = IcoTabs
    val share = IcoShare
    val translate = IcoTranslate
    val find = IcoFind
    val contrast = IcoContrast
    val puzzle = IcoPuzzle
    val moon = IcoMoon
    val textSize = IcoTextSize
    val download = IcoDownload
    val clock = IcoClock
    val grid = IcoGrid
    val gear = IcoGear
    val check = IcoCheck
    val link = IcoLink
    val trash = IcoTrash
    val lock = IcoLock
    val person = IcoPerson
    val pin = IcoPin
    val open = IcoOpen
    val image = IcoImage
    val mask = IcoPrivate
    val pause = IcoPause
    val play = IcoPlay
    val pinOff = IcoPinOff
    val lens = IcoLens
    val zoomIn = IcoZoomIn
    val zoomOut = IcoZoomOut
    val userCog = IcoUserCog
    val diagramFlow = IcoDiagramFlow
    val rotateCwFadingClock = IcoRotateCwFadingClock
    val circleFadingArrowUp = IcoCircleFadingArrowUp
    val lightbulb = IcoLightbulb
    val opacity = IcoOpacity
    val layersMinus = IcoLayersMinus
    val badgeInfo = IcoBadgeInfo
    val badgeCheck = IcoBadgeCheck
    val mic = IcoMic
    val panelTop = IcoPanelTop
    val stretchVertical = IcoStretchVertical
    val grid3x2 = IcoGrid3x2
    val palette = IcoPalette
    val vibrate = IcoVibrate
    val shieldCheck = IcoShieldCheck
    val shield = IcoShield
    val sparkles = IcoSparkles
    val info = IcoInfo
    val folder = IcoFolder
    val externalLink = IcoExternalLink
    val camera = IcoCamera
    val bell = IcoBell
    val languages = IcoLanguages
}

/* -------------------------------------------------------- download tiles */

enum class FileKind { Zip, Pdf, Image, Xpi, Other }

fun fileKindOf(name: String): FileKind = when (downExt(name)) {
    "png", "jpg", "jpeg", "gif", "webp", "svg" -> FileKind.Image
    "pdf" -> FileKind.Pdf
    "zip", "rar", "7z", "gz", "tar" -> FileKind.Zip
    "xpi" -> FileKind.Xpi
    else -> FileKind.Other
}

@Composable
fun fileTileBg(kind: FileKind): Color {
    val c = LocalArc.current
    return when (kind) {
        FileKind.Zip -> Color(0x294F93A8)
        FileKind.Pdf -> Color(0x24D14C40)
        FileKind.Image -> Color(0x267E62BE)
        FileKind.Xpi -> Color(0x26DE5833)
        FileKind.Other -> c.chip
    }
}

@Composable
fun fileTileInk(kind: FileKind): Color {
    val c = LocalArc.current
    val dark = c.dark
    return when (kind) {
        FileKind.Zip -> if (dark) Color(0xFF9FD0DF) else Color(0xFF2F6C7E)
        FileKind.Pdf -> if (dark) Color(0xFFFF9D92) else Color(0xFFB64A3E)
        FileKind.Image -> if (dark) Color(0xFFC1B0EF) else Color(0xFF6C58A8)
        FileKind.Xpi -> if (dark) Color(0xFFFFAB8D) else Color(0xFFC0562F)
        FileKind.Other -> c.muted
    }
}

@Composable
fun DownloadFileTile(name: String, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    val kind = fileKindOf(name)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.2857f))
            .background(fileTileBg(kind)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            downKindLabel(name),
            color = fileTileInk(kind),
            fontSize = (size.value * 0.238f).sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp,
        )
    }
}

/* --------------------------------------------------------------- switches */

@Composable
fun ArcSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalArc.current
    val x by animateDpAsState(if (checked) 18.dp else 0.dp, tween(180), label = "knob")
    Box(
        Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) c.accent else c.switchOff)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(start = 3.dp)
                .offset(x = x)
                .size(20.dp)
                .clip(CircleShape)
                .background(c.knob)
        )
    }
}

/* -------------------------------------------------------------- settings */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 7.dp),
        color = c.muted,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
    )
}

@Composable
fun SettingsRow(
    label: String,
    icon: (@Composable () -> Unit)? = null,
    value: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val c = LocalArc.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                Box(
                    Modifier.size(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    icon()
                }
            }
        }
        Text(
            label,
            color = c.text,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(value, color = c.muted, fontSize = 13.sp, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        if (trailing != null) {
            Box(Modifier.padding(start = 8.dp)) { trailing() }
        } else if (onClick != null) {
            Glyph(Ico.chevronRight, c.muted, size = 16.dp, strokeWidth = 1.9f)
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = LocalArc.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (LocalFrost.current && LocalHazeState.current != null) {
            GlassFill(dark = c.dark)
        } else {
            Box(Modifier.matchParentSize().background(c.surface))
        }
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            content = content,
        )
    }
}

/* ------------------------------------------------------------------ pill */

/**
 * Swipe recognition for the nav controls. A drag past the threshold (26dp for
 * the pill controls, more for full-screen surfaces) fires at most one action;
 * because the drag consumes the pointer, the underlying click never also fires.
 * Back/forward bindings ask for `strictHorizontal`, which raises the horizontal
 * bar to 36dp at a 2.2 ratio so a long diagonal doesn't navigate.
 */
fun Modifier.swipeActions(
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    threshold: Dp = 26.dp,
    strictHorizontal: Boolean = false,
): Modifier {
    if (onUp == null && onDown == null && onLeft == null && onRight == null) return this
    return this.pointerInput(onUp, onDown, onLeft, onRight, threshold, strictHorizontal) {
        val slop = threshold.toPx()
        val horizontalSlop = if (strictHorizontal) 36.dp.toPx() else slop
        val verticalRatio = 1.25f
        val horizontalRatio = if (strictHorizontal) 2.2f else 1.25f
        var dx = 0f
        var dy = 0f
        detectDragGestures(
            onDragStart = {
                dx = 0f
                dy = 0f
            },
            onDrag = { change, amount ->
                change.consume()
                dx += amount.x
                dy += amount.y
            },
            onDragEnd = {
                val ax = abs(dx)
                val ay = abs(dy)
                if (max(ax, ay) >= slop) {
                    when {
                        ay >= slop && ay > ax * verticalRatio && dy < 0f -> onUp?.invoke()
                        ay >= slop && ay > ax * verticalRatio && dy > 0f -> onDown?.invoke()
                        ax >= horizontalSlop && ax > ay * horizontalRatio && dx < 0f -> onLeft?.invoke()
                        ax >= horizontalSlop && ax > ay * horizontalRatio && dx > 0f -> onRight?.invoke()
                    }
                }
            },
        )
    }
}

@Composable
fun PillButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    strictHorizontal: Boolean = false,
    content: @Composable () -> Unit,
) {
    val c = LocalArc.current
    // A fresh lambda is created on every recomposition; keying the detector on
    // it restarted the gesture mid-hold, so long press never completed. Keep
    // the key stable and read the current callback through state instead.
    val longClickState = rememberUpdatedState(onLongClick)
    var longFired by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                if (longClickState.value != null) {
                    detectTapGestures(onLongPress = {
                        longFired = true
                        longClickState.value?.invoke()
                    })
                }
            }
            .swipeActions(
                onUp = onSwipeUp,
                onDown = onSwipeDown,
                onLeft = onSwipeLeft,
                onRight = onSwipeRight,
                strictHorizontal = strictHorizontal,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    // The release after a long press also looks like a click;
                    // swallow it so the chooser isn't replaced by a search.
                    if (longFired) longFired = false else onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The pulsing ring drawn around the nav pill while a page loads. */
@Composable
fun LoadingGlow(active: Boolean, radius: Dp, modifier: Modifier = Modifier) {
    val c = LocalArc.current
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        modifier.drawBehind {
            if (!active) return@drawBehind
            val base = c.glow.copy(alpha = 0.18f + 0.42f * pulse)
            for (i in 0..3) {
                val grow = i * 3.dp.toPx()
                drawRoundRect(
                    color = base.copy(alpha = base.alpha / (i + 1)),
                    topLeft = Offset(-grow, -grow),
                    size = androidx.compose.ui.geometry.Size(size.width + grow * 2, size.height + grow * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx() + grow),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
        }
    )
}

/** Small letter glyph used when a site has no favicon. No tile or background. */
@Composable
fun FaviconTile(letter: String, size: Dp = 22.dp) {
    val c = LocalArc.current
    Box(
        Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.uppercase().take(1),
            color = c.accent,
            fontSize = (size.value * 0.55f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Favicon for a site host. Loads the real icon from DuckDuckGo's icon service and
 * falls back to the letter tile when the site has no icon (or while it loads).
 * Results are cached in memory for the whole process.
 */
@Composable
fun SiteFavicon(host: String, letter: String, size: Dp = 22.dp) {
    val bitmap by produceState(initialValue = FaviconCache.peek(host), key1 = host) {
        value = FaviconCache.load(host)
    }
    val bmp = bitmap
    if (bmp == null) {
        FaviconTile(letter, size)
        return
    }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Favicon tile that only uses the in-memory cache — no per-row network work. */
@Composable
fun CachedFavicon(host: String, letter: String, size: Dp = 22.dp) {
    val bitmap = FaviconCache.peek(host)
    if (bitmap == null) {
        FaviconTile(letter, size)
        return
    }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Dominant saturated color of the site's favicon (or null). */
@Composable
fun faviconAccent(host: String): Color? {
    val color by produceState(initialValue = FaviconCache.peekColor(host), key1 = host) {
        value = FaviconCache.loadColor(host)
    }
    return color
}

/** Initialise the persistent favicon cache once, at app start. */
fun initFaviconCache(context: android.content.Context) {
    FaviconCache.init(context)
}

/**
 * Preloads favicons without flooding the network: hosts are de-duplicated,
 * capped, and the actual fetches are bounded by a semaphore inside the cache.
 */
fun prewarmFaviconCache(hosts: List<String>) {
    val keys = hosts.map { FaviconCache.keyOf(it) }.filter { it.isNotBlank() }.distinct().take(40)
    if (keys.isEmpty()) return
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    keys.forEach { host ->
        scope.launch { FaviconCache.load(host) }
    }
}

private object FaviconCache {
    private val lock = Any()
    private val icons = HashMap<String, Pair<Bitmap?, Color?>?>()
    private val inflight = HashMap<String, CompletableDeferred<Pair<Bitmap?, Color?>?>>()
    /** At most four concurrent icon requests, so long history lists stay light. */
    private val network = Semaphore(4)
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    internal fun keyOf(host: String): String =
        host.trim().lowercase().removePrefix("www.")

    private fun key(host: String): String = keyOf(host)

    fun peek(host: String): Bitmap? = synchronized(lock) { icons[key(host)]?.first }

    fun peekColor(host: String): Color? = synchronized(lock) { icons[key(host)]?.second }

    suspend fun load(host: String): Bitmap? = loadEntry(host)?.first

    suspend fun loadColor(host: String): Color? = loadEntry(host)?.second

    private suspend fun loadEntry(host: String): Pair<Bitmap?, Color?>? {
        val key = key(host)
        if (key.isBlank() || key == "new tab" || key == "home") {
            val entry = Pair(null as Bitmap?, null as Color?)
            synchronized(lock) { icons[key] = entry }
            return entry
        }
        synchronized(lock) {
            icons[key]?.let { return it }
        }
        // One fetch per host, even when many rows ask at the same time.
        val deferred: CompletableDeferred<Pair<Bitmap?, Color?>?>
        val owner: Boolean
        synchronized(lock) {
            val existing = inflight[key]
            if (existing != null) {
                deferred = existing
                owner = false
            } else {
                deferred = CompletableDeferred()
                inflight[key] = deferred
                owner = true
            }
        }
        if (!owner) return deferred.await()
        val result = try {
            loadFromDiskOrNetwork(key)
        } finally {
            synchronized(lock) { inflight.remove(key) }
        }
        synchronized(lock) { icons[key] = result }
        deferred.complete(result)
        return result
    }

    private suspend fun loadFromDiskOrNetwork(key: String): Pair<Bitmap?, Color?>? {
        val ctx = appContext
        if (ctx != null) {
            val file = com.example.geckobrowser.BrowserFiles.faviconFile(ctx, key)
            val disk = withContext(Dispatchers.IO) {
                if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
            }
            if (disk != null) return Pair(disk, dominantSaturatedColor(disk))
        }
        val bitmap = network.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val conn = URL("https://icons.duckduckgo.com/ip3/$key.ico")
                        .openConnection() as HttpURLConnection
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) horiZen")
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } catch (_: Throwable) {
                    null
                }
            }
        }
        if (bitmap != null && ctx != null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    FileOutputStream(com.example.geckobrowser.BrowserFiles.faviconFile(ctx, key)).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        }
        return Pair(bitmap, bitmap?.let { dominantSaturatedColor(it) })
    }

    /** Mean saturation-filtered color of the icon, or null when too pale/desaturated. */
    private fun dominantSaturatedColor(bmp: Bitmap): Color? {
        val width = bmp.width.coerceAtMost(96)
        val height = bmp.height.coerceAtMost(96)
        if (width <= 0 || height <= 0) return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        val stepX = (bmp.width / width).coerceAtLeast(1)
        val stepY = (bmp.height / height).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val px = bmp.getPixel(x, y)
                val alpha = px ushr 24
                if (alpha >= 128) {
                    val pr = (px shr 16) and 0xFF
                    val pg = (px shr 8) and 0xFF
                    val pb = px and 0xFF
                    r += pr; g += pg; b += pb; n++
                }
                x += stepX
            }
            y += stepY
        }
        if (n < 16) return null
        val meanR = (r / n).toInt(); val meanG = (g / n).toInt(); val meanB = (b / n).toInt()
        val maxC = maxOf(meanR, meanG, meanB); val minC = minOf(meanR, meanG, meanB)
        val saturation = if (maxC == 0) 0f else (maxC - minC).toFloat() / maxC
        val luma = 0.2126f * meanR + 0.7152f * meanG + 0.0722f * meanB
        if (saturation < 0.18f) return null          // gray/washed icon: fall back to accent
        if (luma > 215f || luma < 25f) return null   // too pale or too dark to read as an accent
        return Color(0xFF000000L or ((meanR.toLong() shl 16) or (meanG.toLong() shl 8) or meanB.toLong()))
    }
}


@Composable
fun PillAvatar(text: String = "M", size: Dp = 26.dp) {
    val c = LocalArc.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (c.dark) Color(0xFF2E363B) else Color(0xFFE4E4E1)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.accent, fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.SemiBold)
    }
}
