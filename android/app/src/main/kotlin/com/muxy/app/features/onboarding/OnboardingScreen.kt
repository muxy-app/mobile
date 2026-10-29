package com.muxy.app.features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muxy.app.R
import com.muxy.app.design.LocalAppTheme
import com.muxy.app.design.components.PageMargin
import com.muxy.app.design.components.ScrollableCenteredColumn
import com.muxy.app.design.components.SurfaceCorners
import com.muxy.app.design.components.ThemedProminentButton
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onPairDesktop: () -> Unit,
) {
    val slides = OnboardingSlide.all
    val pagerState = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    val isLastSlide = pagerState.currentPage == slides.lastIndex
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(LocalAppTheme.current.groupedBackground)
                .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onSkip, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalAppTheme.current.secondaryForeground,
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            OnboardingSlideView(slides[page], Modifier.padding(horizontal = PageMargin.horizontal * 2))
        }
        Footer(
            pagerState = pagerState,
            isLastSlide = isLastSlide,
            onAdvance = {
                if (isLastSlide) {
                    onPairDesktop()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
        )
    }
}

@Composable
private fun Footer(
    pagerState: PagerState,
    isLastSlide: Boolean,
    onAdvance: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = PageMargin.horizontal, end = PageMargin.horizontal, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PageIndicator(count = pagerState.pageCount, selected = pagerState.currentPage)
        ContinueButton(isLastSlide = isLastSlide, onClick = onAdvance)
    }
}

@Composable
private fun PageIndicator(
    count: Int,
    selected: Int,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            val isSelected = index == selected
            val width by animateDpAsState(if (isSelected) 22.dp else 6.dp, tween(200), label = "indicator")
            val color by animateColorAsState(
                if (isSelected) LocalAppTheme.current.foreground else LocalAppTheme.current.separator,
                tween(200),
                label = "indicator",
            )
            Box(modifier = Modifier.size(width = width, height = 6.dp).clip(CircleShape).background(color))
        }
    }
}

@Composable
private fun ContinueButton(
    isLastSlide: Boolean,
    onClick: () -> Unit,
) {
    ThemedProminentButton(
        text = if (isLastSlide) "Pair your desktop" else "Continue",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OnboardingSlideView(
    slide: OnboardingSlide,
    modifier: Modifier = Modifier,
) {
    when (val content = slide.content) {
        is OnboardingSlide.Content.Hero -> HeroSlide(slide.title, content, modifier)
        is OnboardingSlide.Content.Rows -> RowsSlide(slide.title, content, modifier)
    }
}

@Composable
private fun HeroSlide(
    title: String,
    hero: OnboardingSlide.Content.Hero,
    modifier: Modifier,
) {
    val theme = LocalAppTheme.current
    ScrollableCenteredColumn(spacing = 18.dp, modifier = modifier.fillMaxSize()) {
        HeroArt(hero.icon)
        SlideTitle(title)
        Text(
            text = hero.body,
            style = MaterialTheme.typography.bodyLarge,
            color = theme.secondaryForeground,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HeroArt(icon: Int?) {
    val theme = LocalAppTheme.current
    if (icon == null) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(164.dp).clip(RoundedCornerShape(36.dp)),
        )
        return
    }
    Box(
        modifier =
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(SurfaceCorners.card))
                .background(theme.secondaryGroupedBackground),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(46.dp),
            tint = theme.foreground,
        )
    }
}

@Composable
private fun RowsSlide(
    title: String,
    content: OnboardingSlide.Content.Rows,
    modifier: Modifier,
) {
    ScrollableCenteredColumn(spacing = 24.dp, modifier = modifier.fillMaxSize()) {
        SlideTitle(title)
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            content.rows.forEach { OnboardingRowView(it) }
        }
    }
}

@Composable
private fun SlideTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = LocalAppTheme.current.foreground,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun OnboardingRowView(row: OnboardingRow) {
    val theme = LocalAppTheme.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(SurfaceCorners.row))
                    .background(theme.secondaryGroupedBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(row.icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = theme.foreground,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = theme.foreground,
            )
            Text(
                text = row.body,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.secondaryForeground,
            )
        }
    }
}
