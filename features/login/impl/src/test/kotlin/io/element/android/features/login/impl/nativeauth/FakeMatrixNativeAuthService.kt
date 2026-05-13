/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.simulateLongTask

class FakeMatrixNativeAuthService(
    var startRegistrationResult: (String, String) -> Result<RegistrationResult> = { _, _ -> lambdaError() },
    var submitRegistrationEmailCodeResult: (PendingRegistration, String) -> Result<RegistrationResult> = { _, _ -> lambdaError() },
    var finishRegistrationResult: (PendingRegistration, String?, String) -> Result<RegistrationResult> = { _, _, _ -> lambdaError() },
    var resendRegistrationEmailResult: (PendingRegistration) -> Result<PendingRegistration> = { _ -> lambdaError() },
    var startPasswordResetResult: (String, String) -> Result<PasswordResetResult> = { _, _ -> lambdaError() },
    var submitPasswordResetEmailCodeResult: (PendingPasswordReset, String) -> Result<PasswordResetResult> = { _, _ -> lambdaError() },
    var continuePasswordResetResult: (PendingPasswordReset, String) -> Result<PasswordResetResult> = { _, _ -> lambdaError() },
    var resendPasswordResetEmailResult: (PendingPasswordReset) -> Result<PendingPasswordReset> = { _ -> lambdaError() },
    var startEmailLoginResult: (String, String, String) -> Result<EmailLoginResult> = { _, _, _ -> lambdaError() },
    var continueEmailLoginResult: (PendingEmailLogin) -> Result<EmailLoginResult> = { _ -> lambdaError() },
    var resendEmailLoginCodeResult: (PendingEmailLogin) -> Result<PendingEmailLogin> = { _ -> lambdaError() },
) : MatrixNativeAuthService {
    override suspend fun startRegistration(
        homeserverUrl: String,
        email: String,
    ): Result<RegistrationResult> = simulateLongTask {
        startRegistrationResult(homeserverUrl, email)
    }

    override suspend fun submitRegistrationEmailCode(
        pendingRegistration: PendingRegistration,
        verificationCode: String,
    ): Result<RegistrationResult> = simulateLongTask {
        submitRegistrationEmailCodeResult(pendingRegistration, verificationCode)
    }

    override suspend fun finishRegistration(
        pendingRegistration: PendingRegistration,
        username: String?,
        password: String,
    ): Result<RegistrationResult> = simulateLongTask {
        finishRegistrationResult(pendingRegistration, username, password)
    }

    override suspend fun resendRegistrationEmail(
        pendingRegistration: PendingRegistration,
    ): Result<PendingRegistration> = simulateLongTask {
        resendRegistrationEmailResult(pendingRegistration)
    }

    override suspend fun startPasswordReset(
        homeserverUrl: String,
        email: String,
    ): Result<PasswordResetResult> = simulateLongTask {
        startPasswordResetResult(homeserverUrl, email)
    }

    override suspend fun submitPasswordResetEmailCode(
        pendingPasswordReset: PendingPasswordReset,
        verificationCode: String,
    ): Result<PasswordResetResult> = simulateLongTask {
        submitPasswordResetEmailCodeResult(pendingPasswordReset, verificationCode)
    }

    override suspend fun continuePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
        newPassword: String,
    ): Result<PasswordResetResult> = simulateLongTask {
        continuePasswordResetResult(pendingPasswordReset, newPassword)
    }

    override suspend fun resendPasswordResetEmail(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PendingPasswordReset> = simulateLongTask {
        resendPasswordResetEmailResult(pendingPasswordReset)
    }

    override suspend fun startEmailLogin(
        homeserverUrl: String,
        login: String,
        password: String,
    ): Result<EmailLoginResult> = simulateLongTask {
        startEmailLoginResult(homeserverUrl, login, password)
    }

    override suspend fun continueEmailLogin(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<EmailLoginResult> = simulateLongTask {
        continueEmailLoginResult(pendingEmailLogin)
    }

    override suspend fun resendEmailLoginCode(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<PendingEmailLogin> = simulateLongTask {
        resendEmailLoginCodeResult(pendingEmailLogin)
    }
}
