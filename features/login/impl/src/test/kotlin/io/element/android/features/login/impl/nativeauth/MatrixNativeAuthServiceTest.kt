/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.nativeauth

import com.google.common.truth.Truth.assertThat
import dev.zacsweers.metro.Provider
import io.element.android.libraries.androidutils.json.DefaultJsonProvider
import io.element.android.libraries.androidutils.json.JsonProvider
import io.element.android.libraries.network.RetrofitFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test

class MatrixNativeAuthServiceTest {
    @Test
    fun `start email login sends user identifier for username`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sid":"sid","email":"alice@example.com"}""")
        )
        server.start()

        try {
            val service = createService()

            val result = service.startEmailLogin(
                homeserverUrl = server.url("/").toString(),
                login = "alice",
                password = "password",
            )

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/_matrix/client/v3/login")
            val body = request.body.readUtf8()
            assertThat(body).contains("\"login\":\"alice\"")
            assertThat(body).contains("\"identifier\":")
            assertThat(body).contains("\"type\":\"m.id.user\"")
            assertThat(body).contains("\"user\":\"alice\"")
            assertThat(body).contains("\"password\":\"password\"")
            assertThat(body).contains("\"send_attempt\":1")
            assertThat(result.isSuccess).isTrue()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `start email login sends third-party identifier for email`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sid":"sid","email":"alice@example.com"}""")
        )
        server.start()

        try {
            val service = createService()

            val result = service.startEmailLogin(
                homeserverUrl = server.url("/").toString(),
                login = "alice@example.com",
                password = "password",
            )

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/_matrix/client/v3/login")
            val body = request.body.readUtf8()
            assertThat(body).contains("\"login\":\"alice@example.com\"")
            assertThat(body).contains("\"identifier\":")
            assertThat(body).contains("\"type\":\"m.id.thirdparty\"")
            assertThat(body).contains("\"medium\":\"email\"")
            assertThat(body).contains("\"address\":\"alice@example.com\"")
            assertThat(body).contains("\"password\":\"password\"")
            assertThat(body).contains("\"send_attempt\":1")
            assertThat(result.isSuccess).isTrue()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `start password reset requests email token`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sid":"sid"}""")
        )
        server.start()

        try {
            val service = createService()

            val result = service.startPasswordReset(
                homeserverUrl = server.url("/").toString(),
                email = "alice@example.com",
            )

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/_matrix/client/v3/account/password/email/requestToken")
            val body = request.body.readUtf8()
            assertThat(body).contains("\"client_secret\":\"")
            assertThat(body).contains("\"email\":\"alice@example.com\"")
            assertThat(body).contains("\"send_attempt\":1")
            assertThat(result.isSuccess).isTrue()
            val pending = result.getOrThrow() as PasswordResetResult.AwaitingEmailVerification
            assertThat(pending.pendingPasswordReset.sid).isEqualTo("sid")
            assertThat(pending.pendingPasswordReset.email).isEqualTo("alice@example.com")
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `resend password reset email requests a new email token`() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"sid":"new-sid"}""")
        )
        server.start()

        try {
            val service = createService()
            val pendingPasswordReset = PendingPasswordReset(
                homeserverUrl = server.url("/").toString(),
                email = "alice@example.com",
                clientSecret = "secret",
                sendAttempt = 1,
                sid = "sid",
                session = null,
                completedStages = emptyList(),
                flows = emptyList(),
            )

            val result = service.resendPasswordResetEmail(pendingPasswordReset)

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/_matrix/client/v3/account/password/email/requestToken")
            val body = request.body.readUtf8()
            assertThat(body).contains("\"client_secret\":\"secret\"")
            assertThat(body).contains("\"email\":\"alice@example.com\"")
            assertThat(body).contains("\"send_attempt\":2")
            assertThat(result.isSuccess).isTrue()
            val updated = result.getOrThrow()
            assertThat(updated.sid).isEqualTo("new-sid")
            assertThat(updated.sendAttempt).isEqualTo(2)
        } finally {
            server.shutdown()
        }
    }

    private fun createService(): DefaultMatrixNativeAuthService {
        val jsonProvider = DefaultJsonProvider()
        return DefaultMatrixNativeAuthService(
            retrofitFactory = RetrofitFactory(
                okHttpClient = object : Provider<OkHttpClient> {
                    override fun invoke(): OkHttpClient = OkHttpClient()
                },
                json = object : Provider<JsonProvider> {
                    override fun invoke(): JsonProvider = jsonProvider
                },
            ),
            jsonProvider = jsonProvider,
        )
    }
}
