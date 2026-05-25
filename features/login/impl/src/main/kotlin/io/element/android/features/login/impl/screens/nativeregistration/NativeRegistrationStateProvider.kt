/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.accountprovider.anAccountProvider
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId

open class NativeRegistrationStateProvider : PreviewParameterProvider<NativeRegistrationState> {
    override val values: Sequence<NativeRegistrationState>
        get() = sequenceOf(
            aNativeRegistrationState(),
            aNativeRegistrationState(step = NativeRegistrationStep.Code, pendingRegistration = aPendingRegistration(), formState = aNativeRegistrationFormState(email = "alice@example.com", verificationCode = "123456")),
            aNativeRegistrationState(step = NativeRegistrationStep.Credentials, pendingRegistration = aPendingRegistration(), formState = aNativeRegistrationFormState(email = "alice@example.com", username = "alice", password = "password123")),
            aNativeRegistrationState(registerAction = AsyncData.Loading()),
            aNativeRegistrationState(registerAction = AsyncData.Failure(Exception("An error occurred"))),
        )
}

fun aNativeRegistrationState(
    accountProvider: AccountProvider = anAccountProvider(),
    formState: NativeRegistrationFormState = NativeRegistrationFormState.Default,
    step: NativeRegistrationStep = NativeRegistrationStep.Email,
    registerAction: AsyncData<SessionId> = AsyncData.Uninitialized,
    pendingRegistration: PendingRegistration? = null,
    eventSink: (NativeRegistrationEvents) -> Unit = {},
) = NativeRegistrationState(
    accountProvider = accountProvider,
    formState = formState,
    step = step,
    registerAction = registerAction,
    pendingRegistration = pendingRegistration,
    eventSink = eventSink,
)

fun aNativeRegistrationFormState(
    email: String = "",
    verificationCode: String = "",
    username: String = "",
    password: String = "",
) = NativeRegistrationFormState(
    email = email,
    verificationCode = verificationCode,
    username = username,
    password = password,
)

fun aPendingRegistration() = PendingRegistration(
    homeserverUrl = "https://arcana.celesteai.ru",
    email = "alice@example.com",
    clientSecret = "secret",
    sendAttempt = 1,
    sid = "sid",
)
