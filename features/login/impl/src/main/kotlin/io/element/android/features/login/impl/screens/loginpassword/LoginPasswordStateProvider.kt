/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.accountprovider.anAccountProvider
import io.element.android.features.login.impl.nativeauth.PendingEmailLogin
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId

open class LoginPasswordStateProvider : PreviewParameterProvider<LoginPasswordState> {
    override val values: Sequence<LoginPasswordState>
        get() = sequenceOf(
            aLoginPasswordState(),
            aLoginPasswordState(step = LoginPasswordStep.VerificationCode, pendingEmailLogin = aPendingEmailLogin(), formState = aLoginFormState(login = "alice", verificationCode = "123456")),
            // Loading
            aLoginPasswordState(loginAction = AsyncData.Loading()),
            // Error
            aLoginPasswordState(loginAction = AsyncData.Failure(Exception("An error occurred"))),
        )
}

fun aLoginPasswordState(
    accountProvider: AccountProvider = anAccountProvider(),
    formState: LoginFormState = LoginFormState.Default,
    step: LoginPasswordStep = LoginPasswordStep.Credentials,
    loginAction: AsyncData<SessionId> = AsyncData.Uninitialized,
    pendingEmailLogin: PendingEmailLogin? = null,
    eventSink: (LoginPasswordEvents) -> Unit = {},
) = LoginPasswordState(
    accountProvider = accountProvider,
    formState = formState,
    step = step,
    loginAction = loginAction,
    pendingEmailLogin = pendingEmailLogin,
    eventSink = eventSink,
)

fun aLoginFormState(
    login: String = "",
    password: String = "",
    verificationCode: String = "",
) = LoginFormState(
    login = login,
    password = password,
    verificationCode = verificationCode,
)

fun aPendingEmailLogin() = PendingEmailLogin(
    homeserverUrl = "https://matrix.example.org",
    login = "alice",
    password = "password",
    clientSecret = "client-secret",
    sendAttempt = 1,
    sid = "sid",
    email = "alice@example.com",
    verificationCode = "123456",
)
