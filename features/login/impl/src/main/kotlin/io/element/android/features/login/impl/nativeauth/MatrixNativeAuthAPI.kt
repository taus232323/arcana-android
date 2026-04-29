/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    @POST("/_matrix/client/v3/account/password")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest,
    ): Response<Unit>

    @POST("/_matrix/client/v3/account/password/email/requestToken")
    suspend fun requestPasswordResetEmailToken(
        @Body body: EmailRequestTokenRequest,
    ): Response<EmailRequestTokenResponse>
}

internal const val INITIAL_DEVICE_DISPLAY_NAME = "MESSENGER_NAME Android"

@Serializable
internal data class RegisterRequest(
    val password: String,
    val username: String = "",
    @SerialName("initial_device_display_name")
    val initialDeviceDisplayName: String,
    val auth: AuthRequest? = null,
)

@Serializable
internal data class ResetPasswordRequest(
    @SerialName("new_password")
    val newPassword: String,
    @SerialName("logout_devices")
    val logoutDevices: Boolean = false,
    val auth: AuthRequest? = null,
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
