package org.opencarproject.launcher.presentation.drawer

import org.opencarproject.launcher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opencarproject.launcher.core.model.CatalogItem
import org.opencarproject.launcher.core.ui.theme.Dimens

/**
 * Apps page to the right of the cards: when swiping left, the central content shifts
 * (the top and bottom bars stay put) and this page slides in with large icons and vertical
 * scrolling. The arrow (or swiping right / "back") returns.
 */
@Composable
fun AppsPage(
    apps: List<CatalogItem>,
    onLaunch: (CatalogItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    iconScale: Float = 1f,
) {
    // Card with the same background, corners and margin as the home screen ones: separates the
    // list from the fixed bars and stays aligned with the cards while swiping.
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(end = Dimens.CellGap, bottom = Dimens.CellGap),
        shape = RoundedCornerShape(Dimens.ItemCorner),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.apps_back_home))
                }
                Text(
                    stringResource(R.string.apps_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
            AppDrawerContent(apps = apps, onLaunch = onLaunch, iconSize = 76.dp * iconScale)
        }
    }
}
