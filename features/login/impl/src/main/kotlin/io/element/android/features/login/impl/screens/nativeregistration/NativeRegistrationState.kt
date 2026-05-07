/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

import android.os.Parcelable
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.parcelize.Parcelize

data class NativeRegistrationState(
    val accountProvider: AccountProvider,
    val formState: NativeRegistrationFormState,
    val step: NativeRegistrationStep,
    val registerAction: AsyncData<SessionId>,
    val pendingRegistration: PendingRegistration?,
    val canResendEmail: Boolean = true,
    val eventSink: (NativeRegistrationEvents) -> Unit,
) {
    val submitEnabled: Boolean
        get() = registerAction !is AsyncData.Loading &&
            when (step) {
                NativeRegistrationStep.Email -> formState.email.isNotBlank()
                NativeRegistrationStep.Code -> formState.verificationCode.isNotBlank()
                NativeRegistrationStep.Credentials -> formState.password.isNotBlank()
            }

    val isAwaitingEmailVerification: Boolean
        get() = step == NativeRegistrationStep.Code

    val isAwaitingCredentials: Boolean
        get() = step == NativeRegistrationStep.Credentials
}

enum class NativeRegistrationStep {
    Email,
    Code,
    Credentials,
}

@Parcelize
data class NativeRegistrationFormState(
    val email: String,
    val verificationCode: String,
    val username: String,
    val password: String,
) : Parcelable {
    companion object {
        val Default = NativeRegistrationFormState(
            email = "",
            verificationCode = "",
            username = "",
            password = "",
        )
    }
}

sealed class NativeRegistrationValidationException : Exception() {
    data object PasswordMismatch : NativeRegistrationValidationException()
}
