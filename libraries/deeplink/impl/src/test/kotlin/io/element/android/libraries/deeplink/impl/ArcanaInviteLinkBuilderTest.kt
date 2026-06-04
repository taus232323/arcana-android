/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.ArcanaInviteLinkBuilder
import org.junit.Test

class ArcanaInviteLinkBuilderTest {
    @Test
    fun `build returns arcana invite url`() {
        val result = ArcanaInviteLinkBuilder.build("invite-token-123")

        assertThat(result).isEqualTo("https://arcana.celesteai.ru/invite/invite-token-123")
    }

    @Test
    fun `build escapes invalid path characters`() {
        val result = ArcanaInviteLinkBuilder.build("a/:domain")

        assertThat(result).isEqualTo("https://arcana.celesteai.ru/invite/a%2F%3Adomain")
    }
}
