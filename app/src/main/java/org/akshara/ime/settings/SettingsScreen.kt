package org.akshara.ime.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.akshara.ime.R

/** What a settings page shows, top to bottom. Pages build these; [SettingsScreen] draws them. */
internal sealed interface PageBlock {
    /** The app logo with its name: beside it on Home, centred and larger on About. */
    data class Logo(val large: Boolean, val subtitle: String) : PageBlock
    data class Category(val title: String) : PageBlock
    data class Card(val rows: List<SettingsRow>) : PageBlock
    /** A paragraph of explanation (privacy, notices). */
    data class Copy(val text: String) : PageBlock
    data class Themes(
        val title: String,
        val collapsible: Boolean,
        val expanded: Boolean,
        val onToggle: () -> Unit,
        val cells: List<ThemeCell>,
        val onPick: (ThemeCell) -> Unit
    ) : PageBlock
}

/** One rendered page: [key] identifies it for transitions and scroll memory; Home has no [title]. */
internal data class PageContent(val key: String, val title: String?, val blocks: List<PageBlock>)

/** The page being shown and whether it was reached going deeper ([forward]) or back up. */
internal data class Shown(val page: PageContent, val forward: Boolean)

/**
 * Settings: pages slide in from the end going deeper and from the start coming back; sub-pages get a
 * large Material 3 top bar that collapses as the list scrolls.
 */
@Composable
internal fun SettingsScreen(shown: Shown, listState: (String) -> LazyListState, onBack: () -> Unit) {
    AnimatedContent(
        targetState = shown,
        contentKey = { it.page.key },
        transitionSpec = { pageTransition(targetState.forward) },
        label = "settings page"
    ) { target ->
        SettingsPage(target.page, listState(target.page.key), onBack)
    }
}

/**
 * Where pages start and end, as offsets for a [width]-wide screen: going deeper the new page enters from the
 * end and the old one drifts toward the start; going back, the reverse.
 */
internal fun slideOffsets(forward: Boolean, width: Int): Pair<Int, Int> {
    val direction = if (forward) 1 else -1
    return width * direction to -width * direction / 4
}

private fun AnimatedContentTransitionScope<Shown>.pageTransition(forward: Boolean): ContentTransform {
    val duration = 300
    return (slideInHorizontally(tween(duration)) { slideOffsets(forward, it).first } + fadeIn(tween(duration)))
        .togetherWith(slideOutHorizontally(tween(duration)) { slideOffsets(forward, it).second } + fadeOut(tween(duration / 2)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(page: PageContent, state: LazyListState, onBack: () -> Unit) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = if (page.title != null) Modifier.nestedScroll(scroll.nestedScrollConnection) else Modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (page.title != null) {
                LargeTopAppBar(
                    title = { Text(page.title) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(painterResource(R.drawable.ic_nav_back), stringResource(R.string.navigate_up))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    scrollBehavior = scroll
                )
            }
        }
    ) { padding ->
        // Rows stay below the top bar (it collapses instead); the list still runs under the navigation bar
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp)
        ) {
            itemsIndexed(page.blocks) { index, block ->
                when (block) {
                    is PageBlock.Logo -> Logo(block)
                    is PageBlock.Category -> Category(block.title, first = index == 0)
                    is PageBlock.Card -> Column(Modifier.padding(bottom = 16.dp)) { SettingsCard(block.rows) }
                    is PageBlock.Copy -> CopyCard(block.text)
                    is PageBlock.Themes -> Column(Modifier.padding(bottom = 8.dp)) {
                        ThemeGrid(block.title, block.collapsible, block.expanded, block.onToggle, block.cells, block.onPick)
                    }
                }
            }
        }
    }
}

@Composable
private fun Category(title: String, first: Boolean) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = if (first) 0.dp else 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun CopyCard(text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )
    }
}

@Composable
private fun Logo(block: PageBlock.Logo) {
    val radius = dimensionResource(R.dimen.settings_logo_radius)
    @Composable
    fun mark(size: androidx.compose.ui.unit.Dp) = Image(
        painterResource(R.drawable.settings_logo), null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(colorResource(R.color.akshara_icon_background))
            .border(1.dp, colorResource(R.color.settings_logo_outline), RoundedCornerShape(radius))
    )
    if (block.large) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            mark(dimensionResource(R.dimen.settings_logo_size_large))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                block.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            mark(dimensionResource(R.dimen.settings_logo_size))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(block.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
