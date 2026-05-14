/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.nativeauth.FakeMatrixNativeAuthService
import io.element.android.features.login.impl.nativeauth.PasswordResetResult
import io.element.android.libraries.architecture.AsyncData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class PasswordResetPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state starts on email step`() = runTest {
        createPasswordResetPresenter().test {
            val initialState = awaitItem()
            assertThat(initialState.step).isEqualTo(PasswordResetStep.Email)
            assertThat(initialState.formState).isEqualTo(PasswordResetFormState.Default)
            assertThat(initialState.resetAction).isEqualTo(AsyncData.Uninitialized)
            assertThat(initialState.pendingPasswordReset).isNull()
            assertThat(initialState.submitEnabled).isFalse()
        }
    }

    @Test
    fun `present - submit email starts verification flow`() = runTest {
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(aPendingPasswordReset())
                )
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            val emailState = awaitItem()
            assertThat(emailState.formState.email).isEqualTo("alice@example.com")
            emailState.eventSink.invoke(PasswordResetEvents.Submit)
            val loadingState = awaitItem()
            assertThat(loadingState.resetAction).isInstanceOf(AsyncData.Loading::class.java)
            val codeState = awaitItem()
            assertThat(codeState.step).isEqualTo(PasswordResetStep.Code)
            assertThat(codeState.pendingPasswordReset).isNotNull()
            assertThat(codeState.formState.verificationCode).isEmpty()
            assertThat(codeState.submitEnabled).isFalse()
        }
    }

    @Test
    fun `present - submit email always requests a fresh reset`() = runTest {
        var startCalls = 0
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                startCalls += 1
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(
                        aPendingPasswordReset().copy(email = "alice@example.com")
                    )
                )
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            awaitItem()
            assertThat(startCalls).isEqualTo(1)

            val codeState = awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.GoBack)
            val emailState = awaitItem()
            emailState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            awaitItem()
            val retriedCodeState = awaitItem()
            assertThat(retriedCodeState.step).isEqualTo(PasswordResetStep.Code)
            assertThat(startCalls).isEqualTo(2)
        }
    }

    @Test
    fun `present - submit verification code opens credentials step`() = runTest {
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(aPendingPasswordReset())
                )
            },
            submitPasswordResetEmailCodeResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingCredentials(aPendingPasswordReset())
                )
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val codeState = awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.SetVerificationCode("123456"))
            val verificationState = awaitItem()
            assertThat(verificationState.formState.verificationCode).isEqualTo("123456")
            verificationState.eventSink.invoke(PasswordResetEvents.Submit)
            val loadingState = awaitItem()
            assertThat(loadingState.resetAction).isInstanceOf(AsyncData.Loading::class.java)
            val credentialsState = awaitItem()
            assertThat(credentialsState.step).isEqualTo(PasswordResetStep.Credentials)
            assertThat(credentialsState.pendingPasswordReset).isNotNull()
        }
    }

    @Test
    fun `present - submit password changes the password`() = runTest {
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(aPendingPasswordReset())
                )
            },
            submitPasswordResetEmailCodeResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingCredentials(aPendingPasswordReset())
                )
            },
            continuePasswordResetResult = { _, _ ->
                Result.success(PasswordResetResult.Success)
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val codeState = awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.SetVerificationCode("123456"))
            awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val credentialsState = awaitItem()
            credentialsState.eventSink.invoke(PasswordResetEvents.SetNewPassword("password123"))
            val passwordState = awaitItem()
            passwordState.eventSink.invoke(PasswordResetEvents.SetConfirmPassword("password123"))
            val confirmedState = awaitItem()
            confirmedState.eventSink.invoke(PasswordResetEvents.Submit)
            val loadingState = awaitItem()
            assertThat(loadingState.resetAction).isInstanceOf(AsyncData.Loading::class.java)
            val successState = awaitItem()
            assertThat(successState.resetAction).isEqualTo(AsyncData.Success(Unit))
            assertThat(successState.pendingPasswordReset).isNull()
        }
    }

    @Test
    fun `present - go back from credentials returns to code step`() = runTest {
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(aPendingPasswordReset())
                )
            },
            submitPasswordResetEmailCodeResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingCredentials(aPendingPasswordReset())
                )
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val codeState = awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.SetVerificationCode("123456"))
            awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val credentialsState = awaitItem()
            credentialsState.eventSink.invoke(PasswordResetEvents.GoBack)
            val backState = awaitItem()
            assertThat(backState.step).isEqualTo(PasswordResetStep.Code)
        }
    }

    @Test
    fun `present - resend email keeps the flow open`() = runTest {
        val nativeAuthService = FakeMatrixNativeAuthService(
            startPasswordResetResult = { _, _ ->
                Result.success(
                    PasswordResetResult.AwaitingEmailVerification(aPendingPasswordReset())
                )
            },
            resendPasswordResetEmailResult = { pending ->
                Result.success(pending.copy(sendAttempt = pending.sendAttempt + 1, sid = "new-sid"))
            }
        )
        createPasswordResetPresenter(nativeAuthService = nativeAuthService).test {
            val initialState = awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.SetEmail("alice@example.com"))
            awaitItem()
            initialState.eventSink.invoke(PasswordResetEvents.Submit)
            awaitItem()
            val codeState = awaitItem()
            codeState.eventSink.invoke(PasswordResetEvents.ResendEmail)
            val loadingState = awaitItem()
            assertThat(loadingState.resetAction).isInstanceOf(AsyncData.Loading::class.java)
            val refreshedState = awaitItem()
            assertThat(refreshedState.pendingPasswordReset?.sendAttempt).isEqualTo(2)
            assertThat(refreshedState.resetAction).isEqualTo(AsyncData.Uninitialized)
        }
    }

    private fun createPasswordResetPresenter(
        nativeAuthService: FakeMatrixNativeAuthService = FakeMatrixNativeAuthService(),
        accountProviderDataSource: AccountProviderDataSource = AccountProviderDataSource(FakeEnterpriseService()),
    ): PasswordResetPresenter = PasswordResetPresenter(
        initialEmail = "",
        accountProviderDataSource = accountProviderDataSource,
        nativeAuthService = nativeAuthService,
    )
}
