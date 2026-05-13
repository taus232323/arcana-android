/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

import android.os.Parcelable
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.nativeauth.PendingPasswordReset
import io.element.android.libraries.architecture.AsyncData
import kotlinx.parcelize.Parcelize

data class PasswordResetState(
    val accountProvider: AccountProvider,
    val formState: PasswordResetFormState,
    val step: PasswordResetStep,
    val resetAction: AsyncData<Unit>,
    val pendingPasswordReset: PendingPasswordReset?,
    val eventSink: (PasswordResetEvents) -> Unit,
) {
    val submitEnabled: Boolean
        get() = resetAction !is AsyncData.Loading &&
            when (step) {
                PasswordResetStep.Email -> formState.email.isNotBlank()
                PasswordResetStep.Code -> formState.verificationCode.isNotBlank()
                PasswordResetStep.Credentials -> formState.newPassword.isNotBlank() && formState.confirmPassword.isNotBlank()
            }

    val isAwaitingEmailVerification: Boolean
        get() = step == PasswordResetStep.Code

    val isAwaitingCredentials: Boolean
        get() = step == PasswordResetStep.Credentials
}

enum class PasswordResetStep {
    Email,
    Code,
    Credentials,
}

@Parcelize
data class PasswordResetFormState(
    val email: String,
    val verificationCode: String,
    val newPassword: String,
    val confirmPassword: String,
) : Parcelable {
    companion object {
        val Default = PasswordResetFormState(
            email = "",
            verificationCode = "",
            newPassword = "",
            confirmPassword = "",
        )
    }
}
