/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.nativeauth.MatrixNativeAuthService
import io.element.android.features.login.impl.nativeauth.PasswordResetResult
import io.element.android.features.login.impl.nativeauth.PendingPasswordReset
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@AssistedInject
class PasswordResetPresenter(
    @Assisted private val initialEmail: String,
    private val accountProviderDataSource: AccountProviderDataSource,
    private val nativeAuthService: MatrixNativeAuthService,
) : Presenter<PasswordResetState> {
    @AssistedFactory
    interface Factory {
        fun create(initialEmail: String): PasswordResetPresenter
    }

    @Composable
    override fun present(): PasswordResetState {
        val localCoroutineScope = rememberCoroutineScope()
        val resetAction = remember { mutableStateOf<AsyncData<Unit>>(AsyncData.Uninitialized) }
        val pendingPasswordReset = remember { mutableStateOf<PendingPasswordReset?>(null) }
        val formState = rememberSaveable {
            mutableStateOf(
                PasswordResetFormState(
                    email = initialEmail,
                    newPassword = "",
                    confirmPassword = "",
                )
            )
        }
        val accountProvider by accountProviderDataSource.flow.collectAsState()

        fun handleEvent(event: PasswordResetEvents) {
            when (event) {
                PasswordResetEvents.ClearError -> resetAction.value = AsyncData.Uninitialized
                PasswordResetEvents.ClearSuccess -> resetAction.value = AsyncData.Uninitialized
                PasswordResetEvents.ConfirmEmailVerified -> {
                    localCoroutineScope.continueReset(
                        pendingPasswordReset = pendingPasswordReset,
                        resetAction = resetAction,
                    )
                }
                PasswordResetEvents.ResendEmail -> {
                    localCoroutineScope.resendEmail(
                        pendingPasswordReset = pendingPasswordReset,
                        resetAction = resetAction,
                    )
                }
                is PasswordResetEvents.SetConfirmPassword -> updateFormState(formState) {
                    copy(confirmPassword = event.confirmPassword)
                }
                is PasswordResetEvents.SetEmail -> updateFormState(formState) {
                    copy(email = event.email)
                }
                is PasswordResetEvents.SetNewPassword -> updateFormState(formState) {
                    copy(newPassword = event.newPassword)
                }
                PasswordResetEvents.Submit -> {
                    localCoroutineScope.startReset(
                        homeserverUrl = accountProvider.url,
                        formState = formState.value,
                        pendingPasswordReset = pendingPasswordReset,
                        resetAction = resetAction,
                    )
                }
            }
        }

        return PasswordResetState(
            accountProvider = accountProvider,
            formState = formState.value,
            resetAction = resetAction.value,
            pendingPasswordReset = pendingPasswordReset.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.startReset(
        homeserverUrl: String,
        formState: PasswordResetFormState,
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        resetAction: MutableState<AsyncData<Unit>>,
    ) = launch {
        resetAction.value = AsyncData.Loading()
        pendingPasswordReset.value = null
        if (formState.newPassword != formState.confirmPassword) {
            resetAction.value = AsyncData.Failure(PasswordResetValidationException.PasswordMismatch)
            return@launch
        }
        nativeAuthService.startPasswordReset(
            homeserverUrl = homeserverUrl,
            email = formState.email.trim(),
            newPassword = formState.newPassword,
        ).onSuccess { result ->
            handleResetResult(result, pendingPasswordReset, resetAction)
        }.onFailure { failure ->
            resetAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.continueReset(
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        resetAction: MutableState<AsyncData<Unit>>,
    ) = launch {
        val currentPendingPasswordReset = pendingPasswordReset.value ?: return@launch
        resetAction.value = AsyncData.Loading()
        nativeAuthService.continuePasswordReset(currentPendingPasswordReset)
            .onSuccess { result ->
                handleResetResult(result, pendingPasswordReset, resetAction)
            }
            .onFailure { failure ->
                resetAction.value = AsyncData.Failure(failure)
            }
    }

    private fun CoroutineScope.resendEmail(
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        resetAction: MutableState<AsyncData<Unit>>,
    ) = launch {
        val currentPendingPasswordReset = pendingPasswordReset.value ?: return@launch
        resetAction.value = AsyncData.Loading()
        nativeAuthService.resendPasswordResetEmail(currentPendingPasswordReset)
            .onSuccess { updatedPendingPasswordReset ->
                pendingPasswordReset.value = updatedPendingPasswordReset
                resetAction.value = AsyncData.Uninitialized
            }
            .onFailure { failure ->
                resetAction.value = AsyncData.Failure(failure)
            }
    }

    private fun handleResetResult(
        result: PasswordResetResult,
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        resetAction: MutableState<AsyncData<Unit>>,
    ) {
        when (result) {
            is PasswordResetResult.AwaitingEmailVerification -> {
                pendingPasswordReset.value = result.pendingPasswordReset
                resetAction.value = AsyncData.Uninitialized
            }
            PasswordResetResult.Success -> {
                pendingPasswordReset.value = null
                resetAction.value = AsyncData.Success(Unit)
            }
        }
    }

    private fun updateFormState(
        formState: MutableState<PasswordResetFormState>,
        updateLambda: PasswordResetFormState.() -> PasswordResetFormState,
    ) {
        formState.value = updateLambda(formState.value)
    }
}

sealed class PasswordResetValidationException : Exception() {
    data object PasswordMismatch : PasswordResetValidationException()
}
