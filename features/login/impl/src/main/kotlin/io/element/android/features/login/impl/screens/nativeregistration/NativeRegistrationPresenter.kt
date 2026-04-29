/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import dev.zacsweers.metro.Inject
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.nativeauth.MatrixNativeAuthService
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.features.login.impl.nativeauth.RegistrationResult
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.CoroutineScope
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
        val pendingRegistration = remember { mutableStateOf<PendingRegistration?>(null) }
        val formState = rememberSaveable {
            mutableStateOf(NativeRegistrationFormState.Default)
        }
        val accountProvider by accountProviderDataSource.flow.collectAsState()

        fun handleEvent(event: NativeRegistrationEvents) {
            when (event) {
                NativeRegistrationEvents.ClearError -> registerAction.value = AsyncData.Uninitialized
                NativeRegistrationEvents.ConfirmEmailVerified -> {
                    localCoroutineScope.continueRegistration(
                        pendingRegistration = pendingRegistration,
                        registerAction = registerAction,
                    )
                }
                NativeRegistrationEvents.ResendEmail -> {
                    localCoroutineScope.resendEmail(
                        pendingRegistration = pendingRegistration,
                        registerAction = registerAction,
                    )
                }
                is NativeRegistrationEvents.SetConfirmPassword -> updateFormState(formState) {
                    copy(confirmPassword = event.confirmPassword)
                }
                is NativeRegistrationEvents.SetEmail -> updateFormState(formState) {
                    copy(email = event.email)
                }
                is NativeRegistrationEvents.SetPassword -> updateFormState(formState) {
                    copy(password = event.password)
                }
                is NativeRegistrationEvents.SetRegistrationToken -> updateFormState(formState) {
                    copy(registrationToken = event.registrationToken)
                }
                is NativeRegistrationEvents.SetUsername -> updateFormState(formState) {
                    copy(username = event.username)
                }
                NativeRegistrationEvents.Submit -> {
                    localCoroutineScope.startRegistration(
                        homeserverUrl = accountProvider.url,
                        formState = formState.value,
                        pendingRegistration = pendingRegistration,
                        registerAction = registerAction,
                    )
                }
            }
        }

        return NativeRegistrationState(
            accountProvider = accountProvider,
            formState = formState.value,
            registerAction = registerAction.value,
            pendingRegistration = pendingRegistration.value,
            eventSink = ::handleEvent,
        )
    }

    private fun CoroutineScope.startRegistration(
        homeserverUrl: String,
        formState: NativeRegistrationFormState,
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
    ) = launch {
        registerAction.value = AsyncData.Loading()
        pendingRegistration.value = null
        if (formState.password != formState.confirmPassword) {
            registerAction.value = AsyncData.Failure(NativeRegistrationValidationException.PasswordMismatch)
            return@launch
        }
        nativeAuthService.startRegistration(
            homeserverUrl = homeserverUrl,
            username = formState.username.trim(),
            password = formState.password,
            email = formState.email.trim(),
            registrationToken = formState.registrationToken.trim(),
        ).onSuccess { result ->
            handleRegistrationResult(result, pendingRegistration, registerAction)
        }.onFailure { failure ->
            registerAction.value = AsyncData.Failure(failure)
        }
    }

    private fun CoroutineScope.continueRegistration(
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
    ) = launch {
        val currentPendingRegistration = pendingRegistration.value ?: return@launch
        registerAction.value = AsyncData.Loading()
        nativeAuthService.continueRegistration(currentPendingRegistration)
            .onSuccess { result ->
                handleRegistrationResult(result, pendingRegistration, registerAction)
            }
            .onFailure { failure ->
                registerAction.value = AsyncData.Failure(failure)
            }
    }

    private fun CoroutineScope.resendEmail(
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
    ) = launch {
        val currentPendingRegistration = pendingRegistration.value ?: return@launch
        registerAction.value = AsyncData.Loading()
        nativeAuthService.resendRegistrationEmail(currentPendingRegistration)
            .onSuccess { updatedPendingRegistration ->
                pendingRegistration.value = updatedPendingRegistration
                registerAction.value = AsyncData.Uninitialized
            }
            .onFailure { failure ->
                registerAction.value = AsyncData.Failure(failure)
            }
    }

    private suspend fun handleRegistrationResult(
        result: RegistrationResult,
        pendingRegistration: MutableState<PendingRegistration?>,
        registerAction: MutableState<AsyncData<SessionId>>,
    ) {
        when (result) {
            is RegistrationResult.AwaitingEmailVerification -> {
                pendingRegistration.value = result.pendingRegistration
                registerAction.value = AsyncData.Uninitialized
            }
            is RegistrationResult.Success -> {
                pendingRegistration.value = null
                matrixAuthenticationService.importCreatedSession(result.externalSession)
                    .onSuccess { sessionId ->
                        registerAction.value = AsyncData.Success(sessionId)
                    }
                    .onFailure { failure ->
                        registerAction.value = AsyncData.Failure(failure)
                    }
            }
        }
    }

    private fun updateFormState(
        formState: MutableState<NativeRegistrationFormState>,
        updateLambda: NativeRegistrationFormState.() -> NativeRegistrationFormState,
    ) {
        formState.value = updateLambda(formState.value)
    }
}

sealed class NativeRegistrationValidationException : Exception() {
    data object PasswordMismatch : NativeRegistrationValidationException()
}
