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
import io.element.android.features.login.impl.nativeauth.UiaaFlow
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId

open class NativeRegistrationStateProvider : PreviewParameterProvider<NativeRegistrationState> {
    override val values: Sequence<NativeRegistrationState>
        get() = sequenceOf(
            aNativeRegistrationState(),
            aNativeRegistrationState(registerAction = AsyncData.Loading()),
            aNativeRegistrationState(
                pendingRegistration = aPendingRegistration(),
                formState = aNativeRegistrationFormState(
                    username = "alice",
                    email = "alice@example.com",
                    registrationToken = "celeste-token",
                    password = "password123",
                    confirmPassword = "password123",
                ),
            ),
            aNativeRegistrationState(registerAction = AsyncData.Failure(Exception("An error occurred"))),
        )
}

fun aNativeRegistrationState(
    accountProvider: AccountProvider = anAccountProvider(),
    formState: NativeRegistrationFormState = NativeRegistrationFormState.Default,
    registerAction: AsyncData<SessionId> = AsyncData.Uninitialized,
    pendingRegistration: PendingRegistration? = null,
    eventSink: (NativeRegistrationEvents) -> Unit = {},
) = NativeRegistrationState(
    accountProvider = accountProvider,
    formState = formState,
    registerAction = registerAction,
    pendingRegistration = pendingRegistration,
    eventSink = eventSink,
)

fun aNativeRegistrationFormState(
    username: String = "",
    email: String = "",
    registrationToken: String = "",
    password: String = "",
    confirmPassword: String = "",
) = NativeRegistrationFormState(
    username = username,
    email = email,
    registrationToken = registrationToken,
    password = password,
    confirmPassword = confirmPassword,
)

fun aPendingRegistration() = PendingRegistration(
    homeserverUrl = "https://matrix.celesteai.ru",
    username = "alice",
    password = "password123",
    email = "alice@example.com",
    registrationToken = "celeste-token",
    clientSecret = "secret",
    sendAttempt = 1,
    sid = "sid",
    session = "session",
    completedStages = listOf("m.login.registration_token"),
    flows = listOf(UiaaFlow(stages = listOf("m.login.registration_token", "m.login.email.identity", "m.login.dummy"))),
)
