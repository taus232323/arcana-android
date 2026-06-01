/*
 * Copyright (c) 2026 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object ArcanaInviteLinkBuilder {
    fun build(sessionId: String): String {
        val encodedSessionId = URLEncoder.encode(sessionId, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return "${ArcanaConfiguration.ARCANA_INVITE_BASE_URL}/invite/$encodedSessionId"
    }
}
