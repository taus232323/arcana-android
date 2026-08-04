/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.startchat.api.StartDMAction
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.usersearch.api.UserRepository
import io.element.android.libraries.usersearch.api.UserSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Inject
class RoomListSearchPresenter(
    private val dataSourceFactory: RoomListSearchDataSource.Factory,
    private val userRepository: UserRepository,
    private val startDMAction: StartDMAction,
) : Presenter<RoomListSearchState> {
    @Composable
    override fun present(): RoomListSearchState {
        // Do not use rememberSaveable so that search is not active when the user navigates back to the screen
        var isSearchActive by remember {
            mutableStateOf(false)
        }
        val searchQuery = rememberTextFieldState()

        val coroutineScope = rememberCoroutineScope()
        val dataSource = remember { dataSourceFactory.create(coroutineScope) }
        val startDmActionState: MutableState<AsyncAction<RoomId>> = remember { mutableStateOf(AsyncAction.Uninitialized) }

        var userResults: ImmutableList<UserSearchResult> by remember { mutableStateOf(persistentListOf()) }
        var isSearchingUsers by remember { mutableStateOf(false) }

        LaunchedEffect(searchQuery.text) {
            dataSource.setSearchQuery(searchQuery.text.toString())
        }

        val queryText = searchQuery.text.toString()
        LaunchedEffect(isSearchActive, queryText) {
            if (!isSearchActive || queryText.trim().length < MINIMUM_USER_SEARCH_LENGTH) {
                userResults = persistentListOf()
                isSearchingUsers = false
                return@LaunchedEffect
            }
            userRepository.search(queryText).onEach { state ->
                isSearchingUsers = state.isSearching
                userResults = state.results.toImmutableList()
            }.launchIn(this)
        }

        LaunchedEffect(isSearchActive) {
            if (!isSearchActive) {
                startDmActionState.value = AsyncAction.Uninitialized
            }
        }

        fun handleEvent(event: RoomListSearchEvent) {
            when (event) {
                RoomListSearchEvent.ClearQuery -> {
                    searchQuery.clearText()
                }
                RoomListSearchEvent.ToggleSearchVisibility -> {
                    isSearchActive = !isSearchActive
                    searchQuery.clearText()
                }
                is RoomListSearchEvent.UpdateVisibleRange -> coroutineScope.launch {
                    dataSource.updateVisibleRange(visibleRange = event.range)
                }
                is RoomListSearchEvent.StartDM -> coroutineScope.launch {
                    startDMAction.execute(
                        matrixUser = event.matrixUser,
                        createIfDmDoesNotExist = startDmActionState.value is AsyncAction.Confirming,
                        actionState = startDmActionState,
                    )
                }
                RoomListSearchEvent.CancelStartDM -> {
                    startDmActionState.value = AsyncAction.Uninitialized
                }
            }
        }

        val searchResults by dataSource.roomSummaries.collectAsState(initial = persistentListOf())

        return RoomListSearchState(
            isSearchActive = isSearchActive,
            query = searchQuery,
            results = searchResults,
            userResults = userResults,
            isSearchingUsers = isSearchingUsers,
            startDmAction = startDmActionState.value,
            eventSink = ::handleEvent,
        )
    }

    companion object {
        private const val MINIMUM_USER_SEARCH_LENGTH = 2
    }
}
