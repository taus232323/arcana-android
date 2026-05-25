/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

internal sealed interface ArcanaInviteIntentTarget {
    data class OpenDirectMessage(val userId: UserId) : ArcanaInviteIntentTarget
    data class ShowInvite(val token: String, val webUrl: String?) : ArcanaInviteIntentTarget
}

internal sealed interface ArcanaInviteAcceptanceTarget {
    data class OpenRoom(val roomId: RoomId) : ArcanaInviteAcceptanceTarget
    data class QueueRoom(val roomId: RoomId) : ArcanaInviteAcceptanceTarget
}

internal fun resolveArcanaInviteIntentTarget(token: String, webUrl: String?): ArcanaInviteIntentTarget {
    val inviteUserId = token.takeIf(MatrixPatterns::isUserId)?.let(::UserId)
    return if (inviteUserId != null) {
        ArcanaInviteIntentTarget.OpenDirectMessage(inviteUserId)
    } else {
        ArcanaInviteIntentTarget.ShowInvite(token, webUrl)
    }
}

internal fun resolveArcanaInviteAcceptanceTarget(roomId: RoomId, hasLatestSession: Boolean): ArcanaInviteAcceptanceTarget {
    return if (hasLatestSession) {
        ArcanaInviteAcceptanceTarget.OpenRoom(roomId)
    } else {
        ArcanaInviteAcceptanceTarget.QueueRoom(roomId)
    }
}
