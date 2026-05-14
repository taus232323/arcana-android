/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class MatrixNativeAuthAPITest {
    private val json = Json { }

    @Test
    fun `reset password request includes logout_devices and no auth block`() {
        val request = ResetPasswordRequest(
            password = "new-password",
            clientSecret = "client-secret",
            sid = "sid",
            logoutDevices = false,
        )

        assertThat(json.encodeToString(request)).isEqualTo(
            """{"password":"new-password","client_secret":"client-secret","sid":"sid","logout_devices":false}"""
        )
    }
}
