package com.semmyx.ledger.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.semmyx.ledger.R
import com.semmyx.ledger.common.LedgerMark
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pager = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()
    val titles = listOf(R.string.brand_intro_title, R.string.onboard_title_one, R.string.onboard_title_two, R.string.onboard_title_three)
    val bodies = listOf(R.string.brand_intro_body, R.string.onboard_body_one, R.string.onboard_body_two, R.string.onboard_body_three)
    val icons = listOf(Icons.Default.AutoAwesome, Icons.Default.BarChart, Icons.Default.Lock, Icons.Default.CloudOff)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LedgerMark(Modifier.size(28.dp))
            Text(stringResource(R.string.app_name), Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onComplete) { Text(stringResource(R.string.skip)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Surface(Modifier.size(168.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        if (page == 0) Image(painterResource(R.drawable.brand_sky), null, Modifier.fillMaxSize())
                        else Icon(icons[page], null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(titles[page]), style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(bodies[page]), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(stringResource(R.string.brand_author_signature), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.brand_design_label), style = MaterialTheme.typography.labelSmall)
        Text(stringResource(R.string.brand_copyright), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.page_of, pager.currentPage + 1, 4), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { index ->
                val color by animateColorAsState(if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    label = "pagerIndicator")
                Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            }
        }
        Button(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), onClick = {
            if (pager.currentPage == 3) onComplete() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }) { Text(stringResource(if (pager.currentPage == 3) R.string.get_started else R.string.next)) }
        Spacer(Modifier.height(8.dp))
    }
}
