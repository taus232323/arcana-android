/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.impl.R

@Composable
fun ArcanaLogo(
    modifier: Modifier = Modifier,
    containerSize: Dp,
    logoSize: Dp,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(15.dp))
            .background(ElementTheme.colors.bgCanvasDefault, shape = RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.size(logoSize),
            painter = painterResource(id = R.drawable.arcana_logo),
            contentDescription = null,
        )
    }
}
