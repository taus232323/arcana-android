/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.external.ExternalSession
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.serialization.json.jsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import java.util.UUID

interface MatrixNativeAuthService {
    suspend fun startRegistration(
        homeserverUrl: String,
        username: String,
        password: String,
        email: String,
        registrationToken: String,
    ): Result<RegistrationResult>

    suspend fun continueRegistration(
        pendingRegistration: PendingRegistration,
    ): Result<RegistrationResult>

    suspend fun resendRegistrationEmail(
        pendingRegistration: PendingRegistration,
    ): Result<PendingRegistration>

    suspend fun startPasswordReset(
        homeserverUrl: String,
        email: String,
        newPassword: String,
    ): Result<PasswordResetResult>

    suspend fun continuePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PasswordResetResult>

    suspend fun resendPasswordResetEmail(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PendingPasswordReset>
}

@ContributesBinding(AppScope::class)
@Inject
class DefaultMatrixNativeAuthService(
    private val retrofitFactory: RetrofitFactory,
    private val jsonProvider: JsonProvider,
) : MatrixNativeAuthService {
    override suspend fun startRegistration(
        homeserverUrl: String,
        username: String,
        password: String,
        email: String,
        registrationToken: String,
    ): Result<RegistrationResult> = runCatchingExceptions {
        val baseUrl = homeserverUrl.toApiBaseUrl()
        advanceRegistration(
            pendingRegistration = PendingRegistration(
                homeserverUrl = baseUrl,
                username = username.trim(),
                password = password,
                email = email.trim(),
                registrationToken = registrationToken.trim(),
                clientSecret = UUID.randomUUID().toString(),
                sendAttempt = 0,
                sid = null,
                session = null,
                completedStages = emptyList(),
                flows = emptyList(),
            ),
            response = api(baseUrl).register(
                RegisterRequest(
                    username = username.trim(),
                    password = password,
                    initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
                )
            ),
            attemptedStage = null,
        )
    }

    override suspend fun continueRegistration(
        pendingRegistration: PendingRegistration,
    ): Result<RegistrationResult> = runCatchingExceptions {
        val next = pendingRegistration.withEmailSession()
        advanceRegistration(
            pendingRegistration = next,
            response = api(next.homeserverUrl).register(
                RegisterRequest(
                    username = next.username,
                    password = next.password,
                    initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
                    auth = AuthRequest(
                        type = EMAIL_IDENTITY_STAGE,
                        session = next.session,
                        threePidCreds = ThreePidCredentials(
                            clientSecret = next.clientSecret,
                            sid = requireNotNull(next.sid),
                        ),
                    ),
                )
            ),
            attemptedStage = EMAIL_IDENTITY_STAGE,
        )
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
        newPassword: String,
    ): Result<PasswordResetResult> = runCatchingExceptions {
        val baseUrl = homeserverUrl.toApiBaseUrl()
        advancePasswordReset(
            pendingPasswordReset = PendingPasswordReset(
                homeserverUrl = baseUrl,
                email = email,
                newPassword = newPassword,
                clientSecret = UUID.randomUUID().toString(),
                sendAttempt = 0,
                sid = null,
                session = null,
                completedStages = emptyList(),
                flows = emptyList(),
            ),
            response = api(baseUrl).resetPassword(
                ResetPasswordRequest(
                    newPassword = newPassword,
                )
            ),
            attemptedStage = null,
        )
    }

    override suspend fun continuePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PasswordResetResult> = runCatchingExceptions {
        val next = pendingPasswordReset.withEmailSession()
        advancePasswordReset(
            pendingPasswordReset = next,
            response = api(next.homeserverUrl).resetPassword(
                ResetPasswordRequest(
                    newPassword = next.newPassword,
                    auth = AuthRequest(
                        type = EMAIL_IDENTITY_STAGE,
                        session = next.session,
                        threePidCreds = ThreePidCredentials(
                            clientSecret = next.clientSecret,
                            sid = requireNotNull(next.sid),
                        ),
                    ),
                )
            ),
            attemptedStage = EMAIL_IDENTITY_STAGE,
        )
    }

    override suspend fun resendPasswordResetEmail(
        pendingPasswordReset: PendingPasswordReset,
    ): Result<PendingPasswordReset> = runCatchingExceptions {
        val updated = pendingPasswordReset.copy(sendAttempt = pendingPasswordReset.sendAttempt + 1)
        val response = api(updated.homeserverUrl).requestPasswordResetEmailToken(
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

    private suspend fun advanceRegistration(
        pendingRegistration: PendingRegistration,
        response: Response<RegisterResponse>,
        attemptedStage: String?,
    ): RegistrationResult {
        if (response.isSuccessful) {
            val body = requireNotNull(response.body())
            return RegistrationResult.Success(
                ExternalSession(
                    userId = body.userId,
                    deviceId = body.deviceId,
                    accessToken = body.accessToken,
                    refreshToken = body.refreshToken,
                    homeserverUrl = normalizeHomeserverUrl(
                        homeserverUrl = body.homeServer,
                        fallbackUrl = pendingRegistration.homeserverUrl,
                    ),
                )
            )
        }
        val uiaa = parseUiaa(response.errorBody())
        val updated = pendingRegistration.copy(
            session = uiaa.session ?: pendingRegistration.session,
            completedStages = uiaa.completed,
            flows = uiaa.flows,
        )
        val registrationFlow = updated.compatibleFlow()
        if (attemptedStage == EMAIL_IDENTITY_STAGE && EMAIL_IDENTITY_STAGE !in updated.completedStages) {
            return RegistrationResult.AwaitingEmailVerification(updated)
        }
        val nextStage = registrationFlow.firstOrNull { it !in updated.completedStages }
        return when (nextStage) {
            REGISTRATION_TOKEN_STAGE -> {
                if (updated.registrationToken.isBlank()) {
                    throw NativeAuthException.RegistrationTokenRequired
                }
                advanceRegistration(
                    pendingRegistration = updated,
                    response = api(updated.homeserverUrl).register(
                        RegisterRequest(
                            username = updated.username,
                            password = updated.password,
                            initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
                            auth = AuthRequest(
                                type = REGISTRATION_TOKEN_STAGE,
                                session = updated.session,
                                token = updated.registrationToken,
                            ),
                        )
                    ),
                    attemptedStage = REGISTRATION_TOKEN_STAGE,
                )
            }
            DUMMY_STAGE -> advanceRegistration(
                pendingRegistration = updated,
                response = api(updated.homeserverUrl).register(
                    RegisterRequest(
                        username = updated.username,
                        password = updated.password,
                        initialDeviceDisplayName = INITIAL_DEVICE_DISPLAY_NAME,
                        auth = AuthRequest(
                            type = DUMMY_STAGE,
                            session = updated.session,
                        ),
                    )
                ),
                attemptedStage = DUMMY_STAGE,
            )
            EMAIL_IDENTITY_STAGE -> {
                if (updated.email.isBlank()) {
                    throw NativeAuthException.EmailRequired
                }
                if (updated.sid == null) {
                    val emailResponse = api(updated.homeserverUrl).requestRegistrationEmailToken(
                        EmailRequestTokenRequest(
                            clientSecret = updated.clientSecret,
                            email = updated.email,
                            sendAttempt = updated.sendAttempt + 1,
                        )
                    )
                    if (!emailResponse.isSuccessful) {
                        throw parseError(emailResponse.errorBody())
                    }
                    RegistrationResult.AwaitingEmailVerification(
                        updated.copy(
                            sid = requireNotNull(emailResponse.body()).sid,
                            sendAttempt = updated.sendAttempt + 1,
                        )
                    )
                } else {
                    RegistrationResult.AwaitingEmailVerification(updated)
                }
            }
            null -> {
                if (attemptedStage == REGISTRATION_TOKEN_STAGE && REGISTRATION_TOKEN_STAGE !in updated.completedStages) {
                    throw NativeAuthException.InvalidRegistrationToken
                }
                throw NativeAuthException.UnsupportedAuthenticationFlow(updated.flows.map { it.stages })
            }
            else -> throw NativeAuthException.UnsupportedAuthenticationFlow(updated.flows.map { it.stages })
        }
    }

    private suspend fun advancePasswordReset(
        pendingPasswordReset: PendingPasswordReset,
        response: Response<Unit>,
        attemptedStage: String?,
    ): PasswordResetResult {
        if (response.isSuccessful) {
            return PasswordResetResult.Success
        }
        val uiaa = parseUiaa(response.errorBody())
        val updated = pendingPasswordReset.copy(
            session = uiaa.session ?: pendingPasswordReset.session,
            completedStages = uiaa.completed,
            flows = uiaa.flows,
        )
        val resetFlow = updated.compatibleFlow()
        if (attemptedStage == EMAIL_IDENTITY_STAGE && EMAIL_IDENTITY_STAGE !in updated.completedStages) {
            return PasswordResetResult.AwaitingEmailVerification(updated)
        }
        val nextStage = resetFlow.firstOrNull { it !in updated.completedStages }
        return when (nextStage) {
            DUMMY_STAGE -> advancePasswordReset(
                pendingPasswordReset = updated,
                response = api(updated.homeserverUrl).resetPassword(
                    ResetPasswordRequest(
                        newPassword = updated.newPassword,
                        auth = AuthRequest(
                            type = DUMMY_STAGE,
                            session = updated.session,
                        ),
                    )
                ),
                attemptedStage = DUMMY_STAGE,
            )
            EMAIL_IDENTITY_STAGE -> {
                if (updated.sid == null) {
                    val emailResponse = api(updated.homeserverUrl).requestPasswordResetEmailToken(
                        EmailRequestTokenRequest(
                            clientSecret = updated.clientSecret,
                            email = updated.email,
                            sendAttempt = updated.sendAttempt + 1,
                        )
                    )
                    if (!emailResponse.isSuccessful) {
                        throw parseError(emailResponse.errorBody())
                    }
                    PasswordResetResult.AwaitingEmailVerification(
                        updated.copy(
                            sid = requireNotNull(emailResponse.body()).sid,
                            sendAttempt = updated.sendAttempt + 1,
                        )
                    )
                } else {
                    PasswordResetResult.AwaitingEmailVerification(updated)
                }
            }
            null -> throw NativeAuthException.UnsupportedAuthenticationFlow(updated.flows.map { it.stages })
            else -> throw NativeAuthException.UnsupportedAuthenticationFlow(updated.flows.map { it.stages })
        }
    }

    private fun parseUiaa(errorBody: ResponseBody?): UiaaResponse {
        val payload = errorBody?.string()
        return runCatchingExceptions {
            requireNotNull(payload)
            val response = jsonProvider().decodeFromString(UiaaResponse.serializer(), payload)
            if (response.session == null && response.completed.isEmpty() && response.flows.isEmpty()) {
                throw parseError(payload)
            }
            response
        }.getOrElse {
            throw parseError(payload)
        }
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
            "M_INVALID_USERNAME" -> NativeAuthException.InvalidUsername
            "M_USER_IN_USE" -> NativeAuthException.UsernameInUse
            "M_THREEPID_IN_USE" -> NativeAuthException.EmailAlreadyInUse
            "M_THREEPID_DENIED" -> NativeAuthException.InvalidEmail
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

    private fun PendingRegistration.compatibleFlow(): List<String> {
        return flows.firstOrNull { flow ->
            flow.stages.isNotEmpty() && flow.stages.all { it in knownRegistrationStages }
        }?.stages.orEmpty()
    }

    private fun PendingPasswordReset.compatibleFlow(): List<String> {
        return flows.firstOrNull { flow ->
            flow.stages.isNotEmpty() && flow.stages.all { it in knownPasswordResetStages }
        }?.stages.orEmpty()
    }

    private fun PendingRegistration.withEmailSession(): PendingRegistration {
        check(session != null) { "UIAA session is required for email verification." }
        check(sid != null) { "Email sid is required for email verification." }
        return this
    }

    private fun PendingPasswordReset.withEmailSession(): PendingPasswordReset {
        check(session != null) { "UIAA session is required for email verification." }
        check(sid != null) { "Email sid is required for email verification." }
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
        const val DUMMY_STAGE = "m.login.dummy"
        const val EMAIL_IDENTITY_STAGE = "m.login.email.identity"
        const val REGISTRATION_TOKEN_STAGE = "m.login.registration_token"

        val knownRegistrationStages = setOf(
            REGISTRATION_TOKEN_STAGE,
            EMAIL_IDENTITY_STAGE,
            DUMMY_STAGE,
        )

        val knownPasswordResetStages = setOf(
            EMAIL_IDENTITY_STAGE,
            DUMMY_STAGE,
        )
    }
}

sealed interface RegistrationResult {
    data class AwaitingEmailVerification(
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

    data object Success : PasswordResetResult
}

data class PendingRegistration(
    val homeserverUrl: String,
    val username: String,
    val password: String,
    val email: String,
    val registrationToken: String,
    val clientSecret: String,
    val sendAttempt: Int,
    val sid: String?,
    val session: String?,
    val completedStages: List<String>,
    val flows: List<UiaaFlow>,
)

data class PendingPasswordReset(
    val homeserverUrl: String,
    val email: String,
    val newPassword: String,
    val clientSecret: String,
    val sendAttempt: Int,
    val sid: String?,
    val session: String?,
    val completedStages: List<String>,
    val flows: List<UiaaFlow>,
)

sealed class NativeAuthException(message: String? = null) : Exception(message) {
    data object EmailRequired : NativeAuthException()
    data object RegistrationTokenRequired : NativeAuthException()
    data object InvalidUsername : NativeAuthException()
    data object UsernameInUse : NativeAuthException()
    data object InvalidEmail : NativeAuthException()
    data object EmailAlreadyInUse : NativeAuthException()
    data object InvalidRegistrationToken : NativeAuthException()
    data class RateLimited(val retryAfterMs: Long?) : NativeAuthException()
    data class UnsupportedAuthenticationFlow(val flows: List<List<String>>) : NativeAuthException()
    data class MessageError(val details: String?) : NativeAuthException(details)
}
