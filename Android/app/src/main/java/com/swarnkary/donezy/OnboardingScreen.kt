package com.swarnkary.donezy

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    val pages = listOf(
        OnboardingPageData(
            title = "Welcome to Donezy",
            description = "Your personal dashboard to track hobbies, form good habits, and structure your daily achievements.",
            emoji = "✨",
            accentBrush = Brush.linearGradient(listOf(Color(0xFF1A6B48), Color(0xFF2D7A46)))
        ),
        OnboardingPageData(
            title = "Smart Reminders & Streaks",
            description = "Stay accountable with customizable reminders, snoozes, and automatic streak tracking for every tracker.",
            emoji = "🔔",
            accentBrush = Brush.linearGradient(listOf(Color(0xFFFF8C00), Color(0xFFFF6347)))
        ),
        OnboardingPageData(
            title = "Unlock Achievements",
            description = "Earn badges, track your insights visually, and share your progress cards directly with friends.",
            emoji = "🏆",
            accentBrush = Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFF00BCD4)))
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val data = pages[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated Emoji Float
                val infiniteTransition = rememberInfiniteTransition(label = "emojiFloat")
                val floatAnim by infiniteTransition.animateFloat(
                    initialValue = -8f,
                    targetValue = 8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "emojiFloatAnim"
                )

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(data.accentBrush)
                        .offset(y = floatAnim.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = data.emoji,
                            fontSize = 64.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Text(
                    text = data.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = data.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
        }

        // Bottom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Page Indicators
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    val active = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (active) 20.dp else 8.dp,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                        label = "indicatorWidth"
                    )
                    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .height(8.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = {
                        scope.launch { pagerState.animateScrollToPage(2) }
                    }) {
                        Text("Skip")
                    }
                    Button(
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next")
                    }
                } else {
                    Button(
                        onClick = onComplete,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Get Started")
                    }
                }
            }
        }
    }
}

data class OnboardingPageData(
    val title: String,
    val description: String,
    val emoji: String,
    val accentBrush: Brush
)
