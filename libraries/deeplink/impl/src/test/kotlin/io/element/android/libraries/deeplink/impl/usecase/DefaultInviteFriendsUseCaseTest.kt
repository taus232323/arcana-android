/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.deeplink.impl.usecase

import android.app.Activity
import android.content.Intent
import android.os.Looper
import androidx.core.content.IntentCompat
import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.ArcanaInviteLinkBuilder
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.deeplink.api.usecase.InviteFriendsUseCase
import io.element.android.libraries.deeplink.impl.ArcanaInviteShareRepository
import io.element.android.libraries.deeplink.impl.ArcanaInviteShareResult
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DefaultInviteFriendsUseCaseTest {
    @Test
    fun `execute shares the arcana invite url`() {
        runTest {
            val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
            val stringProvider = TestStringProvider()
            val matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID, sessionCoroutineScope = backgroundScope)
            val inviteRepository = FakeArcanaInviteShareRepository(
                result = Result.success(ArcanaInviteShareResult(token = "invite-token-123", webUrl = null))
            )
            val useCase = createInviteFriendsUseCase(
                stringProvider = stringProvider,
                matrixClient = matrixClient,
                buildMeta = aBuildMeta(applicationName = "Arcana"),
                inviteRepository = inviteRepository,
            )

            useCase.execute(activity)
            advanceUntilIdle()
            shadowOf(Looper.getMainLooper()).idle()

            val chooserIntent = shadowOf(activity).nextStartedActivity
            assertThat(chooserIntent.action).isEqualTo(Intent.ACTION_CHOOSER)

            val shareIntent = IntentCompat.getParcelableExtra(chooserIntent, Intent.EXTRA_INTENT, Intent::class.java)
            assertThat(shareIntent?.action).isEqualTo(Intent.ACTION_SEND)
            assertThat(shareIntent?.getStringExtra(Intent.EXTRA_TEXT))
                .isEqualTo("formatted-text:Arcana,${ArcanaInviteLinkBuilder.build("invite-token-123")}")
            assertThat(shareIntent?.getStringExtra(Intent.EXTRA_TITLE))
                .isEqualTo("formatted-title:Arcana")
        }
    }

    private fun createInviteFriendsUseCase(
        stringProvider: StringProvider,
        matrixClient: MatrixClient,
        buildMeta: BuildMeta,
        inviteRepository: ArcanaInviteShareRepository,
    ): InviteFriendsUseCase {
        return DefaultInviteFriendsUseCase(
            stringProvider = stringProvider,
            matrixClient = matrixClient,
            buildMeta = buildMeta,
            inviteRepository = inviteRepository,
        )
    }

    private class FakeArcanaInviteShareRepository(
        private val result: Result<ArcanaInviteShareResult>,
    ) : ArcanaInviteShareRepository {
        override suspend fun createInvite(): Result<ArcanaInviteShareResult> = result
    }

    private class TestStringProvider : StringProvider {
        override fun getString(resId: Int): String = "string-$resId"

        override fun getString(resId: Int, vararg formatArgs: Any?): String {
            return when (formatArgs.size) {
                1 -> "formatted-title:${formatArgs[0]}"
                2 -> "formatted-text:${formatArgs[0]},${formatArgs[1]}"
                else -> "formatted-$resId:${formatArgs.joinToString(",")}"
            }
        }

        override fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any?): String {
            return "quantity-$resId-$quantity:${formatArgs.joinToString(",")}"
        }

        override fun getSimpleQuantityString(
            resIdForOne: Int,
            resIdForOthers: Int,
            quantity: Int,
            vararg formatArgs: Any?,
        ): String {
            return "simple-$quantity:${formatArgs.joinToString(",")}"
        }
    }
}
