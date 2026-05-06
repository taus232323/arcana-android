/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.element.android.features.login.impl.R

@Composable
fun ArcanaMark(
    modifier: Modifier = Modifier,
    size: Dp,
) {
    Image(
        modifier = modifier.size(size),
        painter = painterResource(id = R.drawable.arcana_mark),
        contentDescription = null,
    )
}
