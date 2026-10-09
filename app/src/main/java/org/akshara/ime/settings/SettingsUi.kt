package org.akshara.ime.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.akshara.ime.R

/** One row of a settings card. Colors and icons are resources, so pages stay plain data. */
internal sealed interface SettingsRow {
    val title: String
    val summary: String?
    val icon: Int
    val tint: Int

    /** Opens something (a page, a choice, a link); [chevron] shows it leads somewhere. */
    data class Action(
        override val title: String,
        override val summary: String?,
        override val icon: Int,
        override val tint: Int,
        val chevron: Boolean,
        val destructive: Boolean = false,
        val onClick: (() -> Unit)?
    ) : SettingsRow

    data class Toggle(
        override val title: String,
        override val summary: String?,
        override val icon: Int,
        override val tint: Int,
        val checked: Boolean,
        val enabled: Boolean,
        val onChange: (Boolean) -> Unit
    ) : SettingsRow

    /** A label with a value on the trailing side (version, device). */
    data class Value(
        override val title: String,
        val value: String,
        val onClick: (() -> Unit)?
    ) : SettingsRow {
        override val summary: String? get() = null
        override val icon: Int get() = 0
        override val tint: Int get() = 0
    }
}

internal object SettingsColors {
    private val brand = Color(0xFF315DA8)

    fun isDark(context: Context) =
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /** Wallpaper colors on Android 12+, otherwise a scheme around the Akshara blue. */
    fun scheme(context: Context): ColorScheme {
        val dark = isDark(context)
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> darkColorScheme(primary = Color(0xFFAFC6FF), onPrimary = Color(0xFF0B2E64), primaryContainer = Color(0xFF26467F))
            else -> lightColorScheme(primary = brand, onPrimary = Color.White, primaryContainer = Color(0xFFD8E2FF))
        }
    }
}

@Composable
internal fun SettingsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = remember(context) { SettingsColors.scheme(context) }
    MaterialTheme(colorScheme = scheme, content = content)
}

private val OUTER = 24.dp
private val INNER = 4.dp

/**
 * A group of rows drawn as connected segments: large outer corners, small inner ones and a thin gap,
 * like the grouped lists in current Android Settings.
 */
@Composable
internal fun SettingsCard(rows: List<SettingsRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            val shape = RoundedCornerShape(
                topStart = if (index == 0) OUTER else INNER,
                topEnd = if (index == 0) OUTER else INNER,
                bottomStart = if (index == rows.lastIndex) OUTER else INNER,
                bottomEnd = if (index == rows.lastIndex) OUTER else INNER
            )
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                when (row) {
                    is SettingsRow.Toggle -> ToggleRow(row)
                    is SettingsRow.Action -> ActionRow(row)
                    is SettingsRow.Value -> ValueRow(row)
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(row: SettingsRow.Toggle) {
    var checked by remember(row.title) { mutableStateOf(row.checked) }
    RowLayout(
        row,
        Modifier
            .toggleable(value = checked, enabled = row.enabled, role = Role.Switch) {
                checked = it
                row.onChange(it)
            }
            .alpha(if (row.enabled) 1f else .38f)
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = row.enabled,
            thumbContent = if (checked) {
                {
                    Icon(
                        painterResource(R.drawable.ic_check), null,
                        tint = MaterialTheme.colorScheme.primary,   // reads on the thumb (onPrimary) in light and dark
                        modifier = Modifier.size(SwitchDefaults.IconSize)
                    )
                }
            } else null
        )
    }
}

@Composable
private fun ActionRow(row: SettingsRow.Action) {
    val modifier = row.onClick?.let { Modifier.clickable(role = Role.Button, onClick = it) } ?: Modifier
    RowLayout(row, modifier, titleColor = if (row.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) {
        if (row.chevron) {
            Icon(
                painterResource(R.drawable.ic_chevron), null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ValueRow(row: SettingsRow.Value) {
    val modifier = row.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    RowLayout(row, modifier) {
        Text(row.value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowLayout(
    row: SettingsRow,
    modifier: Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (row.icon != 0) {
            val context = LocalContext.current
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(context.getColor(row.tint))),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(row.icon), null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            row.summary?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}
