/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface ArcanaInviteApi {
    @GET("api/invite/{token}")
    suspend fun getInvite(@Path("token") token: String): ArcanaInviteResponse

    @POST("api/invite/{token}/accept")
    suspend fun acceptInvite(
        @Path("token") token: String,
        @Header("Authorization") authorization: String,
    ): ArcanaInviteAcceptResponse
}

@Serializable
internal data class ArcanaInviteResponse(
    val token: String,
    val kind: String? = null,
    val inviter: ArcanaInviteIdentityResponse? = null,
    val target: ArcanaInviteIdentityResponse? = null,
    val room: ArcanaInviteRoomResponse? = null,
    val webUrl: String? = null,
    val expiresAt: String? = null,
    val used: Boolean? = null,
)

@Serializable
internal data class ArcanaInviteIdentityResponse(
    val userId: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
internal data class ArcanaInviteRoomResponse(
    val roomId: String? = null,
    val roomName: String? = null,
    val isDm: Boolean? = null,
)

@Serializable
internal data class ArcanaInviteAcceptResponse(
    val roomId: String,
    val isNew: Boolean? = null,
    val openRoom: Boolean? = null,
)
