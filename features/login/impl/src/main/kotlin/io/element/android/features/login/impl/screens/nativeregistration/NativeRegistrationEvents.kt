/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

sealed interface NativeRegistrationEvents {
    data class SetEmail(val email: String) : NativeRegistrationEvents
    data class SetVerificationCode(val verificationCode: String) : NativeRegistrationEvents
    data class SetUsername(val username: String) : NativeRegistrationEvents
    data class SetPassword(val password: String) : NativeRegistrationEvents
    data object Submit : NativeRegistrationEvents
    data object ResendEmail : NativeRegistrationEvents
    data object GoBack : NativeRegistrationEvents
    data object ClearError : NativeRegistrationEvents
}
