package com.amrg.connectly.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun VideoCallControls(
    modifier: Modifier,
    callMediaState: CallMediaState,
    actions: List<VideoCallControlAction> = buildDefaultCallControlActions(callMediaState = callMediaState),
    onCallAction: (CallAction) -> Unit
) {
    LazyRow(
        modifier = modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(actions) { action ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(action.background)
            ) {
                Icon(
                    modifier = Modifier
                        .padding(10.dp)
                        .align(Alignment.Center)
                        .clickable { onCallAction(action.callAction) },
                    tint = action.iconTint,
                    painter = action.icon,
                    contentDescription = null
                )
            }
        }
    }
}