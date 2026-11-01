package com.resolvex.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.ui.theme.ElectricBlue
import com.resolvex.app.ui.theme.ElectricCyan
import com.resolvex.app.ui.theme.NavyDark
import com.resolvex.app.ui.theme.NavyDeep
import com.resolvex.app.ui.theme.NeonPurple
import com.resolvex.app.ui.theme.SoftCyan
import com.resolvex.app.ui.theme.TextPrimary
import com.resolvex.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

data class OnboardingPageData(
    val title: String,
    val subtitle: String,
    val category: String,
    val visualType: Int
)

@Composable
fun OnboardingScreen(navController: NavController) {
    val pages = listOf(
        OnboardingPageData(
            title = "Report civic problems easily.",
            subtitle = "Instantly capture road potholes, water leaks, or street light issues with precise geo-tagging.",
            category = "CITIZEN FIRST",
            visualType = 0
        ),
        OnboardingPageData(
            title = "Get intelligent AI-powered assistance.",
            subtitle = "Smart image recognition and AI automatically categorizes and routes your complaint to the right authority.",
            category = "NEURAL ENGINE",
            visualType = 1
        ),
        OnboardingPageData(
            title = "Track complaints and create real change.",
            subtitle = "Monitor resolution timelines, receive live status alerts, and hold local officials directly accountable.",
            category = "TRANSPARENCY & IMPACT",
            visualType = 2
        )
    )

    val pagerState = rememberPagerState(initialPage = 0) { pages.size }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sessionManager = remember { com.resolvex.app.data.SessionManager.getInstance(context) }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            NavyDeep,
            NavyDark,
            Color(0xFF040817)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Logo & Skip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Header Brand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.resolvex_logo),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        text = "ResolveX",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Skip Button
                AnimatedVisibility(
                    visible = pagerState.currentPage < pages.size - 1,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(
                        onClick = {
                            sessionManager.setSeenOnboarding(true)
                            navController.navigate("login") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    ) {
                        Text(
                            text = "Skip",
                            color = SoftCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Main Pager with scroll-safe pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val page = pages[pageIndex]
                val pageScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollState)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Futuristic visual card (compact 180dp to guarantee no overflow)
                    OnboardingVisualElement(visualType = page.visualType)

                    Spacer(modifier = Modifier.height(24.dp))

                    // Category Pill Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ElectricCyan.copy(alpha = 0.12f))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = page.category,
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Main Title
                    Text(
                        text = page.title,
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subtitle
                    Text(
                        text = page.subtitle,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Bottom Navigation Area: Indicators and Action Buttons (guaranteed visible above system nav)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page Indicator Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    repeat(pages.size) { index ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isSelected) 28.dp else 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue))
                                    } else {
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFF233554),
                                                Color(0xFF1E293B)
                                            )
                                        )
                                    }
                                )
                        )
                    }
                }

                // Action Buttons
                val isLastPage = pagerState.currentPage == pages.size - 1

                Button(
                    onClick = {
                        if (isLastPage) {
                            sessionManager.setSeenOnboarding(true)
                            navController.navigate("login") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = if (isLastPage) {
                                        listOf(ElectricCyan, ElectricBlue, NeonPurple)
                                    } else {
                                        listOf(ElectricCyan, ElectricBlue)
                                    }
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isLastPage) "Get Started" else "Next",
                            color = NavyDeep,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * Reusable Futuristic Vector Artwork for Onboarding Pages
 */
@Composable
fun OnboardingVisualElement(visualType: Int) {
    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF132247),
                        Color(0xFF0C1630),
                        NavyDeep
                    )
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(
                    colors = listOf(
                        ElectricCyan.copy(alpha = 0.5f),
                        ElectricBlue.copy(alpha = 0.2f),
                        NeonPurple.copy(alpha = 0.4f)
                    )
                ),
                RoundedCornerShape(32.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val centerPoint = Offset(size.width / 2, size.height / 2)

            when (visualType) {
                0 -> {
                    // Civic Problem Reporting Illustration: Target Radar & Location Pin
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ElectricCyan.copy(alpha = 0.25f), Color.Transparent),
                            center = centerPoint,
                            radius = size.width / 2
                        )
                    )
                    drawCircle(
                        color = ElectricBlue.copy(alpha = 0.3f),
                        radius = size.width * 0.42f,
                        center = centerPoint,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = ElectricCyan.copy(alpha = 0.5f),
                        radius = size.width * 0.28f,
                        center = centerPoint,
                        style = Stroke(width = 3f)
                    )

                    // Pin Head
                    drawCircle(
                        brush = Brush.linearGradient(listOf(ElectricCyan, ElectricBlue)),
                        radius = 24f,
                        center = Offset(centerPoint.x, centerPoint.y - 12f)
                    )
                    drawCircle(
                        color = NavyDeep,
                        radius = 9f,
                        center = Offset(centerPoint.x, centerPoint.y - 12f)
                    )

                    // Pin Pointer
                    val pinPath = Path().apply {
                        moveTo(centerPoint.x - 18f, centerPoint.y - 4f)
                        lineTo(centerPoint.x, centerPoint.y + 30f)
                        lineTo(centerPoint.x + 18f, centerPoint.y - 4f)
                        close()
                    }
                    drawPath(
                        path = pinPath,
                        brush = Brush.verticalGradient(
                            listOf(ElectricBlue, NeonPurple),
                            startY = centerPoint.y - 4f,
                            endY = centerPoint.y + 30f
                        )
                    )
                }

                1 -> {
                    // AI-Powered Neural Core: Circuit Nodes & Electric Pulse
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(NeonPurple.copy(alpha = 0.3f), Color.Transparent),
                            center = centerPoint,
                            radius = size.width / 2
                        )
                    )

                    // Central AI Core Hex / diamond
                    val coreRadius = 32f
                    drawCircle(
                        brush = Brush.linearGradient(listOf(NeonPurple, ElectricCyan)),
                        radius = coreRadius,
                        center = centerPoint
                    )
                    drawCircle(
                        color = NavyDark,
                        radius = 20f,
                        center = centerPoint
                    )
                    drawCircle(
                        color = ElectricCyan,
                        radius = 10f,
                        center = centerPoint
                    )

                    // Radiating neural branches
                    val angles = listOf(0.0, 60.0, 120.0, 180.0, 240.0, 300.0)
                    angles.forEach { deg ->
                        val rad = Math.toRadians(deg)
                        val endX = centerPoint.x + (Math.cos(rad) * 52f).toFloat()
                        val endY = centerPoint.y + (Math.sin(rad) * 52f).toFloat()

                        drawLine(
                            brush = Brush.linearGradient(listOf(ElectricCyan, NeonPurple)),
                            start = centerPoint,
                            end = Offset(endX, endY),
                            strokeWidth = 3.5f,
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = ElectricCyan,
                            radius = 6f,
                            center = Offset(endX, endY)
                        )
                    }
                }

                2 -> {
                    // Tracking & Real Change: Verified Shield & Progress Check
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ElectricCyan.copy(alpha = 0.25f), Color.Transparent),
                            center = centerPoint,
                            radius = size.width / 2
                        )
                    )

                    // Outer Hex Ring
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(ElectricCyan, ElectricBlue, NeonPurple, ElectricCyan),
                            center = centerPoint
                        ),
                        radius = size.width * 0.38f,
                        center = centerPoint,
                        style = Stroke(width = 3.5f)
                    )

                    // Shield background
                    val shield = Path().apply {
                        moveTo(centerPoint.x, centerPoint.y - 38f)
                        lineTo(centerPoint.x + 32f, centerPoint.y - 24f)
                        lineTo(centerPoint.x + 28f, centerPoint.y + 14f)
                        lineTo(centerPoint.x, centerPoint.y + 40f)
                        lineTo(centerPoint.x - 28f, centerPoint.y + 14f)
                        lineTo(centerPoint.x - 32f, centerPoint.y - 24f)
                        close()
                    }
                    drawPath(
                        path = shield,
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFF102752), Color(0xFF09142E))
                        )
                    )
                    drawPath(
                        path = shield,
                        brush = Brush.linearGradient(listOf(ElectricCyan, ElectricBlue)),
                        style = Stroke(width = 2.5f)
                    )

                    // Checkmark
                    val checkPath = Path().apply {
                        moveTo(centerPoint.x - 14f, centerPoint.y)
                        lineTo(centerPoint.x - 3f, centerPoint.y + 11f)
                        lineTo(centerPoint.x + 16f, centerPoint.y - 11f)
                    }
                    drawPath(
                        path = checkPath,
                        color = ElectricCyan,
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}
