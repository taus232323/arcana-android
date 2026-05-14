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
import timber.log.Timber

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
        val step = rememberSaveable { mutableStateOf(PasswordResetStep.Email) }
        val pendingPasswordReset = remember { mutableStateOf<PendingPasswordReset?>(null) }
        val formState = rememberSaveable {
            mutableStateOf(
                PasswordResetFormState(
                    email = initialEmail,
                    verificationCode = "",
                    newPassword = "",
                    confirmPassword = "",
                )
            )
        }
        val accountProvider by accountProviderDataSource.flow.collectAsState()

        fun handleEvent(event: PasswordResetEvents) {
            when (event) {
                PasswordResetEvents.ClearError -> resetAction.value = AsyncData.Uninitialized
                PasswordResetEvents.ClearSuccess -> {
                    Timber.tag("PasswordReset").d("ui: clear success")
                    resetAction.value = AsyncData.Uninitialized
                }
                PasswordResetEvents.ResendEmail -> {
                    Timber.tag("PasswordReset").d("ui: resend email from step=%s", step.value.name)
                    localCoroutineScope.resendEmail(
                        pendingPasswordReset = pendingPasswordReset,
                        resetAction = resetAction,
                    )
                }
                PasswordResetEvents.GoBack -> {
                    Timber.tag("PasswordReset").d("ui: go back from step=%s", step.value.name)
                    when (step.value) {
                        PasswordResetStep.Email -> Unit
                        PasswordResetStep.Code -> {
                            step.value = PasswordResetStep.Email
                            updateFormState(formState) {
                                copy(verificationCode = "")
                            }
                        }
                        PasswordResetStep.Credentials -> {
                            step.value = PasswordResetStep.Code
                        }
                    }
                }
                is PasswordResetEvents.SetConfirmPassword -> updateFormState(formState) {
                    Timber.tag("PasswordReset").d("ui: confirm password changed")
                    copy(confirmPassword = event.confirmPassword)
                }
                is PasswordResetEvents.SetEmail -> {
                    Timber.tag("PasswordReset").d("ui: email changed")
                    val sanitized = event.email.trim()
                    val previousEmail = formState.value.email.trim()
                    updateFormState(formState) {
                        copy(email = sanitized)
                    }
                    if (pendingPasswordReset.value != null && sanitized != previousEmail) {
                        pendingPasswordReset.value = null
                        step.value = PasswordResetStep.Email
                        updateFormState(formState) {
                            copy(
                                verificationCode = "",
                                newPassword = "",
                                confirmPassword = "",
                            )
                        }
                    }
                }
                is PasswordResetEvents.SetVerificationCode -> updateFormState(formState) {
                    Timber.tag("PasswordReset").d("ui: verification code changed")
                    copy(verificationCode = event.verificationCode)
                }
                is PasswordResetEvents.SetNewPassword -> updateFormState(formState) {
                    Timber.tag("PasswordReset").d("ui: new password changed")
                    copy(newPassword = event.newPassword)
                }
                PasswordResetEvents.Submit -> {
                    Timber.tag("PasswordReset").d("ui: submit from step=%s", step.value.name)
                    when (step.value) {
                        PasswordResetStep.Email -> {
                            localCoroutineScope.startReset(
                                homeserverUrl = accountProvider.url,
                                email = formState.value.email.trim(),
                                pendingPasswordReset = pendingPasswordReset,
                                resetAction = resetAction,
                                step = step,
                                formState = formState,
                            )
                        }
                        PasswordResetStep.Code -> {
                            localCoroutineScope.submitResetCode(
                                pendingPasswordReset = pendingPasswordReset,
                                formState = formState,
                                resetAction = resetAction,
                                step = step,
                            )
                        }
                        PasswordResetStep.Credentials -> {
                            localCoroutineScope.finishReset(
                                pendingPasswordReset = pendingPasswordReset,
                                formState = formState,
                                resetAction = resetAction,
                            )
                        }
                    }
                }
            }
        }

        return PasswordResetState(
            accountProvider = accountProvider,
            formState = formState.value,
            step = step.value,
            resetAction = resetAction.value,
            pendingPasswordReset = pendingPasswordReset.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.startReset(
        homeserverUrl: String,
        email: String,
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        resetAction: MutableState<AsyncData<Unit>>,
        step: MutableState<PasswordResetStep>,
        formState: MutableState<PasswordResetFormState>,
    ) = launch {
        Timber.tag("PasswordReset").d("flow: start reset")
        resetAction.value = AsyncData.Loading()
        pendingPasswordReset.value = null
        nativeAuthService.startPasswordReset(
            homeserverUrl = homeserverUrl,
            email = email,
        ).onSuccess { result ->
            when (result) {
                is PasswordResetResult.AwaitingEmailVerification -> {
                    Timber.tag("PasswordReset").d("flow: awaiting email verification")
                    pendingPasswordReset.value = result.pendingPasswordReset
                    step.value = PasswordResetStep.Code
                    updateFormState(formState) {
                        copy(verificationCode = "")
                    }
                    resetAction.value = AsyncData.Uninitialized
                }
                else -> {
                    resetAction.value = AsyncData.Failure(IllegalStateException("Unexpected password reset state"))
                }
            }
        }.onFailure { failure ->
            resetAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.submitResetCode(
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        formState: MutableState<PasswordResetFormState>,
        resetAction: MutableState<AsyncData<Unit>>,
        step: MutableState<PasswordResetStep>,
    ) = launch {
        val currentPendingPasswordReset = pendingPasswordReset.value ?: return@launch
        Timber.tag("PasswordReset").d("flow: submit reset code")
        resetAction.value = AsyncData.Loading()
        nativeAuthService.submitPasswordResetEmailCode(
            pendingPasswordReset = currentPendingPasswordReset,
            verificationCode = formState.value.verificationCode.trim(),
        )
            .onSuccess { result ->
                when (result) {
                    is PasswordResetResult.AwaitingCredentials -> {
                        Timber.tag("PasswordReset").d("flow: code accepted, moving to credentials")
                        pendingPasswordReset.value = result.pendingPasswordReset
                        step.value = PasswordResetStep.Credentials
                        resetAction.value = AsyncData.Uninitialized
                    }
                    else -> {
                        resetAction.value = AsyncData.Failure(IllegalStateException("Unexpected password reset state"))
                    }
                }
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
        Timber.tag("PasswordReset").d("flow: resend email")
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

    private fun CoroutineScope.finishReset(
        pendingPasswordReset: MutableState<PendingPasswordReset?>,
        formState: MutableState<PasswordResetFormState>,
        resetAction: MutableState<AsyncData<Unit>>,
    ) = launch {
        val currentPendingPasswordReset = pendingPasswordReset.value ?: return@launch
        Timber.tag("PasswordReset").d("flow: finish reset")
        resetAction.value = AsyncData.Loading()
        if (formState.value.newPassword != formState.value.confirmPassword) {
            resetAction.value = AsyncData.Failure(PasswordResetValidationException.PasswordMismatch)
            return@launch
        }
        nativeAuthService.continuePasswordReset(
            pendingPasswordReset = currentPendingPasswordReset,
            newPassword = formState.value.newPassword,
        ).onSuccess { result ->
            when (result) {
                PasswordResetResult.Success -> {
                    Timber.tag("PasswordReset").d("flow: reset success")
                    pendingPasswordReset.value = null
                    resetAction.value = AsyncData.Success(Unit)
                }
                else -> {
                    resetAction.value = AsyncData.Failure(IllegalStateException("Unexpected password reset state"))
                }
            }
        }.onFailure { failure ->
            resetAction.value = AsyncData.Failure(failure)
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
