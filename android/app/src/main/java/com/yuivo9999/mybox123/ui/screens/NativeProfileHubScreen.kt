package com.yuivo9999.mybox123.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yuivo9999.mybox123.ui.theme.LocalAppAppearance

/** Profile subpage switcher; keeps source/settings pages under the existing PROFILE destination. */
@Composable
fun NativeProfileHubScreen(
    selectedPageName: String,
    onSelectedPageNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalAppAppearance.current
    val selectedPage = ProfileSubpage.values().firstOrNull { it.name == selectedPageName } ?: ProfileSubpage.SOURCES
    BackHandler(enabled = selectedPage != ProfileSubpage.SOURCES) {
        onSelectedPageNameChange(ProfileSubpage.SOURCES.name)
    }
    Column(
        modifier = modifier.fillMaxSize()
            .padding(horizontal = appearance.outerPadding.dp, vertical = appearance.outerPadding.dp),
        verticalArrangement = Arrangement.spacedBy(appearance.itemSpacing.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileTab("内容源", selectedPage == ProfileSubpage.SOURCES, Modifier.weight(1f)) { onSelectedPageNameChange(ProfileSubpage.SOURCES.name) }
            ProfileTab("设置", selectedPage == ProfileSubpage.SETTINGS, Modifier.weight(1f)) { onSelectedPageNameChange(ProfileSubpage.SETTINGS.name) }
            ProfileTab("多仓", selectedPage == ProfileSubpage.REPOSITORIES, Modifier.weight(1f)) { onSelectedPageNameChange(ProfileSubpage.REPOSITORIES.name) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileTab("数据", selectedPage == ProfileSubpage.DATA, Modifier.weight(1f)) { onSelectedPageNameChange(ProfileSubpage.DATA.name) }
            ProfileTab("关于", selectedPage == ProfileSubpage.ABOUT, Modifier.weight(1f)) { onSelectedPageNameChange(ProfileSubpage.ABOUT.name) }
            Box(modifier = Modifier.weight(1f)) {}
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selectedPage) {
                ProfileSubpage.SOURCES -> NativeSourceManagementScreen()
                ProfileSubpage.SETTINGS -> NativeSettingsScreen()
                ProfileSubpage.REPOSITORIES -> NativeRepositoryManagementScreen()
                ProfileSubpage.DATA -> NativeDataManagementScreen()
                ProfileSubpage.ABOUT -> NativeAboutScreen()
            }
        }
    }
}

@Composable
private fun ProfileTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) Button(onClick = {}, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

enum class ProfileSubpage { SOURCES, SETTINGS, REPOSITORIES, DATA, ABOUT }
