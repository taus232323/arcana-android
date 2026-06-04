/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.appconfig.ArcanaConfiguration
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.network.RetrofitFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException
import retrofit2.http.Header
import retrofit2.http.POST

data class ArcanaInviteShareResult(
    val token: String,
    val webUrl: String?,
)

interface ArcanaInviteShareRepository {
    suspend fun createInvite(): Result<ArcanaInviteShareResult>
}

@ContributesBinding(io.element.android.libraries.di.SessionScope::class)
class DefaultArcanaInviteShareRepository(
    private val matrixClient: MatrixClient,
    private val sessionStore: SessionStore,
    retrofitFactory: RetrofitFactory,
) : ArcanaInviteShareRepository {
    private val api = retrofitFactory.create(ArcanaConfiguration.ARCANA_INVITE_BASE_URL).create(ArcanaInviteShareApi::class.java)

    override suspend fun createInvite(): Result<ArcanaInviteShareResult> {
        return try {
            val session = sessionStore.getSession(matrixClient.sessionId.value)
                ?: return Result.failure(IllegalStateException("No session data found for the current user"))

            Result.success(api.createInvite("Bearer ${session.accessToken}").toResult())
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    private fun ArcanaInviteShareResponse.toResult(): ArcanaInviteShareResult {
        return ArcanaInviteShareResult(
            token = token,
            webUrl = webUrl,
        )
    }
}

private interface ArcanaInviteShareApi {
    @POST("api/invite")
    suspend fun createInvite(@Header("Authorization") authorization: String): ArcanaInviteShareResponse
}

@Serializable
private data class ArcanaInviteShareResponse(
    val token: String,
    val webUrl: String? = null,
)
