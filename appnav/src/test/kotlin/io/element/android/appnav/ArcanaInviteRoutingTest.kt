/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import org.junit.Test

class ArcanaInviteRoutingTest {
    @Test
    fun `resolve invite intent target for matrix user id opens direct message`() {
        val target = resolveArcanaInviteIntentTarget("@alice:celesteai.ru", "https://arcana.celesteai.ru/invite/token-123")

        assertThat(target).isEqualTo(
            ArcanaInviteIntentTarget.OpenDirectMessage(
                userId = UserId("@alice:celesteai.ru"),
            )
        )
    }

    @Test
    fun `resolve invite intent target for regular token shows invite`() {
        val target = resolveArcanaInviteIntentTarget("token-123", "https://arcana.celesteai.ru/invite/token-123")

        assertThat(target).isEqualTo(
            ArcanaInviteIntentTarget.ShowInvite(
                token = "token-123",
                webUrl = "https://arcana.celesteai.ru/invite/token-123",
            )
        )
    }

    @Test
    fun `resolve invite acceptance target opens room when session exists`() {
        val target = resolveArcanaInviteAcceptanceTarget(RoomId("!roomid:celesteai.ru"), hasLatestSession = true)

        assertThat(target).isEqualTo(
            ArcanaInviteAcceptanceTarget.OpenRoom(
                roomId = RoomId("!roomid:celesteai.ru"),
            )
        )
    }

    @Test
    fun `resolve invite acceptance target queues room when session is missing`() {
        val target = resolveArcanaInviteAcceptanceTarget(RoomId("!roomid:celesteai.ru"), hasLatestSession = false)

        assertThat(target).isEqualTo(
            ArcanaInviteAcceptanceTarget.QueueRoom(
                roomId = RoomId("!roomid:celesteai.ru"),
            )
        )
    }
}
