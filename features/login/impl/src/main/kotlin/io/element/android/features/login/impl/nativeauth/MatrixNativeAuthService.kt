/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import android.os.Parcelable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.external.ExternalSession
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.serialization.json.jsonObject
import kotlinx.parcelize.Parcelize
import okhttp3.ResponseBody
import retrofit2.Response
import timber.log.Timber
import java.util.UUID

interface MatrixNativeAuthService {
    suspend fun startRegistration(
        homeserverUrl: String,
        email: String,
    ): Result<RegistrationResult>

    suspend fun submitRegistrationEmailCode(
        pendingRegistration: PendingRegistration,
        verificationCode: String,
    ): Result<RegistrationResult>

    /**
     * Completes the email-first registration flow.
     *
     * First-run bootstrap with `m.login.registration_token` is intentionally not surfaced in this UX.
     * If the homeserver requires that path, use the raw `/register` API fallback for the initial admin.
     */
    suspend fun finishRegistration(
        pendingRegistration: PendingRegistration,
        username: String?,
        password: String,
    ): Result<RegistrationResult>

    suspend fun resendRegistrationEmail(
        pendingRegistration: PendingRegistration,
    ): Result<PendingRegistration>

    suspend fun startPasswordReset(
        homeserverUrl: String,
        email: String,
    ): Result<PasswordResetResult>

    suspend fun submitPasswordResetEmailCode(
        pendingPasswordReset: PendingPasswordReset,
        verificationCode: String,
    ): Result<PasswordResetResult>

    suspend fun continuePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
        newPassword: String,
    ): Result<PasswordResetResult>

    suspend fun resendPasswordResetEmail(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PendingPasswordReset>

    suspend fun startEmailLogin(
        homeserverUrl: String,
        login: String,
        password: String,
    ): Result<EmailLoginResult>

    suspend fun continueEmailLogin(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<EmailLoginResult>

    suspend fun resendEmailLoginCode(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<PendingEmailLogin>
}

@ContributesBinding(AppScope::class)
@Inject
class DefaultMatrixNativeAuthService(
    private val retrofitFactory: RetrofitFactory,
    private val jsonProvider: JsonProvider,
) : MatrixNativeAuthService {
    override suspend fun startRegistration(
        homeserverUrl: String,
        email: String,
    ): Result<RegistrationResult> = runCatchingExceptions {
        if (email.isBlank()) {
            throw NativeAuthException.EmailRequired
        }
        val baseUrl = homeserverUrl.toApiBaseUrl()
        val pendingRegistration = PendingRegistration(
            homeserverUrl = baseUrl,
            email = email.trim(),
            clientSecret = UUID.randomUUID().toString(),
            sendAttempt = 1,
            sid = null,
        )
        val response = api(baseUrl).requestRegistrationEmailToken(
            EmailRequestTokenRequest(
                clientSecret = pendingRegistration.clientSecret,
                email = pendingRegistration.email,
                sendAttempt = pendingRegistration.sendAttempt,
            )
        )
        if (response.isSuccessful) {
            RegistrationResult.AwaitingEmailVerification(
                pendingRegistration.copy(sid = requireNotNull(response.body()).sid)
            )
        } else {
            throw parseError(response.errorBody())
        }
    }

    override suspend fun submitRegistrationEmailCode(
        pendingRegistration: PendingRegistration,
        verificationCode: String,
    ): Result<RegistrationResult> = runCatchingExceptions {
        val response = api(pendingRegistration.homeserverUrl).submitRegistrationEmailToken(
            RegistrationEmailSubmitRequest(
                clientSecret = pendingRegistration.clientSecret,
                sid = requireNotNull(pendingRegistration.sid),
                token = verificationCode,
            )
        )
        if (response.isSuccessful) {
            RegistrationResult.AwaitingCredentials(
                pendingRegistration.copy(sid = requireNotNull(response.body()).sid)
            )
        } else {
            throw when (val error = parseError(response.errorBody())) {
                NativeAuthException.InvalidCredentials -> NativeAuthException.InvalidVerificationCode
                is NativeAuthException.MessageError -> NativeAuthException.InvalidVerificationCode
                else -> error
            }
        }
    }

    override suspend fun finishRegistration(
        pendingRegistration: PendingRegistration,
        username: String?,
        password: String,
    ): Result<RegistrationResult> = runCatchingExceptions {
        val response = api(pendingRegistration.homeserverUrl).register(
            RegisterRequest(
                email = pendingRegistration.email,
                clientSecret = pendingRegistration.clientSecret,
                sid = requireNotNull(pendingRegistration.sid),
                password = password,
                username = username?.trim()?.takeIf { it.isNotBlank() },
                initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
            )
        )
        if (response.isSuccessful) {
            RegistrationResult.Success(
                externalSession = requireNotNull(response.body()).toExternalSession(pendingRegistration.homeserverUrl)
            )
        } else {
            throw parseError(response.errorBody())
        }
    }

    override suspend fun resendRegistrationEmail(
        pendingRegistration: PendingRegistration,
    ): Result<PendingRegistration> = runCatchingExceptions {
        val updated = pendingRegistration.copy(sendAttempt = pendingRegistration.sendAttempt + 1)
        val response = api(updated.homeserverUrl).requestRegistrationEmailToken(
            EmailRequestTokenRequest(
                clientSecret = updated.clientSecret,
                email = updated.email,
                sendAttempt = updated.sendAttempt,
            )
        )
        if (response.isSuccessful) {
            updated.copy(sid = requireNotNull(response.body()).sid)
        } else {
            throw parseError(response.errorBody())
        }
    }

    override suspend fun startPasswordReset(
        homeserverUrl: String,
        email: String,
    ): Result<PasswordResetResult> = runCatchingExceptions {
        Timber.tag("PasswordReset").d("startPasswordReset: begin")
        if (email.isBlank()) {
            throw NativeAuthException.EmailRequired
        }
        val baseUrl = homeserverUrl.toApiBaseUrl()
        val pendingPasswordReset = PendingPasswordReset(
            homeserverUrl = baseUrl,
            email = email.trim(),
            clientSecret = UUID.randomUUID().toString(),
            sendAttempt = 1,
            sid = null,
            session = null,
            completedStages = emptyList(),
            flows = emptyList(),
        )
        val response = api(baseUrl).requestPasswordResetEmailToken(
            EmailRequestTokenRequest(
                clientSecret = pendingPasswordReset.clientSecret,
                email = pendingPasswordReset.email,
                sendAttempt = pendingPasswordReset.sendAttempt,
            )
        )
        if (response.isSuccessful) {
            Timber.tag("PasswordReset").d("startPasswordReset: requestToken success")
            PasswordResetResult.AwaitingEmailVerification(
                pendingPasswordReset.copy(sid = requireNotNull(response.body()).sid)
            )
        } else {
            Timber.tag("PasswordReset").d("startPasswordReset: requestToken failed")
            throw parseError(response.errorBody())
        }
    }

    override suspend fun submitPasswordResetEmailCode(
        pendingPasswordReset: PendingPasswordReset,
        verificationCode: String,
    ): Result<PasswordResetResult> = runCatchingExceptions {
        Timber.tag("PasswordReset").d("submitPasswordResetEmailCode: begin")
        val response = api(pendingPasswordReset.homeserverUrl).submitPasswordResetEmailToken(
            RegistrationEmailSubmitRequest(
                clientSecret = pendingPasswordReset.clientSecret,
                sid = requireNotNull(pendingPasswordReset.sid),
                token = verificationCode,
            )
        )
        if (response.isSuccessful) {
            Timber.tag("PasswordReset").d("submitPasswordResetEmailCode: submitToken success")
            PasswordResetResult.AwaitingCredentials(
                pendingPasswordReset.copy(sid = requireNotNull(response.body()).sid)
            )
        } else {
            Timber.tag("PasswordReset").d("submitPasswordResetEmailCode: submitToken failed")
            throw when (val error = parseError(response.errorBody())) {
                NativeAuthException.InvalidCredentials -> NativeAuthException.InvalidVerificationCode
                is NativeAuthException.MessageError -> NativeAuthException.InvalidVerificationCode
                else -> error
            }
        }
    }

    override suspend fun continuePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
        newPassword: String,
    ): Result<PasswordResetResult> = runCatchingExceptions {
        Timber.tag("PasswordReset").d("continuePasswordReset: begin")
        val response = api(pendingPasswordReset.homeserverUrl).resetPassword(
            ResetPasswordRequest(
                password = newPassword,
                clientSecret = pendingPasswordReset.clientSecret,
                sid = requireNotNull(pendingPasswordReset.sid),
                logoutDevices = false,
            )
        )
        if (response.isSuccessful) {
            Timber.tag("PasswordReset").d("continuePasswordReset: reset success")
            PasswordResetResult.Success
        } else {
            Timber.tag("PasswordReset").d("continuePasswordReset: reset failed")
            throw parseError(response.errorBody())
        }
    }

    override suspend fun resendPasswordResetEmail(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PendingPasswordReset> = runCatchingExceptions {
        Timber.tag("PasswordReset").d("resendPasswordResetEmail: begin")
        val updated = pendingPasswordReset.copy(sendAttempt = pendingPasswordReset.sendAttempt + 1)
        val response = api(updated.homeserverUrl).requestPasswordResetEmailToken(
            EmailRequestTokenRequest(
                clientSecret = updated.clientSecret,
                email = updated.email,
                sendAttempt = updated.sendAttempt,
            )
        )
        if (response.isSuccessful) {
            Timber.tag("PasswordReset").d("resendPasswordResetEmail: requestToken success")
            updated.copy(sid = requireNotNull(response.body()).sid)
        } else {
            Timber.tag("PasswordReset").d("resendPasswordResetEmail: requestToken failed")
            throw parseError(response.errorBody())
        }
    }

    override suspend fun startEmailLogin(
        homeserverUrl: String,
        login: String,
        password: String,
    ): Result<EmailLoginResult> = runCatchingExceptions {
        val baseUrl = homeserverUrl.toApiBaseUrl()
        val pendingEmailLogin = PendingEmailLogin(
            homeserverUrl = baseUrl,
            login = login.trim(),
            password = password,
            clientSecret = UUID.randomUUID().toString(),
            sendAttempt = 0,
            sid = null,
            email = null,
        )
        val response = requestEmailLoginVerification(pendingEmailLogin)
        if (response.isSuccessful) {
            val body = requireNotNull(response.body())
            EmailLoginResult.AwaitingEmailVerification(
                pendingEmailLogin.copy(
                    sendAttempt = pendingEmailLogin.sendAttempt + 1,
                    sid = body.sid,
                    email = body.email ?: throw NativeAuthException.EmailVerificationUnavailable,
                )
            )
        } else {
            throw parseError(response.errorBody())
        }
    }

    override suspend fun continueEmailLogin(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<EmailLoginResult> = runCatchingExceptions {
        val next = pendingEmailLogin.withEmailSession()
        val response = api(next.homeserverUrl).submitEmailLoginVerification(
            EmailLoginConfirmationRequest(
                clientSecret = next.clientSecret,
                sid = requireNotNull(next.sid),
                token = next.verificationCode,
                deviceId = null,
                initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
            )
        )
        if (response.isSuccessful) {
            EmailLoginResult.Success(
                externalSession = requireNotNull(response.body()).toExternalSession(next.homeserverUrl)
            )
        } else {
            val error = parseError(response.errorBody())
            throw when (error) {
                is NativeAuthException.InvalidCredentials,
                is NativeAuthException.MessageError -> NativeAuthException.InvalidVerificationCode
                else -> error
            }
        }
    }

    override suspend fun resendEmailLoginCode(
        pendingEmailLogin: PendingEmailLogin,
    ): Result<PendingEmailLogin> = runCatchingExceptions {
        val updated = pendingEmailLogin.copy(sendAttempt = pendingEmailLogin.sendAttempt + 1)
        val response = requestEmailLoginVerification(updated, updated.sendAttempt)
        if (response.isSuccessful) {
            updated.copy(
                sid = requireNotNull(response.body()).sid,
                email = requireNotNull(response.body()).email,
            )
        } else {
            throw parseError(response.errorBody())
        }
    }

    private suspend fun requestEmailLoginVerification(
        pendingEmailLogin: PendingEmailLogin,
        sendAttempt: Int = pendingEmailLogin.sendAttempt + 1,
    ): Response<EmailLoginStartResponse> {
        return api(pendingEmailLogin.homeserverUrl).requestEmailLoginVerification(
            EmailLoginRequest(
                clientSecret = pendingEmailLogin.clientSecret,
                login = pendingEmailLogin.login,
                password = pendingEmailLogin.password,
                sendAttempt = sendAttempt,
            )
        )
    }

    private fun LoginResponse.toExternalSession(fallbackUrl: String): ExternalSession {
        return ExternalSession(
            userId = userId,
            deviceId = deviceId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            homeserverUrl = normalizeHomeserverUrl(
                homeserverUrl = homeServer,
                fallbackUrl = fallbackUrl,
            ),
        )
    }

    private fun RegisterResponse.toExternalSession(fallbackUrl: String): ExternalSession {
        return ExternalSession(
            userId = userId,
            deviceId = deviceId,
            accessToken = accessToken,
            refreshToken = refreshToken,
            homeserverUrl = normalizeHomeserverUrl(
                homeserverUrl = homeServer,
                fallbackUrl = fallbackUrl,
            ),
        )
    }

    private fun parseError(errorBody: ResponseBody?): Throwable {
        return parseError(errorBody?.string())
    }

    private fun parseError(payload: String?): Throwable {
        val response = runCatchingExceptions {
            payload ?: return@runCatchingExceptions null
            jsonProvider().decodeFromString(UiaaResponse.serializer(), payload)
        }.getOrNull()
        return when (response?.errcode) {
            "M_FORBIDDEN" -> NativeAuthException.InvalidCredentials
            "M_UNAUTHORIZED" -> NativeAuthException.InvalidCredentials
            "M_INVALID_USERNAME" -> NativeAuthException.InvalidUsername
            "M_USER_IN_USE" -> NativeAuthException.UsernameInUse
            "M_THREEPID_IN_USE" -> NativeAuthException.EmailAlreadyInUse
            "M_THREEPID_DENIED" -> NativeAuthException.InvalidEmail
            "M_INVALID_CREDENTIALS" -> NativeAuthException.InvalidCredentials
            "M_INVALID_TOKEN" -> NativeAuthException.InvalidVerificationCode
            "M_INVALID_REGISTRATION_TOKEN" -> NativeAuthException.InvalidRegistrationToken
            "M_REGISTRATION_TOKEN_INVALID" -> NativeAuthException.InvalidRegistrationToken
            "M_EMAIL_LOGIN_CODE_EXPIRED" -> NativeAuthException.InvalidVerificationCode
            "M_NOT_FOUND" -> NativeAuthException.EmailVerificationUnavailable
            "M_LIMIT_EXCEEDED" -> {
                val retryAfterMs = runCatchingExceptions {
                    payload ?: return@runCatchingExceptions null
                    jsonProvider().parseToJsonElement(payload)
                        .jsonObject["retry_after_ms"]
                        ?.toString()
                        ?.trim('"')
                        ?.toLongOrNull()
                }.getOrNull()
                NativeAuthException.RateLimited(retryAfterMs)
            }
            else -> NativeAuthException.MessageError(response?.error)
        }
    }

    private fun api(baseUrl: String): MatrixNativeAuthAPI {
        return retrofitFactory.create(baseUrl).create(MatrixNativeAuthAPI::class.java)
    }

    private fun PendingEmailLogin.withEmailSession(): PendingEmailLogin {
        check(sid != null) { "Email sid is required for email verification." }
        check(email != null) { "Email is required for email verification." }
        return this
    }

    private fun String.toApiBaseUrl(): String {
        return if (this == AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL) {
            AuthenticationConfig.DEFAULT_HOMESERVER_URL
        } else {
            this
        }
    }

    private fun normalizeHomeserverUrl(homeserverUrl: String?, fallbackUrl: String): String {
        return homeserverUrl
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: fallbackUrl
    }

    private companion object {
        const val EMAIL_IDENTITY_STAGE = "m.login.email.identity"
    }
}

sealed interface RegistrationResult {
    data class AwaitingEmailVerification(
        val pendingRegistration: PendingRegistration,
    ) : RegistrationResult

    data class AwaitingCredentials(
        val pendingRegistration: PendingRegistration,
    ) : RegistrationResult

    data class Success(
        val externalSession: ExternalSession,
    ) : RegistrationResult
}

sealed interface PasswordResetResult {
    data class AwaitingEmailVerification(
        val pendingPasswordReset: PendingPasswordReset,
    ) : PasswordResetResult

    data class AwaitingCredentials(
        val pendingPasswordReset: PendingPasswordReset,
    ) : PasswordResetResult

    data object Success : PasswordResetResult
}

sealed interface EmailLoginResult {
    data class AwaitingEmailVerification(
        val pendingEmailLogin: PendingEmailLogin,
    ) : EmailLoginResult

    data class Success(
        val externalSession: ExternalSession,
    ) : EmailLoginResult
}

@Parcelize
data class PendingRegistration(
    val homeserverUrl: String,
    val email: String,
    val clientSecret: String,
    val sendAttempt: Int,
    val sid: String?,
) : Parcelable

data class PendingPasswordReset(
    val homeserverUrl: String,
    val email: String,
    val clientSecret: String,
    val sendAttempt: Int,
    val sid: String?,
    val session: String?,
    val completedStages: List<String>,
    val flows: List<UiaaFlow>,
)

@Parcelize
data class PendingEmailLogin(
    val homeserverUrl: String,
    val login: String,
    val password: String,
    val clientSecret: String,
    val sendAttempt: Int,
    val sid: String?,
    val email: String?,
    val verificationCode: String = "",
) : Parcelable

sealed class NativeAuthException(message: String? = null) : Exception(message) {
    data object EmailRequired : NativeAuthException()
    data object InvalidUsername : NativeAuthException()
    data object UsernameInUse : NativeAuthException()
    data object InvalidEmail : NativeAuthException()
    data object EmailAlreadyInUse : NativeAuthException()
    data object InvalidRegistrationToken : NativeAuthException()
    data object InvalidCredentials : NativeAuthException()
    data object InvalidVerificationCode : NativeAuthException()
    data object EmailVerificationUnavailable : NativeAuthException()
    data class RateLimited(val retryAfterMs: Long?) : NativeAuthException()
    data class UnsupportedAuthenticationFlow(val flows: List<List<String>>) : NativeAuthException()
    data class MessageError(val details: String?) : NativeAuthException(details)
}
