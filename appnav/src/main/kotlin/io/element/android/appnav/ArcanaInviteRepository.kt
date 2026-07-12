/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlin.coroutines.cancellation.CancellationException

data class ArcanaInviteDetails(
    val token: String,
    val inviterDisplayName: String?,
    val inviterUserId: String?,
    val roomName: String?,
    val roomId: RoomId?,
    val webUrl: String?,
    val expiresAt: String?,
    val isUsed: Boolean,
    val isDm: Boolean,
)

interface ArcanaInviteRepository {
    suspend fun loadInvite(token: String): Result<ArcanaInviteDetails>
    suspend fun acceptInvite(token: String): Result<RoomId>
}

class MissingSessionForInviteAcceptException : IllegalStateException("No logged-in session available to accept invite")

@ContributesBinding(AppScope::class)
class DefaultArcanaInviteRepository : ArcanaInviteRepository {
    private val sessionStore: SessionStore
    private val api: ArcanaInviteApi

    @Inject constructor(
        sessionStore: SessionStore,
        retrofitFactory: RetrofitFactory,
    ) {
        this.sessionStore = sessionStore
        this.api = retrofitFactory.create(ARCANA_INVITE_BASE_URL).create(ArcanaInviteApi::class.java)
    }

    internal constructor(
        sessionStore: SessionStore,
        api: ArcanaInviteApi,
    ) {
        this.sessionStore = sessionStore
        this.api = api
    }

    override suspend fun loadInvite(token: String): Result<ArcanaInviteDetails> {
        return try {
            Result.success(api.getInvite(token).toDetails())
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    override suspend fun acceptInvite(token: String): Result<RoomId> {
        return try {
            val accessToken = sessionStore.getLatestSession()?.accessToken
                ?: return Result.failure(MissingSessionForInviteAcceptException())
            Result.success(RoomId(api.acceptInvite(token, "Bearer $accessToken").roomId))
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun ArcanaInviteResponse.toDetails(): ArcanaInviteDetails {
        return ArcanaInviteDetails(
            token = token,
            inviterDisplayName = inviter?.displayName,
            inviterUserId = inviter?.userId,
            roomName = room?.roomName,
            roomId = room?.roomId?.let(::RoomId),
            webUrl = webUrl,
            expiresAt = expiresAt,
            isUsed = used == true,
            isDm = room?.isDm == true || kind == "dm",
        )
    }
}

private const val ARCANA_INVITE_BASE_URL = "https://arcana.celesteai.ru"
