package io.github.ard9.linguaplayer.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.ard9.linguaplayer.R

private class GuideSection(@StringRes val title: Int, @StringRes val body: Int)

private val SECTIONS = listOf(
    GuideSection(R.string.guide_start_title, R.string.guide_start_body),
    GuideSection(R.string.guide_subs_title, R.string.guide_subs_body),
    GuideSection(R.string.guide_player_title, R.string.guide_player_body),
    GuideSection(R.string.guide_save_title, R.string.guide_save_body),
    GuideSection(R.string.guide_cards_title, R.string.guide_cards_body),
    GuideSection(R.string.guide_roleplay_title, R.string.guide_roleplay_body),
    GuideSection(R.string.guide_backup_title, R.string.guide_backup_body),
    GuideSection(R.string.guide_settings_title, R.string.guide_settings_body),
    GuideSection(R.string.guide_tips_title, R.string.guide_tips_body),
    GuideSection(R.string.guide_trouble_title, R.string.guide_trouble_body),
)

/** The user guide: a short introduction and sections that open on tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    // Which section is open; the first one starts open so the page is not just a list.
    var open by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.guide)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                Text(
                    stringResource(R.string.guide_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cinema.Dim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            itemsIndexed(SECTIONS) { index, section ->
                GuideItem(
                    number = index + 1,
                    section = section,
                    expanded = open == index,
                    onToggle = { open = if (open == index) -1 else index },
                )
            }
        }
    }
}

@Composable
private fun GuideItem(number: Int, section: GuideSection, expanded: Boolean, onToggle: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        color = Cinema.Seat,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (expanded) accent else Cinema.Aisle, CircleShape),
                ) {
                    Text(
                        stringResource(R.string.number, number),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (expanded) Cinema.Night else Cinema.Screen,
                    )
                }
                Text(
                    stringResource(section.title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Cinema.Dim,
                )
            }
            if (expanded) {
                Text(
                    stringResource(section.body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cinema.Screen,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }
}
