/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Inject
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.nativeauth.NativeAuthException
import io.element.android.features.login.impl.nativeauth.MatrixNativeAuthService
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.features.login.impl.nativeauth.RegistrationResult
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Inject
class NativeRegistrationPresenter(
    private val matrixAuthenticationService: MatrixAuthenticationService,
    private val nativeAuthService: MatrixNativeAuthService,
    private val accountProviderDataSource: AccountProviderDataSource,
) : Presenter<NativeRegistrationState> {
    @Composable
    override fun present(): NativeRegistrationState {
        val localCoroutineScope = rememberCoroutineScope()
        val registerAction: MutableState<AsyncData<SessionId>> = remember {
            mutableStateOf(AsyncData.Uninitialized)
        }
        val step = rememberSaveable { mutableStateOf(NativeRegistrationStep.Email) }
        val pendingRegistration = rememberSaveable {
            mutableStateOf<PendingRegistration?>(null)
        }
        val formState = rememberSaveable {
            mutableStateOf(NativeRegistrationFormState.Default)
        }
        val resendBlockedUntilMs = rememberSaveable {
            mutableStateOf<Long?>(null)
        }

        LaunchedEffect(resendBlockedUntilMs.value) {
            val blockedUntilMs = resendBlockedUntilMs.value ?: return@LaunchedEffect
            val delayMs = blockedUntilMs - System.currentTimeMillis()
            if (delayMs > 0) {
                delay(delayMs)
            }
            if (resendBlockedUntilMs.value == blockedUntilMs) {
                resendBlockedUntilMs.value = null
            }
        }
        val accountProvider by accountProviderDataSource.flow.collectAsState()

        fun handleEvent(event: NativeRegistrationEvents) {
            when (event) {
                NativeRegistrationEvents.ClearError -> registerAction.value = AsyncData.Uninitialized
                NativeRegistrationEvents.GoBack -> {
                    when (step.value) {
                        NativeRegistrationStep.Email -> Unit
                        NativeRegistrationStep.Code -> {
                            step.value = NativeRegistrationStep.Email
                            updateFormState(formState) {
                                copy(verificationCode = "")
                            }
                        }
                        NativeRegistrationStep.Credentials -> {
                            step.value = NativeRegistrationStep.Code
                        }
                    }
                }
                NativeRegistrationEvents.ResendEmail -> {
                    localCoroutineScope.resendEmail(
                        pendingRegistration = pendingRegistration,
                        registerAction = registerAction,
                        resendBlockedUntilMs = resendBlockedUntilMs,
                    )
                }
                is NativeRegistrationEvents.SetEmail -> {
                    val sanitized = event.email.trim()
                    val previousEmail = formState.value.email.trim()
                    updateFormState(formState) {
                        copy(email = sanitized)
                    }
                    if (pendingRegistration.value != null && sanitized != previousEmail) {
                        step.value = NativeRegistrationStep.Email
                        pendingRegistration.value = null
                        updateFormState(formState) {
                            copy(verificationCode = "")
                        }
                    }
                }
                is NativeRegistrationEvents.SetVerificationCode -> updateFormState(formState) {
                    copy(verificationCode = event.verificationCode)
                }
                is NativeRegistrationEvents.SetPassword -> updateFormState(formState) {
                    copy(password = event.password)
                }
                is NativeRegistrationEvents.SetUsername -> updateFormState(formState) {
                    copy(username = event.username)
                }
                NativeRegistrationEvents.Submit -> {
                    when (step.value) {
                        NativeRegistrationStep.Email -> {
                            val currentEmail = formState.value.email.trim()
                            val currentPendingRegistration = pendingRegistration.value
                            if (currentPendingRegistration != null && currentPendingRegistration.email == currentEmail) {
                                step.value = NativeRegistrationStep.Code
                                updateFormState(formState) {
                                    copy(verificationCode = "")
                                }
                            } else {
                                localCoroutineScope.startRegistrationEmail(
                                    homeserverUrl = accountProvider.url,
                                    formState = formState,
                                    pendingRegistration = pendingRegistration,
                                    registerAction = registerAction,
                                    step = step,
                                )
                            }
                        }
                        NativeRegistrationStep.Code -> {
                            localCoroutineScope.submitRegistrationEmailCode(
                                formState = formState,
                                pendingRegistration = pendingRegistration,
                                registerAction = registerAction,
                                step = step,
                            )
                        }
                        NativeRegistrationStep.Credentials -> {
                            localCoroutineScope.finishRegistration(
                                formState = formState,
                                pendingRegistration = pendingRegistration,
                                registerAction = registerAction,
                            )
                        }
                    }
                }
            }
        }

        return NativeRegistrationState(
            accountProvider = accountProvider,
            formState = formState.value,
            step = step.value,
            registerAction = registerAction.value,
            pendingRegistration = pendingRegistration.value,
            canResendEmail = resendBlockedUntilMs.value == null,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.startRegistrationEmail(
        homeserverUrl: String,
        formState: MutableState<NativeRegistrationFormState>,
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
        step: MutableState<NativeRegistrationStep>,
    ) = launch {
        registerAction.value = AsyncData.Loading()
        pendingRegistration.value = null
        nativeAuthService.startRegistration(
            homeserverUrl = homeserverUrl,
            email = formState.value.email.trim(),
        ).onSuccess { result ->
            when (result) {
                is RegistrationResult.AwaitingEmailVerification -> {
                    pendingRegistration.value = result.pendingRegistration
                    step.value = NativeRegistrationStep.Code
                    updateFormState(formState) {
                        copy(verificationCode = "")
                    }
                    registerAction.value = AsyncData.Uninitialized
                }
                else -> {
                    registerAction.value = AsyncData.Failure(IllegalStateException("Unexpected registration state"))
                }
            }
        }.onFailure { failure ->
            registerAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.submitRegistrationEmailCode(
        formState: MutableState<NativeRegistrationFormState>,
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
        step: MutableState<NativeRegistrationStep>,
    ) = launch {
        val currentPendingRegistration = pendingRegistration.value ?: return@launch
        registerAction.value = AsyncData.Loading()
        nativeAuthService.submitRegistrationEmailCode(
            pendingRegistration = currentPendingRegistration,
            verificationCode = formState.value.verificationCode.trim(),
        ).onSuccess { result ->
            when (result) {
                is RegistrationResult.AwaitingCredentials -> {
                    pendingRegistration.value = result.pendingRegistration
                    step.value = NativeRegistrationStep.Credentials
                    registerAction.value = AsyncData.Uninitialized
                }
                else -> {
                    registerAction.value = AsyncData.Failure(IllegalStateException("Unexpected registration state"))
                }
            }
        }.onFailure { failure ->
            registerAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.resendEmail(
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
        resendBlockedUntilMs: MutableState<Long?>,
    ) = launch {
        val currentPendingRegistration = pendingRegistration.value ?: return@launch
        registerAction.value = AsyncData.Loading()
        nativeAuthService.resendRegistrationEmail(currentPendingRegistration)
            .onSuccess { updatedPendingRegistration ->
                resendBlockedUntilMs.value = null
                pendingRegistration.value = updatedPendingRegistration
                registerAction.value = AsyncData.Uninitialized
            }
            .onFailure { failure ->
                if (failure is NativeAuthException.RateLimited) {
                    failure.retryAfterMs?.let { retryAfterMs ->
                        resendBlockedUntilMs.value = System.currentTimeMillis() + retryAfterMs
                    }
                }
                registerAction.value = AsyncData.Failure(failure)
            }
    }

    private fun CoroutineScope.finishRegistration(
        formState: MutableState<NativeRegistrationFormState>,
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
    ) = launch {
        val currentPendingRegistration = pendingRegistration.value ?: return@launch
        registerAction.value = AsyncData.Loading()
        nativeAuthService.finishRegistration(
            pendingRegistration = currentPendingRegistration,
            username = formState.value.username.trim().takeIf { it.isNotBlank() },
            password = formState.value.password,
        ).onSuccess { result ->
            when (result) {
                is RegistrationResult.Success -> {
                    matrixAuthenticationService.importCreatedSession(
                        externalSession = result.externalSession,
                        identityBootstrapPassword = formState.value.password,
                    )
                        .onSuccess { sessionId ->
                            registerAction.value = AsyncData.Success(sessionId)
                        }
                        .onFailure { failure ->
                            registerAction.value = AsyncData.Failure(failure)
                        }
                }
                else -> {
                    registerAction.value = AsyncData.Failure(IllegalStateException("Unexpected registration state"))
                }
            }
        }.onFailure { failure ->
            registerAction.value = AsyncData.Failure(failure)
        }
    }

    private fun updateFormState(
        formState: MutableState<NativeRegistrationFormState>,
        updateLambda: NativeRegistrationFormState.() -> NativeRegistrationFormState,
    ) {
        formState.value = updateLambda(formState.value)
    }
}
