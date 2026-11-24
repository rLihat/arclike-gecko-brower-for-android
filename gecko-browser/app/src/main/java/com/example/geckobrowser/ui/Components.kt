package com.example.geckobrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.asComposePath
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
import kotlinx.coroutines.flow.collect
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
    data.map { d ->
        // A single malformed glyph must never abort app startup. Bad paths become
        // empty (invisible) icons; every other icon in the pack still renders.
        runCatching { PathParser.createPathFromPathData(d).asComposePath() }
            .getOrElse { Path() }
    }

private val IcoPlus: List<Path> by lazy { lucide("M 5 12 h 14", "M 12 5 v 14") }
private val IcoExpand: List<Path> by lazy { lucide("M 8 3 H 5 a 2 2 0 0 0 -2 2 v 3", "M 21 8 V 5 a 2 2 0 0 0 -2 -2 h -3", "M 3 16 v 3 a 2 2 0 0 0 2 2 h 3", "M 16 21 h 3 a 2 2 0 0 0 2 -2 v -3") }
private val IcoChevronUp: List<Path> by lazy { lucide("m 18 15 -6 -6 -6 6") }
private val IcoArrowUp: List<Path> by lazy { lucide("m 5 12 7 -7 7 7", "M 12 19 V 5") }
private val IcoChevronLeft: List<Path> by lazy { lucide("m 15 18 -6 -6 6 -6") }
private val IcoChevronRight: List<Path> by lazy { lucide("m 9 18 6 -6 -6 -6") }
private val IcoChevronDown: List<Path> by lazy { lucide("m 6 9 6 6 6 -6") }
private val IcoClose: List<Path> by lazy { lucide("M 18 6 6 18", "m 6 6 12 12") }
private val IcoBack: List<Path> by lazy { lucide("m 12 19 -7 -7 7 -7", "M 19 12 H 5") }
private val IcoForward: List<Path> by lazy { lucide("M 5 12 h 14", "m 12 5 7 7 -7 7") }
private val IcoRefresh: List<Path> by lazy { lucide("M 21 12 a 9 9 0 1 1 -9 -9 c 2.52 0 4.93 1 6.74 2.74 L 21 8", "M 21 3 v 5 h -5") }
private val IcoCopy: List<Path> by lazy { lucide("M 10 8 H 20 A 2 2 0 0 1 22 10 V 20 A 2 2 0 0 1 20 22 H 10 A 2 2 0 0 1 8 20 V 10 A 2 2 0 0 1 10 8 Z", "M 4 16 c -1.1 0 -2 -.9 -2 -2 V 4 c 0 -1.1 .9 -2 2 -2 h 10 c 1.1 0 2 .9 2 2") }
private val IcoGlobe: List<Path> by lazy { lucide("M 2 12 C 2 6.47715 6.47715 2 12 2 C 17.52285 2 22 6.47715 22 12 C 22 17.52285 17.52285 22 12 22 C 6.47715 22 2 17.52285 2 12 Z", "M 12 2 a 14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0 -20", "M 2 12 h 20") }
private val IcoTabs: List<Path> by lazy { lucide("M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z", "M 3 9 h 18") }
private val IcoShare: List<Path> by lazy { lucide("M 12 2 v 13", "m 16 6 -4 -4 -4 4", "M 4 12 v 8 a 2 2 0 0 0 2 2 h 12 a 2 2 0 0 0 2 -2 v -8") }
private val IcoTranslate: List<Path> by lazy { lucide("m 5 8 6 6", "m 4 14 6 -6 2 -3", "M 2 5 h 12", "M 7 2 h 1", "m 22 22 -5 -10 -5 10", "M 14 18 h 6") }
private val IcoFind: List<Path> by lazy { lucide("M 3 7 V 5 a 2 2 0 0 1 2 -2 h 2", "M 17 3 h 2 a 2 2 0 0 1 2 2 v 2", "M 21 17 v 2 a 2 2 0 0 1 -2 2 h -2", "M 7 21 H 5 a 2 2 0 0 1 -2 -2 v -2", "M 9 12 C 9 10.34315 10.34315 9 12 9 C 13.65685 9 15 10.34315 15 12 C 15 13.65685 13.65685 15 12 15 C 10.34315 15 9 13.65685 9 12 Z", "m 16 16 -1.9 -1.9") }
private val IcoContrast: List<Path> by lazy { lucide("M 2 12 C 2 6.47715 6.47715 2 12 2 C 17.52285 2 22 6.47715 22 12 C 22 17.52285 17.52285 22 12 22 C 6.47715 22 2 17.52285 2 12 Z", "M 12 18 a 6 6 0 0 0 0 -12 v 12 z") }
private val IcoPuzzle: List<Path> by lazy { lucide("M 15.39 4.39 a 1 1 0 0 0 1.68 -.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0 -.474 1.68 l 1.683 1.682 a 2.414 2.414 0 0 1 0 3.414 L 19.61 15.39 a 1 1 0 0 1 -1.68 -.474 2.5 2.5 0 1 0 -3.014 3.015 1 1 0 0 1 .474 1.68 l -1.683 1.682 a 2.414 2.414 0 0 1 -3.414 0 L 8.61 19.61 a 1 1 0 0 0 -1.68 .474 2.5 2.5 0 1 1 -3.014 -3.015 1 1 0 0 0 .474 -1.68 l -1.683 -1.682 a 2.414 2.414 0 0 1 0 -3.414 L 4.39 8.61 a 1 1 0 0 1 1.68 .474 2.5 2.5 0 1 0 3.014 -3.015 1 1 0 0 1 -.474 -1.68 l 1.683 -1.682 a 2.414 2.414 0 0 1 3.414 0 z") }
private val IcoMoon: List<Path> by lazy { lucide("M 20.985 12.486 a 9 9 0 1 1 -9.473 -9.472 c .405 -.022 .617 .46 .402 .803 a 6 6 0 0 0 8.268 8.268 c .344 -.215 .825 -.004 .803 .401") }
private val IcoTextSize: List<Path> by lazy { lucide("M 12 4 v 16", "M 4 7 V 5 a 1 1 0 0 1 1 -1 h 14 a 1 1 0 0 1 1 1 v 2", "M 9 20 h 6") }
private val IcoDownload: List<Path> by lazy { lucide("M 12 15 V 3", "M 21 15 v 4 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 v -4", "m 7 10 5 5 5 -5") }
private val IcoClock: List<Path> by lazy { lucide("M 2 12 C 2 6.47715 6.47715 2 12 2 C 17.52285 2 22 6.47715 22 12 C 22 17.52285 17.52285 22 12 22 C 6.47715 22 2 17.52285 2 12 Z", "M 12 6 v 6 l 4 2") }
private val IcoGrid: List<Path> by lazy { lucide("M 15 3 v 18", "M 3 12 h 18", "M 9 3 v 18", "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z") }
private val IcoGear: List<Path> by lazy { lucide("M 11 10.27 7 3.34", "m 11 13.73 -4 6.93", "M 12 22 v -2", "M 12 2 v 2", "M 14 12 h 8", "m 17 20.66 -1 -1.73", "m 17 3.34 -1 1.73", "M 2 12 h 2", "m 20.66 17 -1.73 -1", "m 20.66 7 -1.73 1", "m 3.34 17 1.73 -1", "m 3.34 7 1.73 1", "M 10 12 C 10 10.89543 10.89543 10 12 10 C 13.10457 10 14 10.89543 14 12 C 14 13.10457 13.10457 14 12 14 C 10.89543 14 10 13.10457 10 12 Z", "M 4 12 C 4 7.58172 7.58172 4 12 4 C 16.41828 4 20 7.58172 20 12 C 20 16.41828 16.41828 20 12 20 C 7.58172 20 4 16.41828 4 12 Z") }
private val IcoCheck: List<Path> by lazy { lucide("M 20 6 9 17 l -5 -5") }
private val IcoLink: List<Path> by lazy { lucide("M 10 13 a 5 5 0 0 0 7.54 .54 l 3 -3 a 5 5 0 0 0 -7.07 -7.07 l -1.72 1.71", "M 14 11 a 5 5 0 0 0 -7.54 -.54 l -3 3 a 5 5 0 0 0 7.07 7.07 l 1.71 -1.71") }
private val IcoTrash: List<Path> by lazy { lucide("M 10 11 v 6", "M 14 11 v 6", "M 19 6 v 14 a 2 2 0 0 1 -2 2 H 7 a 2 2 0 0 1 -2 -2 V 6", "M 3 6 h 18", "M 8 6 V 4 a 2 2 0 0 1 2 -2 h 4 a 2 2 0 0 1 2 2 v 2") }
private val IcoLock: List<Path> by lazy { lucide("M 5 11 H 19 A 2 2 0 0 1 21 13 V 20 A 2 2 0 0 1 19 22 H 5 A 2 2 0 0 1 3 20 V 13 A 2 2 0 0 1 5 11 Z", "M 7 11 V 7 a 5 5 0 0 1 10 0 v 4") }
private val IcoPerson: List<Path> by lazy { lucide("M 19 21 v -2 a 4 4 0 0 0 -4 -4 H 9 a 4 4 0 0 0 -4 4 v 2", "M 8 7 C 8 4.79086 9.79086 3 12 3 C 14.20914 3 16 4.79086 16 7 C 16 9.20914 14.20914 11 12 11 C 9.79086 11 8 9.20914 8 7 Z") }
private val IcoPin: List<Path> by lazy { lucide("M 12 17 v 5", "M 9 10.76 a 2 2 0 0 1 -1.11 1.79 l -1.78 .9 A 2 2 0 0 0 5 15.24 V 16 a 1 1 0 0 0 1 1 h 12 a 1 1 0 0 0 1 -1 v -.76 a 2 2 0 0 0 -1.11 -1.79 l -1.78 -.9 A 2 2 0 0 1 15 10.76 V 7 a 1 1 0 0 1 1 -1 2 2 0 0 0 0 -4 H 8 a 2 2 0 0 0 0 4 1 1 0 0 1 1 1 z") }
private val IcoOpen: List<Path> by lazy { lucide("M 15 3 h 6 v 6", "M 10 14 21 3", "M 18 13 v 6 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 V 8 a 2 2 0 0 1 2 -2 h 6") }
private val IcoImage: List<Path> by lazy { lucide("M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z", "M 7 9 C 7 7.89543 7.89543 7 9 7 C 10.10457 7 11 7.89543 11 9 C 11 10.10457 10.10457 11 9 11 C 7.89543 11 7 10.10457 7 9 Z", "m 21 15 -3.086 -3.086 a 2 2 0 0 0 -2.828 0 L 6 21") }
private val IcoMask: List<Path> by lazy { lucide("M 15 10 v 1", "M 7.528 20.472 a 1.6 1.6 0 0 1 2.277 0 l 1.057 1.056 a 1.6 1.6 0 0 0 2.276 0 l 1.057 -1.056 a 1.6 1.6 0 0 1 2.277 0 l 1.114 1.114 a 1.4 1.4 0 0 0 2.414 -1 V 10 a 8 8 0 0 0 -16 0 v 10.586 a 1.4 1.4 0 0 0 2.414 1 z", "M 9 10 v 1") }
private val IcoPause: List<Path> by lazy { lucide("M 15 3 H 18 A 1 1 0 0 1 19 4 V 20 A 1 1 0 0 1 18 21 H 15 A 1 1 0 0 1 14 20 V 4 A 1 1 0 0 1 15 3 Z", "M 6 3 H 9 A 1 1 0 0 1 10 4 V 20 A 1 1 0 0 1 9 21 H 6 A 1 1 0 0 1 5 20 V 4 A 1 1 0 0 1 6 3 Z") }
private val IcoPlay: List<Path> by lazy { lucide("M 5 5 a 2 2 0 0 1 3.008 -1.728 l 11.997 6.998 a 2 2 0 0 1 .003 3.458 l -12 7 A 2 2 0 0 1 5 19 z") }
private val IcoPinOff: List<Path> by lazy { lucide("M 12 17 v 5", "M 15 9.34 V 7 a 1 1 0 0 1 1 -1 2 2 0 0 0 0 -4 H 7.89", "m 2 2 20 20", "M 9 9 v 1.76 a 2 2 0 0 1 -1.11 1.79 l -1.78 .9 A 2 2 0 0 0 5 15.24 V 16 a 1 1 0 0 0 1 1 h 11") }
private val IcoLens: List<Path> by lazy { lucide("M 15.725 7.296 h .01", "M 6 12 a 6 6 0 0 1 6 -6", "M 2 12 C 2 6.47715 6.47715 2 12 2 C 17.52285 2 22 6.47715 22 12 C 22 17.52285 17.52285 22 12 22 C 6.47715 22 2 17.52285 2 12 Z") }
private val IcoZoomIn: List<Path> by lazy { lucide("M 3 11 C 3 6.58172 6.58172 3 11 3 C 15.41828 3 19 6.58172 19 11 C 19 15.41828 15.41828 19 11 19 C 6.58172 19 3 15.41828 3 11 Z", "M 21 21 L 16.65 16.65", "M 11 8 L 11 14", "M 8 11 L 14 11") }
private val IcoZoomOut: List<Path> by lazy { lucide("M 3 11 C 3 6.58172 6.58172 3 11 3 C 15.41828 3 19 6.58172 19 11 C 19 15.41828 15.41828 19 11 19 C 6.58172 19 3 15.41828 3 11 Z", "M 21 21 L 16.65 16.65", "M 8 11 L 14 11") }
private val IcoUserCog: List<Path> by lazy { lucide("M 10 15 H 6 a 4 4 0 0 0 -4 4 v 2", "m 14.305 16.53 .923 -.382", "m 15.228 13.852 -.923 -.383", "m 16.852 12.228 -.383 -.923", "m 16.852 17.772 -.383 .924", "m 19.148 12.228 .383 -.923", "m 19.53 18.696 -.382 -.924", "m 20.772 13.852 .924 -.383", "m 20.772 16.148 .924 .383", "M 15 15 C 15 13.34315 16.34315 12 18 12 C 19.65685 12 21 13.34315 21 15 C 21 16.65685 19.65685 18 18 18 C 16.34315 18 15 16.65685 15 15 Z", "M 5 7 C 5 4.79086 6.79086 3 9 3 C 11.20914 3 13 4.79086 13 7 C 13 9.20914 11.20914 11 9 11 C 6.79086 11 5 9.20914 5 7 Z") }
private val IcoDiagramFlow: List<Path> by lazy { lucide("M 8 12 h .5 a 3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 0 3.5 3.5 h .5", "M 8.5 12 A 3.5 3.5 0 0 0 12 8.5 3.5 3.5 0 0 1 15.5 5 h .5", "M 17 16 H 21 A 1 1 0 0 1 22 17 V 21 A 1 1 0 0 1 21 22 H 17 A 1 1 0 0 1 16 21 V 17 A 1 1 0 0 1 17 16 Z", "M 17 2 H 21 A 1 1 0 0 1 22 3 V 7 A 1 1 0 0 1 21 8 H 17 A 1 1 0 0 1 16 7 V 3 A 1 1 0 0 1 17 2 Z", "M 3 9 H 7 A 1 1 0 0 1 8 10 V 14 A 1 1 0 0 1 7 15 H 3 A 1 1 0 0 1 2 14 V 10 A 1 1 0 0 1 3 9 Z") }
private val IcoGhost: List<Path> by lazy { lucide("M 15 10 v 1", "M 7.528 20.472 a 1.6 1.6 0 0 1 2.277 0 l 1.057 1.056 a 1.6 1.6 0 0 0 2.276 0 l 1.057 -1.056 a 1.6 1.6 0 0 1 2.277 0 l 1.114 1.114 a 1.4 1.4 0 0 0 2.414 -1 V 10 a 8 8 0 0 0 -16 0 v 10.586 a 1.4 1.4 0 0 0 2.414 1 z", "M 9 10 v 1") }
private val IcoRotateCwFadingClock: List<Path> by lazy { lucide("M 12 3 a 9.75 9.75 0 0 1 6.74 2.74", "M 18.74 5.74 21 8", "M 21 8 V 3", "M 7.5 19.794 c -6 -3.464 -6 -12.124 0 -15.588", "M 7.5 4.206 A 9 9 0 0 1 12 3", "M 12 7 v 5 l 4 2", "M 14 20.775 A 9 9 0 0 1 12 21", "M 19 17.656 a 9 9 0 0 1 -1.5 1.456", "M 21 12 a 9 9 0 0 1 -.228 2", "M 21 8 h -5") }
private val IcoCircleFadingArrowUp: List<Path> by lazy { lucide("M 12 2 a 10 10 0 0 1 7.38 16.75", "m 16 12 -4 -4 -4 4", "M 12 16 V 8", "M 2.5 8.875 a 10 10 0 0 0 -.5 3", "M 2.83 16 a 10 10 0 0 0 2.43 3.4", "M 4.636 5.235 a 10 10 0 0 1 .891 -.857", "M 8.644 21.42 a 10 10 0 0 0 7.631 -.38") }
private val IcoLightbulb: List<Path> by lazy { lucide("M 15 14 c .2 -1 .7 -1.7 1.5 -2.5 1 -.9 1.5 -2.2 1.5 -3.5 A 6 6 0 0 0 6 8 c 0 1 .2 2.2 1.5 3.5 .7 .7 1.3 1.5 1.5 2.5", "M 9 18 h 6", "M 10 22 h 4") }
private val IcoOpacity: List<Path> by lazy { lucide("M 12 3 h .01", "M 12 9 h .01", "M 15 6 h .01", "M 18 3 h .01", "M 21 6 v 13 a 2 2 0 0 1 -2 2 H 6", "M 3 12 h .01", "M 3 18 h .01", "M 3 6 h .01", "M 6 15 h .01", "M 6 3 h .01", "M 6 9 h .01", "M 9 12 h .01", "M 9 6 h .01") }
private val IcoLayersMinus: List<Path> by lazy { lucide("M 12.83 2.18 a 2 2 0 0 0 -1.66 0 L 2.6 6.08 a 1 1 0 0 0 0 1.83 l 8.58 3.91 a 2 2 0 0 0 .83 .18 2 2 0 0 0 .83 -.18 l 8.58 -3.9 a 1 1 0 0 0 0 -1.832 z", "M 16 17 h 6", "M 2.003 11.995 a 1 1 0 0 0 .597 .915 l 8.58 3.91 a 2 2 0 0 0 .83 .18", "M 2.003 16.995 a 1 1 0 0 0 .597 .915 l 8.58 3.91 a 2 2 0 0 0 .83 .18 2 2 0 0 0 .83 -.18 l 2.11 -.96", "M 22.018 12.004 a 1 1 0 0 1 -.598 .916 l -.177 .08") }
private val IcoBadgeInfo: List<Path> by lazy { lucide("M 3.85 8.62 a 4 4 0 0 1 4.78 -4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1 -4.77 4.78 4 4 0 0 1 -6.75 0 4 4 0 0 1 -4.78 -4.77 4 4 0 0 1 0 -6.76 Z", "M 12 16 L 12 12", "M 12 8 L 12.01 8") }
private val IcoBadgeCheck: List<Path> by lazy { lucide("M 3.85 8.62 a 4 4 0 0 1 4.78 -4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1 -4.77 4.78 4 4 0 0 1 -6.75 0 4 4 0 0 1 -4.78 -4.77 4 4 0 0 1 0 -6.76 Z", "m 16 9 -5.5 5.5 L 8 12") }
private val IcoMic: List<Path> by lazy { lucide("M 12 19 v 3", "M 19 10 v 2 a 7 7 0 0 1 -14 0 v -2", "M 12 2 H 12 A 3 3 0 0 1 15 5 V 12 A 3 3 0 0 1 12 15 H 12 A 3 3 0 0 1 9 12 V 5 A 3 3 0 0 1 12 2 Z") }
private val IcoPanelTop: List<Path> by lazy { lucide("M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z", "M 3 9 h 18") }
private val IcoStretchVertical: List<Path> by lazy { lucide("M 6 2 H 8 A 2 2 0 0 1 10 4 V 20 A 2 2 0 0 1 8 22 H 6 A 2 2 0 0 1 4 20 V 4 A 2 2 0 0 1 6 2 Z", "M 16 2 H 18 A 2 2 0 0 1 20 4 V 20 A 2 2 0 0 1 18 22 H 16 A 2 2 0 0 1 14 20 V 4 A 2 2 0 0 1 16 2 Z") }
private val IcoGrid3x2: List<Path> by lazy { lucide("M 15 3 v 18", "M 3 12 h 18", "M 9 3 v 18", "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z") }
private val IcoPalette: List<Path> by lazy { lucide("M 12 22 a 1 1 0 0 1 0 -20 10 9 0 0 1 10 9 5 5 0 0 1 -5 5 h -2.25 a 1.75 1.75 0 0 0 -1.4 2.8 l .3 .4 a 1.75 1.75 0 0 1 -1.4 2.8 z", "M 13 6.5 C 13 6.22386 13.22386 6 13.5 6 C 13.77614 6 14 6.22386 14 6.5 C 14 6.77614 13.77614 7 13.5 7 C 13.22386 7 13 6.77614 13 6.5 Z", "M 17 10.5 C 17 10.22386 17.22386 10 17.5 10 C 17.77614 10 18 10.22386 18 10.5 C 18 10.77614 17.77614 11 17.5 11 C 17.22386 11 17 10.77614 17 10.5 Z", "M 6 12.5 C 6 12.22386 6.22386 12 6.5 12 C 6.77614 12 7 12.22386 7 12.5 C 7 12.77614 6.77614 13 6.5 13 C 6.22386 13 6 12.77614 6 12.5 Z", "M 8 7.5 C 8 7.22386 8.22386 7 8.5 7 C 8.77614 7 9 7.22386 9 7.5 C 9 7.77614 8.77614 8 8.5 8 C 8.22386 8 8 7.77614 8 7.5 Z") }
private val IcoVibrate: List<Path> by lazy { lucide("m 2 8 2 2 -2 2 2 2 -2 2", "m 22 8 -2 2 2 2 -2 2 2 2", "M 9 5 H 15 A 1 1 0 0 1 16 6 V 18 A 1 1 0 0 1 15 19 H 9 A 1 1 0 0 1 8 18 V 6 A 1 1 0 0 1 9 5 Z") }
private val IcoShieldCheck: List<Path> by lazy { lucide("M 20 13 c 0 5 -3.5 7.5 -7.66 8.95 a 1 1 0 0 1 -.67 -.01 C 7.5 20.5 4 18 4 13 V 6 a 1 1 0 0 1 1 -1 c 2 0 4.5 -1.2 6.24 -2.72 a 1.17 1.17 0 0 1 1.52 0 C 14.51 3.81 17 5 19 5 a 1 1 0 0 1 1 1 z", "m 9 12 2 2 4 -4") }
private val IcoShield: List<Path> by lazy { lucide("M 20 13 c 0 5 -3.5 7.5 -7.66 8.95 a 1 1 0 0 1 -.67 -.01 C 7.5 20.5 4 18 4 13 V 6 a 1 1 0 0 1 1 -1 c 2 0 4.5 -1.2 6.24 -2.72 a 1.17 1.17 0 0 1 1.52 0 C 14.51 3.81 17 5 19 5 a 1 1 0 0 1 1 1 z") }
private val IcoSparkles: List<Path> by lazy { lucide("M 11.017 2.814 a 1 1 0 0 1 1.966 0 l 1.051 5.558 a 2 2 0 0 0 1.594 1.594 l 5.558 1.051 a 1 1 0 0 1 0 1.966 l -5.558 1.051 a 2 2 0 0 0 -1.594 1.594 l -1.051 5.558 a 1 1 0 0 1 -1.966 0 l -1.051 -5.558 a 2 2 0 0 0 -1.594 -1.594 l -5.558 -1.051 a 1 1 0 0 1 0 -1.966 l 5.558 -1.051 a 2 2 0 0 0 1.594 -1.594 z", "M 20 2 v 4", "M 22 4 h -4", "M 2 20 C 2 18.89543 2.89543 18 4 18 C 5.10457 18 6 18.89543 6 20 C 6 21.10457 5.10457 22 4 22 C 2.89543 22 2 21.10457 2 20 Z") }
private val IcoInfo: List<Path> by lazy { lucide("M 2 12 C 2 6.47715 6.47715 2 12 2 C 17.52285 2 22 6.47715 22 12 C 22 17.52285 17.52285 22 12 22 C 6.47715 22 2 17.52285 2 12 Z", "M 12 16 v -4", "M 12 8 h .01") }
private val IcoFolder: List<Path> by lazy { lucide("M 20 20 a 2 2 0 0 0 2 -2 V 8 a 2 2 0 0 0 -2 -2 h -7.9 a 2 2 0 0 1 -1.69 -.9 L 9.6 3.9 A 2 2 0 0 0 7.93 3 H 4 a 2 2 0 0 0 -2 2 v 13 a 2 2 0 0 0 2 2 Z") }
private val IcoExternalLink: List<Path> by lazy { lucide("M 15 3 h 6 v 6", "M 10 14 21 3", "M 18 13 v 6 a 2 2 0 0 1 -2 2 H 5 a 2 2 0 0 1 -2 -2 V 8 a 2 2 0 0 1 2 -2 h 6") }
private val IcoChevronRightSmall: List<Path> by lazy { lucide("m 9 18 6 -6 -6 -6") }
private val IcoCamera: List<Path> by lazy { lucide("M 13.997 4 a 2 2 0 0 1 1.76 1.05 l .486 .9 A 2 2 0 0 0 18.003 7 H 20 a 2 2 0 0 1 2 2 v 9 a 2 2 0 0 1 -2 2 H 4 a 2 2 0 0 1 -2 -2 V 9 a 2 2 0 0 1 2 -2 h 1.997 a 2 2 0 0 0 1.759 -1.048 l .489 -.904 A 2 2 0 0 1 10.004 4 z", "M 9 13 C 9 11.34315 10.34315 10 12 10 C 13.65685 10 15 11.34315 15 13 C 15 14.65685 13.65685 16 12 16 C 10.34315 16 9 14.65685 9 13 Z") }
private val IcoBell: List<Path> by lazy { lucide("M 10.268 21 a 2 2 0 0 0 3.464 0", "M 3.262 15.326 A 1 1 0 0 0 4 17 h 16 a 1 1 0 0 0 .74 -1.673 C 19.41 13.956 18 12.499 18 8 A 6 6 0 0 0 6 8 c 0 4.499 -1.411 5.956 -2.738 7.326") }
private val IcoLanguages: List<Path> by lazy { lucide("m 5 8 6 6", "m 4 14 6 -6 2 -3", "M 2 5 h 12", "M 7 2 h 1", "m 22 22 -5 -10 -5 10", "M 14 18 h 6") }
private val IcoMapPin: List<Path> by lazy { lucide("M 20 10 c 0 4.993 -5.539 10.193 -7.399 11.799 a 1 1 0 0 1 -1.202 0 C 9.539 20.193 4 14.993 4 10 a 8 8 0 0 1 16 0", "M 9 10 C 9 8.34315 10.34315 7 12 7 C 13.65685 7 15 8.34315 15 10 C 15 11.65685 13.65685 13 12 13 C 10.34315 13 9 11.65685 9 10 Z") }
private val IcoCookie: List<Path> by lazy { lucide("M 11 17 h .01", "M 11.496 2 c .324 -.016 .558 .292 .529 .615 a 4 4 0 0 0 4.235 4.368 .713 .713 0 0 1 .758 .757 4 4 0 0 0 4.366 4.237 c .323 -.03 .63 .204 .614 .527 a 10 10 0 0 1 -2.915 6.566 A 1 1 0 1 1 4.93 4.918 10 10 0 0 1 11.496 2", "M 12 12 h .01", "M 16 16 h .01", "M 16 3 h .01", "M 21 4 h .01", "M 21 8 h .01", "M 7 14 h .01", "M 9 8 h .01") }
private val IcoEngine: List<Path> by lazy { lucide("M 10 3 h 6", "M 13 3 v 4", "M 2 10 v 6", "M 2 13 h 4", "M 6 16 a 2 2 0 0 0 2 2 h 1 a 2 2 0 0 1 1.6 .8 l .3 .4 a 2 2 0 0 0 1.6 .8 h 2.264 a 2 2 0 0 0 1.789 -1.106 l 1.67 -3.341 a 1 1 0 0 1 .895 -.553 H 21 a 1 1 0 0 0 1 -1 v -4 a 1 1 0 0 0 -1 -1 h -3.5 a 1 1 0 0 1 -.8 -.4 l -.9 -1.2 A 1 1 0 0 0 15 7 h -4 a 1 1 0 0 0 -.8 .4 l -.9 1.2 a 1 1 0 0 1 -.8 .4 H 7 a 1 1 0 0 0 -1 1 z") }
private val IcoPackage: List<Path> by lazy { lucide("M 11 21.73 a 2 2 0 0 0 2 0 l 7 -4 A 2 2 0 0 0 21 16 V 8 a 2 2 0 0 0 -1 -1.73 l -7 -4 a 2 2 0 0 0 -2 0 l -7 4 A 2 2 0 0 0 3 8 v 8 a 2 2 0 0 0 1 1.73 z", "M 12 22 V 12", "M 3.29 7 L 12 12 L 20.71 7", "m 7.5 4.27 9 5.15") }
private val IcoSquareCode: List<Path> by lazy { lucide("m 10 9 -3 3 3 3", "m 14 15 3 -3 -3 -3", "M 5 3 H 19 A 2 2 0 0 1 21 5 V 19 A 2 2 0 0 1 19 21 H 5 A 2 2 0 0 1 3 19 V 5 A 2 2 0 0 1 5 3 Z") }
private val IcoSearchCheck: List<Path> by lazy { lucide("m 8 11 2 2 4 -4", "M 3 11 C 3 6.58172 6.58172 3 11 3 C 15.41828 3 19 6.58172 19 11 C 19 15.41828 15.41828 19 11 19 C 6.58172 19 3 15.41828 3 11 Z", "m 21 21 -4.3 -4.3") }
private val IcoSearch: List<Path> by lazy { lucide("m 21 21 -4.34 -4.34", "M 3 11 C 3 6.58172 6.58172 3 11 3 C 15.41828 3 19 6.58172 19 11 C 19 15.41828 15.41828 19 11 19 C 6.58172 19 3 15.41828 3 11 Z") }
private val IcoCog: List<Path> by lazy { lucide("M 11 10.27 7 3.34", "m 11 13.73 -4 6.93", "M 12 22 v -2", "M 12 2 v 2", "M 14 12 h 8", "m 17 20.66 -1 -1.73", "m 17 3.34 -1 1.73", "M 2 12 h 2", "m 20.66 17 -1.73 -1", "m 20.66 7 -1.73 1", "m 3.34 17 1.73 -1", "m 3.34 7 1.73 1", "M 10 12 C 10 10.89543 10.89543 10 12 10 C 13.10457 10 14 10.89543 14 12 C 14 13.10457 13.10457 14 12 14 C 10.89543 14 10 13.10457 10 12 Z", "M 4 12 C 4 7.58172 7.58172 4 12 4 C 16.41828 4 20 7.58172 20 12 C 20 16.41828 16.41828 20 12 20 C 7.58172 20 4 16.41828 4 12 Z") }
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
    val mask = IcoMask
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
    val mapPin = IcoMapPin
    val cookie = IcoCookie
    val engine = IcoEngine
    val packageIcon = IcoPackage
    val squareCode = IcoSquareCode
    val searchCheck = IcoSearchCheck
    val search = IcoSearch
    val cog = IcoCog
}

/** The glass search field used by settings, history and downloads. */
@Composable
fun GlassSearchPill(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalArc.current
    Box(
        modifier
            .fillMaxWidth(0.7f)
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .background(c.navSurface)
            .then(rememberGlass(c.navSurface, fps = 20, maxEdge = 170, radius = 6)),
    ) {
        if (LocalFrost.current && LocalHazeState.current != null) GlassFill(dark = c.dark)
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Ico.search, c.muted, size = 18.dp, strokeWidth = 2f)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, color = c.muted, fontSize = 14.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
    val haptics = LocalHapticFeedback.current
    val threshold = LocalViewConfiguration.current.touchSlop
    val horizontalSlop = if (strictHorizontal) 36.dp.toPx() else threshold
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            // One gesture handler per button, the way Fenix does it: a single
            // detector so taps, long presses and swipes can't steal each other's events.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    if (down.isConsumed) return@awaitEachGesture
                    down.consume()
                    var dx = 0f
                    var dy = 0f
                    var longFired = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                        if (change.isConsumed) break
                        dx += change.delta.x
                        dy += change.delta.y
                        val barelyMoved = abs(dx) <= threshold && abs(dy) <= threshold
                        val longPressMs = ViewConfiguration.getLongPressTimeout().toLong()
                        if (!longFired && SystemClock.uptimeMillis() - down.uptimeMillis >= longPressMs && barelyMoved) {
                            longFired = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick?.invoke()
                            break
                        }
                        if (event.type == PointerEventType.Released) {
                            if (longFired) break
                            if (abs(dx) >= threshold || abs(dy) >= threshold) {
                                val ax = abs(dx)
                                val ay = abs(dy)
                                when {
                                    ay >= threshold && ay > ax * 1.25f && dy < 0f -> onSwipeUp?.invoke()
                                    ay >= threshold && ay > ax * 1.25f && dy > 0f -> onSwipeDown?.invoke()
                                    ax >= horizontalSlop && ax > ay * (if (strictHorizontal) 2.2f else 1.25f) && dx < 0f -> onSwipeLeft?.invoke()
                                    ax >= horizontalSlop && ax > ay * (if (strictHorizontal) 2.2f else 1.25f) && dx > 0f -> onSwipeRight?.invoke()
                                }
                            } else {
                                onClick()
                            }
                            break
                        }
                    }
                }
            },
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
