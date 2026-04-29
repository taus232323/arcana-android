/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.changeaccountprovider

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.changeserver.aChangeServerState
import io.element.android.libraries.core.meta.BuildType
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_2
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChangeAccountProviderPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val presenter = ChangeAccountProviderPresenter(
            changeServerPresenter = { aChangeServerState() },
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { emptyList() }
            ),
            buildMeta = aBuildMeta(buildType = BuildType.RELEASE),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.accountProviders).isEqualTo(
                listOf(
                    AccountProvider(
                        url = AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL,
                        title = "celesteai.ru",
                        subtitle = null,
                        isPublic = true,
                        isMatrixOrg = true,
                    )
                )
            )
            assertThat(initialState.canSearchForAccountProviders).isTrue()
        }
    }

    @Test
    fun `present - fixed list of account providers`() = runTest {
        val presenter = ChangeAccountProviderPresenter(
            changeServerPresenter = { aChangeServerState() },
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = {
                    listOf(AuthenticationConfig.DEFAULT_SERVER_NAME, AN_ACCOUNT_PROVIDER_2)
                }
            ),
            buildMeta = aBuildMeta(buildType = BuildType.RELEASE),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.accountProviders).isEqualTo(
                listOf(
                    AccountProvider(
                        url = AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL,
                        title = "celesteai.ru",
                        subtitle = null,
                        isPublic = true,
                        isMatrixOrg = true,
                    ),
                    AccountProvider(
                        url = "https://element.io",
                        title = "element.io",
                        subtitle = null,
                        isPublic = false,
                        isMatrixOrg = false,
                    )
                )
            )
            assertThat(initialState.canSearchForAccountProviders).isFalse()
        }
    }

    @Test
    fun `present - opened list of account providers`() = runTest {
        val presenter = ChangeAccountProviderPresenter(
            changeServerPresenter = { aChangeServerState() },
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = {
                    listOf(AuthenticationConfig.DEFAULT_SERVER_NAME, EnterpriseService.ANY_ACCOUNT_PROVIDER)
                }
            ),
            buildMeta = aBuildMeta(buildType = BuildType.RELEASE),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.accountProviders).isEqualTo(
                listOf(
                    AccountProvider(
                        url = AuthenticationConfig.DEFAULT_ACCOUNT_PROVIDER_URL,
                        title = "celesteai.ru",
                        subtitle = null,
                        isPublic = true,
                        isMatrixOrg = true,
                    )
                )
            )
            assertThat(initialState.canSearchForAccountProviders).isTrue()
        }
    }

    @Test
    fun `present - debug builds can search for account providers manually`() = runTest {
        val presenter = ChangeAccountProviderPresenter(
            changeServerPresenter = { aChangeServerState() },
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = {
                    listOf(AuthenticationConfig.DEFAULT_SERVER_NAME)
                }
            ),
            buildMeta = aBuildMeta(buildType = BuildType.DEBUG),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.canSearchForAccountProviders).isTrue()
        }
    }
}
