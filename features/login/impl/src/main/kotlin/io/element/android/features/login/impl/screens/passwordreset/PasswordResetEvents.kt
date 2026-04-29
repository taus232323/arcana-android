/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

sealed interface PasswordResetEvents {
    data class SetEmail(val email: String) : PasswordResetEvents
    data class SetNewPassword(val newPassword: String) : PasswordResetEvents
    data class SetConfirmPassword(val confirmPassword: String) : PasswordResetEvents
    data object Submit : PasswordResetEvents
    data object ConfirmEmailVerified : PasswordResetEvents
    data object ResendEmail : PasswordResetEvents
    data object ClearError : PasswordResetEvents
    data object ClearSuccess : PasswordResetEvents
}
