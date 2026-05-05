/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.element.android.features.login.impl.nativeauth.EmailLoginResult
import io.element.android.features.login.impl.nativeauth.MatrixNativeAuthService
import io.element.android.features.login.impl.nativeauth.PendingEmailLogin
import io.element.android.features.login.impl.nativeauth.NativeAuthException
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class LoginPasswordPresenter(
    @Assisted
    private val initialLogin: String,
    private val authenticationService: MatrixAuthenticationService,
    private val emailLoginService: MatrixNativeAuthService,
    private val accountProviderDataSource: AccountProviderDataSource,
) : Presenter<LoginPasswordState> {
    @AssistedFactory
    interface Factory {
        fun create(initialLogin: String): LoginPasswordPresenter
    }

    @Composable
    override fun present(): LoginPasswordState {
        val localCoroutineScope = rememberCoroutineScope()
        val loginAction: MutableState<AsyncData<SessionId>> = remember {
            mutableStateOf(AsyncData.Uninitialized)
        }
        val pendingEmailLogin = rememberSaveable {
            mutableStateOf<PendingEmailLogin?>(null)
        }
        val step = rememberSaveable { mutableStateOf(LoginPasswordStep.Credentials) }
        val formState = rememberSaveable {
            mutableStateOf(
                LoginFormState(
                    login = initialLogin,
                    password = "",
                    verificationCode = "",
                )
            )
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

        fun handleEvent(event: LoginPasswordEvents) {
            when (event) {
                is LoginPasswordEvents.SetLogin -> {
                    val sanitized = event.login.trim()
                    val previousLogin = formState.value.login.trim()
                    updateFormState(formState) {
                        copy(login = sanitized)
                    }
                    if (pendingEmailLogin.value != null && sanitized != previousLogin) {
                        pendingEmailLogin.value = null
                        step.value = LoginPasswordStep.Credentials
                        updateFormState(formState) {
                            copy(verificationCode = "")
                        }
                    }
                }
                is LoginPasswordEvents.SetPassword -> {
                    val previousPassword = formState.value.password
                    updateFormState(formState) {
                        copy(password = event.password)
                    }
                    if (pendingEmailLogin.value != null && event.password != previousPassword) {
                        pendingEmailLogin.value = null
                        step.value = LoginPasswordStep.Credentials
                        updateFormState(formState) {
                            copy(verificationCode = "")
                        }
                    }
                }
                is LoginPasswordEvents.SetVerificationCode -> updateFormState(formState) {
                    copy(verificationCode = event.verificationCode)
                }
                LoginPasswordEvents.Submit -> {
                    when (step.value) {
                        LoginPasswordStep.Credentials -> {
                            if (pendingEmailLogin.value == null) {
                                localCoroutineScope.startEmailLogin(
                                    homeserverUrl = accountProvider.url,
                                    formState = formState,
                                    pendingEmailLogin = pendingEmailLogin,
                                    loginAction = loginAction,
                                    step = step,
                                )
                            } else {
                                step.value = LoginPasswordStep.VerificationCode
                            }
                        }
                        LoginPasswordStep.VerificationCode -> {
                            localCoroutineScope.confirmEmailLogin(
                                formState = formState,
                                pendingEmailLogin = pendingEmailLogin,
                                loginAction = loginAction,
                            )
                        }
                    }
                }
                LoginPasswordEvents.ResendVerificationCode -> {
                    localCoroutineScope.resendVerificationCode(
                        pendingEmailLogin = pendingEmailLogin,
                        loginAction = loginAction,
                        resendBlockedUntilMs = resendBlockedUntilMs,
                    )
                }
                LoginPasswordEvents.GoBack -> {
                    step.value = LoginPasswordStep.Credentials
                }
                LoginPasswordEvents.ClearError -> loginAction.value = AsyncData.Uninitialized
            }
        }

        return LoginPasswordState(
            accountProvider = accountProvider,
            formState = formState.value,
            step = step.value,
            loginAction = loginAction.value,
            pendingEmailLogin = pendingEmailLogin.value,
            canResendVerificationCode = resendBlockedUntilMs.value == null,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.startEmailLogin(
        homeserverUrl: String,
        formState: MutableState<LoginFormState>,
        pendingEmailLogin: MutableState<PendingEmailLogin?>,
        loginAction: MutableState<AsyncData<SessionId>>,
        step: MutableState<LoginPasswordStep>,
    ) = launch {
        loginAction.value = AsyncData.Loading()
        pendingEmailLogin.value = null
        emailLoginService.startEmailLogin(
            homeserverUrl = homeserverUrl,
            login = formState.value.login.trim(),
            password = formState.value.password,
        ).onSuccess { result ->
            handleEmailLoginResult(
                result = result,
                formState = formState,
                pendingEmailLogin = pendingEmailLogin,
                loginAction = loginAction,
                step = step,
            )
        }.onFailure { failure ->
            loginAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.confirmEmailLogin(
        formState: MutableState<LoginFormState>,
        pendingEmailLogin: MutableState<PendingEmailLogin?>,
        loginAction: MutableState<AsyncData<SessionId>>,
    ) = launch {
        val currentPendingEmailLogin = pendingEmailLogin.value ?: return@launch
        loginAction.value = AsyncData.Loading()
        emailLoginService.continueEmailLogin(
            currentPendingEmailLogin.copy(
                verificationCode = formState.value.verificationCode.trim(),
            )
        ).onSuccess { result ->
            handleEmailLoginResult(
                result = result,
                formState = formState,
                pendingEmailLogin = pendingEmailLogin,
                loginAction = loginAction,
            )
        }.onFailure { failure ->
            loginAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.resendVerificationCode(
        pendingEmailLogin: MutableState<PendingEmailLogin?>,
        loginAction: MutableState<AsyncData<SessionId>>,
        resendBlockedUntilMs: MutableState<Long?>,
    ) = launch {
        val currentPendingEmailLogin = pendingEmailLogin.value ?: return@launch
        loginAction.value = AsyncData.Loading()
        emailLoginService.resendEmailLoginCode(currentPendingEmailLogin)
            .onSuccess { updatedPendingEmailLogin ->
                resendBlockedUntilMs.value = null
                pendingEmailLogin.value = updatedPendingEmailLogin
                loginAction.value = AsyncData.Uninitialized
            }
            .onFailure { failure ->
                if (failure is NativeAuthException.RateLimited) {
                    failure.retryAfterMs?.let { retryAfterMs ->
                        resendBlockedUntilMs.value = System.currentTimeMillis() + retryAfterMs
                    }
                }
                loginAction.value = AsyncData.Failure(failure)
            }
    }

    private suspend fun handleEmailLoginResult(
        result: EmailLoginResult,
        formState: MutableState<LoginFormState>,
        pendingEmailLogin: MutableState<PendingEmailLogin?>,
        loginAction: MutableState<AsyncData<SessionId>>,
        step: MutableState<LoginPasswordStep>? = null,
    ) {
        when (result) {
            is EmailLoginResult.AwaitingEmailVerification -> {
                pendingEmailLogin.value = result.pendingEmailLogin
                step?.value = LoginPasswordStep.VerificationCode
                formState.value = formState.value.copy(
                    password = "",
                    verificationCode = "",
                )
                loginAction.value = AsyncData.Uninitialized
            }
            is EmailLoginResult.Success -> {
                pendingEmailLogin.value = null
                step?.value = LoginPasswordStep.Credentials
                formState.value = formState.value.copy(
                    password = "",
                    verificationCode = "",
                )
                authenticationService.importCreatedSession(result.externalSession)
                    .onSuccess { sessionId ->
                        loginAction.value = AsyncData.Success(sessionId)
                    }
                    .onFailure { failure ->
                        loginAction.value = AsyncData.Failure(failure)
                    }
            }
        }
    }

    private fun updateFormState(
        formState: MutableState<LoginFormState>,
        updateLambda: LoginFormState.() -> LoginFormState,
    ) {
        formState.value = updateLambda(formState.value)
    }
}
