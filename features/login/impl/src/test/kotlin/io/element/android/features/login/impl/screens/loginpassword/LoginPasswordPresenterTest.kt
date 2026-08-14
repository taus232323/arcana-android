/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.nativeauth.EmailLoginResult
import io.element.android.features.login.impl.nativeauth.FakeMatrixNativeAuthService
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_PASSWORD
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.A_USER_NAME_2
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class LoginPasswordPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        createLoginPasswordPresenter().test {
            val initialState = awaitItem()
            assertThat(initialState.accountProvider.url).isEqualTo(AuthenticationConfig.MATRIX_ORG_URL)
            assertThat(initialState.formState).isEqualTo(LoginFormState.Default)
            assertThat(initialState.loginAction).isEqualTo(AsyncData.Uninitialized)
            assertThat(initialState.pendingEmailLogin).isNull()
            assertThat(initialState.submitEnabled).isFalse()
        }
    }

    @Test
    fun `present - initial login is in the first state and can be modified`() = runTest {
        createLoginPasswordPresenter(
            initialLogin = A_USER_NAME,
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.formState.login).isEqualTo(A_USER_NAME)
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME_2))
            val loginChangedState = awaitItem()
            assertThat(loginChangedState.formState.login).isEqualTo(A_USER_NAME_2)
        }
    }

    @Test
    fun `present - submit starts email verification`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.success(
                    EmailLoginResult.AwaitingEmailVerification(aPendingEmailLogin())
                )
            }
        )
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
            authenticationService = FakeMatrixAuthenticationService(
                importCreatedSessionLambda = { _, _ -> Result.success(A_SESSION_ID) }
            ),
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            val loadingState = awaitItem()
            assertThat(loadingState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val awaitingState = awaitItem()
            assertThat(awaitingState.pendingEmailLogin).isNotNull()
            assertThat(awaitingState.formState.password).isEmpty()
            assertThat(awaitingState.formState.verificationCode).isEmpty()
            assertThat(awaitingState.submitEnabled).isFalse()
        }
    }

    @Test
    fun `present - submit verification code logs the user in`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.success(
                    EmailLoginResult.AwaitingEmailVerification(aPendingEmailLogin())
                )
            },
            continueEmailLoginResult = { _ ->
                Result.success(
                    EmailLoginResult.Success(
                        externalSession = anExternalSession()
                    )
                )
            }
        )
        var capturedBootstrapPassword: String? = null
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
            authenticationService = FakeMatrixAuthenticationService(
                importCreatedSessionLambda = { _, identityBootstrapPassword ->
                    capturedBootstrapPassword = identityBootstrapPassword
                    Result.success(A_SESSION_ID)
                }
            ),
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            awaitItem()
            val awaitingState = awaitItem()
            awaitingState.eventSink.invoke(LoginPasswordEvents.SetVerificationCode("123456"))
            val codeState = awaitItem()
            assertThat(codeState.formState.verificationCode).isEqualTo("123456")
            codeState.eventSink.invoke(LoginPasswordEvents.Submit)
            val loadingState = awaitItem()
            assertThat(loadingState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val loggedInState = awaitItem()
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
            assertThat(loggedInState.pendingEmailLogin).isNull()
            assertThat(capturedBootstrapPassword).isEqualTo(A_PASSWORD)
        }
    }

    @Test
    fun `present - resend verification code keeps login flow open`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.success(
                    EmailLoginResult.AwaitingEmailVerification(aPendingEmailLogin())
                )
            },
            resendEmailLoginCodeResult = { pending ->
                Result.success(pending.copy(sendAttempt = pending.sendAttempt + 1, sid = "new-sid"))
            }
        )
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            awaitItem()
            val awaitingState = awaitItem()
            awaitingState.eventSink.invoke(LoginPasswordEvents.ResendVerificationCode)
            val loadingState = awaitItem()
            assertThat(loadingState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val refreshedState = awaitItem()
            assertThat(refreshedState.pendingEmailLogin?.sendAttempt).isEqualTo(2)
            assertThat(refreshedState.loginAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    @Test
    fun `present - go back from verification returns to credentials form`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.success(
                    EmailLoginResult.AwaitingEmailVerification(aPendingEmailLogin())
                )
            }
        )
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            awaitItem()
            val awaitingState = awaitItem()
            awaitingState.eventSink.invoke(LoginPasswordEvents.GoBack)
            val cancelledState = awaitItem()
            assertThat(cancelledState.pendingEmailLogin).isNotNull()
            assertThat(cancelledState.step).isEqualTo(LoginPasswordStep.Credentials)
        }
    }

    @Test
    fun `present - submit with error`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.failure(AN_EXCEPTION)
            }
        )
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            val submitState = awaitItem()
            assertThat(submitState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val errorState = awaitItem()
            assertThat(errorState.loginAction).isEqualTo(AsyncData.Failure<SessionId>(AN_EXCEPTION))
        }
    }

    @Test
    fun `present - clear error`() = runTest {
        val emailLoginService = FakeMatrixNativeAuthService(
            startEmailLoginResult = { _, _, _ ->
                Result.failure(AN_EXCEPTION)
            }
        )
        createLoginPasswordPresenter(
            emailLoginService = emailLoginService,
        ).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(LoginPasswordEvents.SetLogin(A_USER_NAME))
            initialState.eventSink.invoke(LoginPasswordEvents.SetPassword(A_PASSWORD))
            skipItems(1)
            val loginAndPasswordState = awaitItem()
            loginAndPasswordState.eventSink.invoke(LoginPasswordEvents.Submit)
            awaitItem()
            val errorState = awaitItem()
            assertThat(errorState.loginAction).isEqualTo(AsyncData.Failure<SessionId>(AN_EXCEPTION))
            errorState.eventSink(LoginPasswordEvents.ClearError)
            val clearedState = awaitItem()
            assertThat(clearedState.loginAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    private fun createLoginPasswordPresenter(
        initialLogin: String = "",
        authenticationService: FakeMatrixAuthenticationService = FakeMatrixAuthenticationService(
            importCreatedSessionLambda = { _, _ -> Result.success(A_SESSION_ID) }
        ),
        emailLoginService: FakeMatrixNativeAuthService = FakeMatrixNativeAuthService(),
        accountProviderDataSource: AccountProviderDataSource = AccountProviderDataSource(FakeEnterpriseService()),
    ): LoginPasswordPresenter = LoginPasswordPresenter(
        initialLogin = initialLogin,
        authenticationService = authenticationService,
        emailLoginService = emailLoginService,
        accountProviderDataSource = accountProviderDataSource,
    )
}

private fun anExternalSession() = io.element.android.libraries.matrix.api.auth.external.ExternalSession(
    userId = "@user:server",
    deviceId = "device",
    accessToken = "token",
    refreshToken = null,
    homeserverUrl = "https://matrix.example.org",
)
