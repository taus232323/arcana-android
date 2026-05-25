/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.accountprovider.anAccountProvider
import io.element.android.features.login.impl.nativeauth.PendingPasswordReset
import io.element.android.features.login.impl.nativeauth.UiaaFlow
import io.element.android.libraries.architecture.AsyncData

open class PasswordResetStateProvider : PreviewParameterProvider<PasswordResetState> {
    override val values: Sequence<PasswordResetState>
        get() = sequenceOf(
            aPasswordResetState(),
            aPasswordResetState(
                step = PasswordResetStep.Email,
                formState = aPasswordResetFormState(email = "alice@example.com"),
            ),
            aPasswordResetState(
                step = PasswordResetStep.Email,
                resetAction = AsyncData.Loading(),
                formState = aPasswordResetFormState(email = "alice@example.com"),
            ),
            aPasswordResetState(
                step = PasswordResetStep.Code,
                pendingPasswordReset = aPendingPasswordReset(),
                formState = aPasswordResetFormState(
                    email = "alice@example.com",
                    verificationCode = "123456",
                ),
            ),
            aPasswordResetState(
                step = PasswordResetStep.Credentials,
                pendingPasswordReset = aPendingPasswordReset(),
                formState = aPasswordResetFormState(
                    email = "alice@example.com",
                    verificationCode = "123456",
                    newPassword = "password123",
                    confirmPassword = "password123",
                ),
            ),
            aPasswordResetState(resetAction = AsyncData.Success(Unit)),
            aPasswordResetState(resetAction = AsyncData.Failure(Exception("An error occurred"))),
        )
}

fun aPasswordResetState(
    accountProvider: AccountProvider = anAccountProvider(),
    formState: PasswordResetFormState = PasswordResetFormState.Default,
    step: PasswordResetStep = PasswordResetStep.Email,
    resetAction: AsyncData<Unit> = AsyncData.Uninitialized,
    pendingPasswordReset: PendingPasswordReset? = null,
    eventSink: (PasswordResetEvents) -> Unit = {},
) = PasswordResetState(
    accountProvider = accountProvider,
    formState = formState,
    step = step,
    resetAction = resetAction,
    pendingPasswordReset = pendingPasswordReset,
    eventSink = eventSink,
)

fun aPasswordResetFormState(
    email: String = "",
    verificationCode: String = "",
    newPassword: String = "",
    confirmPassword: String = "",
) = PasswordResetFormState(
    email = email,
    verificationCode = verificationCode,
    newPassword = newPassword,
    confirmPassword = confirmPassword,
)

fun aPendingPasswordReset() = PendingPasswordReset(
    homeserverUrl = "https://arcana.celesteai.ru",
    email = "alice@example.com",
    clientSecret = "secret",
    sendAttempt = 1,
    sid = "sid",
    session = "session",
    completedStages = emptyList(),
    flows = listOf(UiaaFlow(stages = listOf("m.login.email.identity", "m.login.dummy"))),
)
