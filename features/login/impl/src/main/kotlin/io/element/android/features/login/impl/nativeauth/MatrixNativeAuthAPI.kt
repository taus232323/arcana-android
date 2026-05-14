/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import io.element.android.appconfig.ApplicationConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

internal interface MatrixNativeAuthAPI {
    @POST("/_matrix/client/v3/register")
    suspend fun register(
        @Body body: RegisterRequest,
    ): Response<RegisterResponse>

    @POST("/_matrix/client/v3/register/email/requestToken")
    suspend fun requestRegistrationEmailToken(
        @Body body: EmailRequestTokenRequest,
    ): Response<EmailRequestTokenResponse>

    @POST("/_matrix/client/v3/register/email/submitToken")
    suspend fun submitRegistrationEmailToken(
        @Body body: RegistrationEmailSubmitRequest,
    ): Response<EmailRequestTokenResponse>

    @POST("/_matrix/client/v3/account/password/email/reset")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest,
    ): Response<Unit>

    @POST("/_matrix/client/v3/account/password/email/requestToken")
    suspend fun requestPasswordResetEmailToken(
        @Body body: EmailRequestTokenRequest,
    ): Response<EmailRequestTokenResponse>

    @POST("/_matrix/client/v3/account/password/email/submitToken")
    suspend fun submitPasswordResetEmailToken(
        @Body body: RegistrationEmailSubmitRequest,
    ): Response<EmailRequestTokenResponse>

    @POST("/_matrix/client/v3/login")
    suspend fun requestEmailLoginVerification(
        @Body body: EmailLoginRequest,
    ): Response<EmailLoginStartResponse>

    @POST("/_matrix/client/v3/login")
    suspend fun submitEmailLoginVerification(
        @Body body: EmailLoginConfirmationRequest,
    ): Response<LoginResponse>
}

internal const val INITIAL_DEVICE_DISPLAY_NAME = ApplicationConfig.APPLICATION_NAME + " Android"

@Serializable
internal data class RegisterRequest(
    val email: String,
    @SerialName("client_secret")
    val clientSecret: String,
    val sid: String,
    val password: String,
    val username: String? = null,
    @SerialName("device_id")
    val deviceId: String? = null,
    @SerialName("initial_device_display_name")
    val initialDeviceDisplayName: String,
    @SerialName("inhibit_login")
    val inhibitLogin: Boolean = false,
)

@Serializable
internal data class ResetPasswordRequest(
    val password: String,
    @SerialName("client_secret")
    val clientSecret: String,
    val sid: String,
    @SerialName("logout_devices")
    val logoutDevices: Boolean,
)

@Serializable
internal data class RegisterResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("home_server")
    val homeServer: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
)

@Serializable
internal data class EmailRequestTokenRequest(
    @SerialName("client_secret")
    val clientSecret: String,
    val email: String,
    @SerialName("send_attempt")
    val sendAttempt: Int,
)

@Serializable
internal data class EmailRequestTokenResponse(
    val sid: String,
)

@Serializable
internal data class RegistrationEmailSubmitRequest(
    @SerialName("client_secret")
    val clientSecret: String,
    val sid: String,
    val token: String,
)

@Serializable
internal data class EmailLoginRequest(
    @SerialName("client_secret")
    val clientSecret: String,
    val login: String,
    val identifier: JsonObject = loginIdentifier(login),
    val password: String,
    @SerialName("send_attempt")
    val sendAttempt: Int,
)

private fun loginIdentifier(login: String): JsonObject {
    val sanitizedLogin = login.trim()
    return if (sanitizedLogin.isEmailAddress()) {
        buildJsonObject {
            put("type", JsonPrimitive("m.id.thirdparty"))
            put("medium", JsonPrimitive("email"))
            put("address", JsonPrimitive(sanitizedLogin))
        }
    } else {
        buildJsonObject {
            put("type", JsonPrimitive("m.id.user"))
            put("user", JsonPrimitive(sanitizedLogin))
        }
    }
}

private fun String.isEmailAddress(): Boolean {
    val atIndex = indexOf('@')
    return atIndex > 0 &&
        atIndex == lastIndexOf('@') &&
        atIndex < lastIndex &&
        substring(atIndex + 1).contains('.')
}

@Serializable
internal data class EmailLoginStartResponse(
    val sid: String,
    val email: String? = null,
)

@Serializable
internal data class EmailLoginConfirmationRequest(
    @SerialName("client_secret")
    val clientSecret: String,
    val sid: String,
    val token: String,
    @SerialName("device_id")
    val deviceId: String? = null,
    @SerialName("initial_device_display_name")
    val initialDeviceDisplayName: String? = null,
)

@Serializable
internal data class LoginResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("home_server")
    val homeServer: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
)

@Serializable
internal data class AuthRequest(
    val type: String,
    val session: String? = null,
    val token: String? = null,
    @SerialName("threepid_creds")
    val threePidCreds: ThreePidCredentials? = null,
)

@Serializable
internal data class ThreePidCredentials(
    @SerialName("client_secret")
    val clientSecret: String,
    val sid: String,
)

@Serializable
internal data class UiaaResponse(
    val session: String? = null,
    val completed: List<String> = emptyList(),
    val flows: List<UiaaFlow> = emptyList(),
    val errcode: String? = null,
    val error: String? = null,
)

@Serializable
data class UiaaFlow(
    val stages: List<String> = emptyList(),
)
