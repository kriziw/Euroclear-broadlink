package io.github.kriziw.bl3372setup.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kriziw.bl3372setup.R
import io.github.kriziw.bl3372setup.ui.theme.LocalStatusColors

/**
 * Every screen: a top app bar, a scrolling column of content, and optional bottom bar, FAB and
 * snackbars. Content is padded for system bars and the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    belowTopBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            subtitle?.invoke()
                        }
                    },
                    navigationIcon = navigation,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        scrolledContainerColor = colors.surfaceContainer,
                    ),
                )
                belowTopBar()
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun BackButton(onBack: () -> Unit, @DrawableRes icon: Int = R.drawable.ic_arrow_back, description: String = stringResource(R.string.cd_back)) {
    IconButton(onClick = onBack) { Icon(painterResource(icon), description) }
}

/** A plain rounded card; content is spaced evenly. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

/** A card whose body opens on tap, for details most people never need. */
@Composable
fun ExpandableCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = stringResource(if (expanded) R.string.action_hide else R.string.action_show),
                    role = Role.Button,
                ) { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

enum class StatusKind(@param:DrawableRes val icon: Int?) {
    SUCCESS(R.drawable.ic_check_circle),
    WARNING(R.drawable.ic_warning),
    ERROR(R.drawable.ic_error),
    INFO(R.drawable.ic_info),
    PROGRESS(null),
}

@Composable
private fun StatusKind.tint(): Color = when (this) {
    StatusKind.SUCCESS -> LocalStatusColors.current.success
    StatusKind.WARNING -> LocalStatusColors.current.warning
    StatusKind.ERROR -> MaterialTheme.colorScheme.error
    StatusKind.INFO, StatusKind.PROGRESS -> MaterialTheme.colorScheme.primary
}

@Composable
private fun StatusIcon(kind: StatusKind, tint: Color) {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (kind.icon != null) {
            Icon(painterResource(kind.icon), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        } else {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = tint)
        }
    }
}

@Composable
fun StatusLine(kind: StatusKind, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        StatusIcon(kind, kind.tint())
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * A tinted message box for things that need attention. [actions] are laid out under the text.
 */
@Composable
fun Banner(
    kind: StatusKind,
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val status = LocalStatusColors.current
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        StatusKind.SUCCESS -> status.successContainer to status.onSuccessContainer
        StatusKind.WARNING -> status.warningContainer to status.onWarningContainer
        StatusKind.ERROR -> colors.errorContainer to colors.onErrorContainer
        StatusKind.INFO, StatusKind.PROGRESS -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            StatusIcon(kind, content)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                title?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (actions != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
                }
            }
        }
    }
}

/** A text button for a [Banner], in the banner's own colour. */
@Composable
fun BannerAction(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.offset(x = (-8).dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** The privacy note, shown where credentials or settings are handled. */
@Composable
fun PrivacyFooter(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Top) {
        Icon(
            painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 1.dp).size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Hint(stringResource(R.string.footer_privacy))
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** A label/value row for read-only device data. */
@Composable
fun ValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

/**
 * A tappable list row: label over value, with an optional leading icon. Disabled rows still show
 * their value but lose the trailing affordance.
 */
@Composable
fun SettingRow(
    label: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int = R.drawable.ic_chevron_right,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (enabled) {
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(trailingIcon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

/** A card holding [SettingRow]s edge to edge, with an optional heading. */
@Composable
fun ListCard(modifier: Modifier = Modifier, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            title?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            content()
        }
    }
}

/** A small heading inside a [ListCard]. */
@Composable
fun ListSubheader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** An icon on a tinted circle, used to mark list entries. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(Modifier.size(44.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

/** A large tappable choice: icon, title and a one-line explanation. */
@Composable
fun OptionCard(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Hint(description)
            }
            Spacer(Modifier.width(8.dp))
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
