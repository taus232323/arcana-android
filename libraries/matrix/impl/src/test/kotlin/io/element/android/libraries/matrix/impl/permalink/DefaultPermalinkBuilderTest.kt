/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.permalink

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomAlias
import io.element.android.libraries.matrix.api.core.UserId
import org.junit.Test

class DefaultPermalinkBuilderTest {
    @Test
    fun `permalinkForUser uses the configured client permalink base url`() {
        val builder = DefaultPermalinkBuilder(clientPermalinkBaseUrl = "https://arcana.celesteai.ru/#/")

        val result = builder.permalinkForUser(UserId("@dtaus:celesteai.ru"))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo("https://arcana.celesteai.ru/#/user/@dtaus:celesteai.ru")
    }

    @Test
    fun `permalinkForRoomAlias uses the configured client permalink base url`() {
        val builder = DefaultPermalinkBuilder(clientPermalinkBaseUrl = "https://arcana.celesteai.ru/#/")

        val result = builder.permalinkForRoomAlias(RoomAlias("#general:celesteai.ru"))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo("https://arcana.celesteai.ru/#/room/#general:celesteai.ru")
    }
}
