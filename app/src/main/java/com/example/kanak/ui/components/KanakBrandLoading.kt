package com.example.kanak.ui.components

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.*

// Premium Enterprise Branding Palette
private val BrandGold = Color(0xFFD4AF37)
private val BrandGoldLight = Color(0xFFFFDF79)
private val BrandNavyDark = Color(0xFF091422)
private val BrandNavySurface = Color(0xFF0D1B2A)
private val BrandAccentCyan = Color(0xFF38BDF8)

/**
 * Fullscreen or embedded enterprise loading overlay with animated KANAK branding,
 * pulsating golden aura, dual orbital spinners, and live status messaging.
 */
@Composable
fun KanakBrandLoadingOverlay(
    modifier: Modifier = Modifier,
    message: String = "Synchronizing with Authoritative Cloud...",
    subMessage: String = "Enterprise FMCG Wholesale Platform • Kingdom of Bahrain"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "KanakBrandLoading")

    // Rotation animation for orbital ring
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbitalRotation"
    )

    // Breathing pulse for emblem
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "EmblemPulse"
    )

    // Glowing halo alpha
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.70f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "HaloGlow"
    )

    // Animated dots for status progress
    val dotCount by infiniteTransition.animateValue(
        initialValue = 1,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "StatusDots"
    )
    val animatedDots = ".".repeat(dotCount.coerceIn(1, 3))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        BrandNavyDark.copy(alpha = 0.96f),
                        BrandNavySurface.copy(alpha = 0.98f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(0.88f)
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(24.dp), ambientColor = BrandGold),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BrandNavySurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BrandGold.copy(alpha = 0.6f),
                        BrandAccentCyan.copy(alpha = 0.3f),
                        BrandGold.copy(alpha = 0.2f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Central Glowing Brand Emblem with Orbital Ring
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsating Golden Aura Glow
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        BrandGold.copy(alpha = haloAlpha),
                                        BrandAccentCyan.copy(alpha = haloAlpha * 0.4f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Rotating Dual-Color Orbital Spinner
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(116.dp)
                            .rotate(rotation),
                        strokeWidth = 3.5.dp,
                        color = BrandGold,
                        trackColor = BrandGold.copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round
                    )

                    // Secondary Fast Counter-Rotating Cyan Arc
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(98.dp)
                            .rotate(-rotation * 1.4f),
                        strokeWidth = 2.dp,
                        color = BrandAccentCyan.copy(alpha = 0.8f),
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )

                    // Brand App Icon Centerpiece
                    Surface(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(pulseScale),
                        shape = CircleShape,
                        color = BrandNavyDark,
                        shadowElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = 2.dp,
                            brush = Brush.linearGradient(
                                listOf(BrandGoldLight, BrandGold, BrandAccentCyan)
                            )
                        )
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.kanak_app_icon_1794073715264),
                            contentDescription = "KANAK Brand Emblem",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                                .clip(CircleShape)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Brand Name & Typography
                Text(
                    text = "KANAK",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 5.sp,
                        color = BrandGoldLight
                    )
                )

                Text(
                    text = "WHOLESALE FMCG • BAHRAIN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp,
                        color = Color(0xFF94A3B8)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Linear Brand Activity Bar
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = BrandGold,
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Live Action Message
                Text(
                    text = "$message$animatedDots",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    textAlign = TextAlign.Center
                )

                if (subMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subMessage,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Dialog variation of the Kanak Brand Loading overlay for blocking operations
 * such as posting tax invoices, syncing outbox, or completing company activation.
 */
@Composable
fun KanakBrandLoadingDialog(
    message: String = "Processing Transaction...",
    subMessage: String = "Authoritative Bahrain NBR Ledger Sync",
    onDismissRequest: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        KanakBrandLoadingOverlay(
            modifier = Modifier.fillMaxWidth(),
            message = message,
            subMessage = subMessage
        )
    }
}

/**
 * Compact branded inline loading indicator with miniature gold/navy K emblem
 * and glowing spinner for cards, sheets, or list headers.
 */
@Composable
fun KanakBrandInlineLoading(
    modifier: Modifier = Modifier,
    text: String = "Loading...",
    size: Dp = 28.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "InlineLoading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "InlineRotation"
    )

    Row(
        modifier = modifier.padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotation),
                strokeWidth = 2.5.dp,
                color = BrandGold,
                trackColor = BrandGold.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round
            )
            Surface(
                modifier = Modifier.size(size - 8.dp),
                shape = CircleShape,
                color = BrandNavyDark
            ) {
                Image(
                    painter = painterResource(id = R.drawable.kanak_app_icon_1794073715264),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(CircleShape)
                )
            }
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                color = KanakSecondaryText
            )
        )
    }
}
