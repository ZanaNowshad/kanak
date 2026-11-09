package com.example.kanak.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay

private val SplashNavyDark = Color(0xFFF8FAFF)
private val SplashNavyMid = Color(0xFFFFFFFF)
private val SplashGold = Color(0xFF2563FF)
private val SplashGoldBright = Color(0xFF2563FF)
private val SplashCyan = Color(0xFF38BDF8)
private val BahrainCrimson = Color(0xFFE1001A)

/**
 * Enterprise Splash & Startup Loader Animation for KANAK.
 * Delivers a cinematic, branded startup sequence with golden particle aura,
 * rotating dual orbital rings, and smooth boot phase milestones.
 */
@Composable
fun KanakStartupLoader(
    onStartupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPhaseText by remember { mutableStateOf("Initializing KANAK Engine...") }

    // Smooth milestone advancement
    LaunchedEffect(Unit) {
        val phases = listOf(
            0.20f to "Verifying secure local SQLite cache...",
            0.50f to "Mounting immutable Bahrain VAT ledger...",
            0.80f to "Calibrating 3-decimal BHD financial engine...",
            1.00f to "Ready • Kingdom of Bahrain"
        )
        for ((targetProgress, text) in phases) {
            currentPhaseText = text
            val startProg = progress
            val steps = 8
            val diff = (targetProgress - startProg) / steps
            for (i in 1..steps) {
                progress += diff
                delay(6)
            }
            delay(30)
        }
        delay(60)
        onStartupComplete()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "StartupLoaderMotion")

    // Rotation for outer golden orbital ring
    val orbitalRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitalRing"
    )

    // Breathing pulse for emblem
    val emblemScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EmblemPulse"
    )

    // Halo glow expansion
    val haloGlow by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloGlow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF13283F),
                        SplashNavyMid,
                        SplashNavyDark
                    ),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // =========================================================================
            // CENTRAL BRAND EMBLEM WITH PULSING GOLDEN HALO & DUAL ORBITAL RINGS
            // =========================================================================
            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulsating Halo
                Box(
                    modifier = Modifier
                        .size(145.dp)
                        .scale(emblemScale)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    SplashGold.copy(alpha = haloGlow * 0.7f),
                                    SplashCyan.copy(alpha = haloGlow * 0.25f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Primary Golden Orbital Arc
                CircularProgressIndicator(
                    progress = { 0.75f },
                    modifier = Modifier
                        .size(156.dp)
                        .rotate(orbitalRotation),
                    color = SplashGold,
                    trackColor = SplashGold.copy(alpha = 0.12f),
                    strokeWidth = 3.5.dp,
                    strokeCap = StrokeCap.Round
                )

                // Secondary Fast Counter-Rotating Cyan Arc
                CircularProgressIndicator(
                    progress = { 0.45f },
                    modifier = Modifier
                        .size(134.dp)
                        .rotate(-orbitalRotation * 1.5f),
                    color = SplashCyan.copy(alpha = 0.85f),
                    trackColor = Color.Transparent,
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round
                )

                // Central Rounded Badge with High-Res App Icon
                Surface(
                    modifier = Modifier
                        .size(105.dp)
                        .scale(emblemScale)
                        .shadow(elevation = 20.dp, shape = CircleShape, ambientColor = SplashGold),
                    shape = CircleShape,
                    color = SplashNavyDark,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 2.5.dp,
                        brush = Brush.linearGradient(
                            listOf(SplashGoldBright, SplashGold, SplashCyan)
                        )
                    )
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "KANAK Master Brand Emblem",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // =========================================================================
            // TYPOGRAPHY & TAGLINE
            // =========================================================================
            Text(
                text = "KANAK",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 8.sp,
                    color = Color(0xFF0B0F1A)
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                // Bahrain National Red Pill
                Surface(
                    color = BahrainCrimson,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        text = "BAHRAIN",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0B0F1A),
                        letterSpacing = 1.2.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "FMCG WHOLESALE • VAN SALES & POS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = SplashGoldBright
                    )
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // =========================================================================
            // PROGRESS BAR & PHASE MILESTONES
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxWidth(0.82f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SplashGold,
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = currentPhaseText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Footer version info
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "v1.0.0 Enterprise • 3-Decimal BHD • 10% NBR VAT",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF475569),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}
