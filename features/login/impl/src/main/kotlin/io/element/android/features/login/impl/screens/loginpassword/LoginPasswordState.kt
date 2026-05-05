/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import android.os.Parcelable
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.nativeauth.PendingEmailLogin
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.parcelize.Parcelize

data class LoginPasswordState(
    val accountProvider: AccountProvider,
    val formState: LoginFormState,
    val step: LoginPasswordStep,
    val loginAction: AsyncData<SessionId>,
    val pendingEmailLogin: PendingEmailLogin?,
    val canResendVerificationCode: Boolean = true,
    val eventSink: (LoginPasswordEvents) -> Unit
) {
    val isAwaitingEmailVerification: Boolean
        get() = step == LoginPasswordStep.VerificationCode

    val submitEnabled: Boolean
        get() = loginAction !is AsyncData.Failure &&
            if (isAwaitingEmailVerification) {
                formState.verificationCode.isNotEmpty()
            } else {
                formState.login.isNotEmpty() &&
                    formState.password.isNotEmpty()
            }
}

enum class LoginPasswordStep {
    Credentials,
    VerificationCode,
}

@Parcelize
data class LoginFormState(
    val login: String,
    val password: String,
    val verificationCode: String,
) : Parcelable {
    companion object {
        val Default = LoginFormState("", "", "")
    }
}
