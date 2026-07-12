/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType

class DefaultArcanaInviteRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `accept invite sends bearer token from latest session`() = runTest {
        val sessionStore = InMemorySessionStore(
            initialList = listOf(aSessionData(accessToken = "access-token-123"))
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"roomId":"!room:celesteai.ru"}""")
                .addHeader("Content-Type", "application/json")
        )

        val repository = createRepository(sessionStore)
        val result = repository.acceptInvite("invite-token")

        assertThat(result.getOrThrow()).isEqualTo(RoomId("!room:celesteai.ru"))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/invite/invite-token/accept")
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer access-token-123")
    }

    @Test
    fun `accept invite fails when no session is available`() = runTest {
        val repository = createRepository(InMemorySessionStore())

        val result = repository.acceptInvite("invite-token")

        assertThat(result.exceptionOrNull()).isInstanceOf(MissingSessionForInviteAcceptException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun createRepository(sessionStore: InMemorySessionStore): DefaultArcanaInviteRepository {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(
                Json {
                    ignoreUnknownKeys = true
                }.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(ArcanaInviteApi::class.java)
        return DefaultArcanaInviteRepository(
            sessionStore = sessionStore,
            api = api,
        )
    }
}
