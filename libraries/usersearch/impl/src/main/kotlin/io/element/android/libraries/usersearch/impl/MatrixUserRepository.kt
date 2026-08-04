/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.usersearch.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.usersearch.api.UserListDataSource
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import io.element.android.libraries.usersearch.api.UserSearchResultState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@ContributesBinding(SessionScope::class)
class MatrixUserRepository(
    private val client: MatrixClient,
    private val dataSource: UserListDataSource
) : UserRepository {
    override fun search(query: String): Flow<UserSearchResultState> = flow {
        val trimmed = query.trim()
        val shouldFetchSearchResults = trimmed.length >= MINIMUM_SEARCH_LENGTH
        // Resolve alice / @alice / @alice:server once the query is long enough (or already a full MXID).
        val resolvedUserId = when {
            MatrixPatterns.isUserId(trimmed) -> UserId(trimmed)
            shouldFetchSearchResults -> resolveLocalpartUserId(trimmed)
            else -> null
        }
        val shouldQueryProfile = resolvedUserId != null && !client.isMe(resolvedUserId)
        // Always offer a direct hit for @user / user / @user:server so search works
        // without waiting on (or depending entirely on) the user directory API.
        val fakeSearchResult = if (shouldQueryProfile) {
            UserSearchResult(MatrixUser(resolvedUserId))
        } else {
            null
        }
        if (shouldQueryProfile || shouldFetchSearchResults) {
            emit(UserSearchResultState(isSearching = shouldFetchSearchResults, results = listOfNotNull(fakeSearchResult)))
        }
        if (shouldFetchSearchResults) {
            val results = fetchSearchResults(trimmed, resolvedUserId, shouldQueryProfile)
            emit(results)
        }
    }

    private suspend fun fetchSearchResults(
        query: String,
        resolvedUserId: UserId?,
        shouldQueryProfile: Boolean,
    ): UserSearchResultState {
        // Debounce
        delay(DEBOUNCE_TIME_MILLIS)
        // Directory may match on localpart; also try without a leading '@'.
        val directoryQuery = query.trim().removePrefix("@")
        val results = dataSource
            .search(directoryQuery, MAXIMUM_SEARCH_RESULTS)
            .filter { !client.isMe(it.userId) }
            .map { UserSearchResult(it) }
            .toMutableList()

        // If the query resolves to another user's MXID and the result doesn't contain that user ID, query the profile explicitly
        if (shouldQueryProfile && resolvedUserId != null && results.none { it.matrixUser.userId == resolvedUserId }) {
            results.add(
                0,
                dataSource.getProfile(resolvedUserId)
                    ?.let { UserSearchResult(it) }
                    ?: UserSearchResult(MatrixUser(resolvedUserId), isUnresolved = true)
            )
        }

        return UserSearchResultState(results = results, isSearching = false)
    }

    /**
     * Resolve `alice` or `@alice` to a full [UserId] on this homeserver.
     */
    private fun resolveLocalpartUserId(query: String): UserId? {
        val localpart = query.removePrefix("@")
        if (localpart.isEmpty() || localpart.contains(':') || localpart.contains(' ')) {
            return null
        }
        val fullId = "@$localpart:${client.userIdServerName()}"
        return fullId.takeIf { MatrixPatterns.isUserId(it) }?.let(::UserId)
    }

    companion object {
        private const val DEBOUNCE_TIME_MILLIS = 250L
        private const val MINIMUM_SEARCH_LENGTH = 2
        private const val MAXIMUM_SEARCH_RESULTS = 10L
    }
}
