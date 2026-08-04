/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.components.RoomSummaryRow
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.features.startchat.api.ConfirmingStartDmWithMatrixUser
import io.element.android.libraries.designsystem.components.async.AsyncActionView
import io.element.android.libraries.designsystem.components.async.AsyncActionViewDefaults
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FilledTextField
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.OnVisibleRangeChangeEffect
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.ui.components.CreateDmConfirmationBottomSheet
import io.element.android.libraries.matrix.ui.components.MatrixUserRow
import io.element.android.libraries.matrix.ui.components.UnresolvedUserRow
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.usersearch.api.UserSearchResult

@Composable
internal fun RoomListSearchView(
    state: RoomListSearchState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = state.isSearchActive) {
        state.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
    }

    AnimatedVisibility(
        visible = state.isSearchActive,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(modifier = modifier) {
            RoomListSearchContent(
                state = state,
                hideInvitesAvatars = hideInvitesAvatars,
                onRoomClick = onRoomClick,
                eventSink = eventSink,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomListSearchContent(
    state: RoomListSearchState,
    hideInvitesAvatars: Boolean,
    eventSink: (RoomListEvent) -> Unit,
    onRoomClick: (RoomId) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.tertiary
    val strokeWidth = 1.dp
    fun onBackButtonClick() {
        state.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
    }

    fun onRoomClick(room: RoomListRoomSummary) {
        onRoomClick(room.roomId)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokeWidth.value
                    )
                },
                navigationIcon = { BackButton(onClick = ::onBackButtonClick) },
                title = {
                    // The stateSaver will keep the selection state when returning to this UI
                    val focusRequester = remember { FocusRequester() }
                    FilledTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        state = state.query,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = if (state.query.text.isNotEmpty()) {
                            @Composable {
                                IconButton(onClick = { state.eventSink(RoomListSearchEvent.ClearQuery) }) {
                                    Icon(
                                        imageVector = CompoundIcons.Close(),
                                        contentDescription = stringResource(CommonStrings.action_cancel)
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )

                    LaunchedEffect(Unit) {
                        if (!focusRequester.restoreFocusedChild()) {
                            focusRequester.requestFocus()
                        }
                        focusRequester.saveFocusedChild()
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            val lazyListState = rememberLazyListState()
            OnVisibleRangeChangeEffect(lazyListState) { visibleRange ->
                state.eventSink(RoomListSearchEvent.UpdateVisibleRange(visibleRange))
            }
            val showSectionHeaders = state.results.isNotEmpty() && state.userResults.isNotEmpty()
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
            ) {
                if (state.results.isNotEmpty()) {
                    if (showSectionHeaders) {
                        item(key = "chats_header") {
                            ListSectionHeader(
                                title = stringResource(id = R.string.screen_home_tab_chats),
                                hasDivider = false,
                            )
                        }
                    }
                    items(
                        items = state.results,
                        key = { it.roomId.value },
                        contentType = { room -> room.contentType() },
                    ) { room ->
                        RoomSummaryRow(
                            room = room,
                            hideInviteAvatars = hideInvitesAvatars,
                            // TODO
                            isInviteSeen = false,
                            onClick = ::onRoomClick,
                            eventSink = eventSink,
                        )
                    }
                }
                if (state.userResults.isNotEmpty()) {
                    item(key = "people_header") {
                        ListSectionHeader(
                            title = stringResource(id = CommonStrings.common_people),
                            hasDivider = showSectionHeaders,
                        )
                    }
                    items(
                        items = state.userResults,
                        key = { it.matrixUser.userId.value },
                    ) { userResult ->
                        SearchUserResultRow(
                            searchResult = userResult,
                            onClick = {
                                state.eventSink(RoomListSearchEvent.StartDM(userResult.matrixUser))
                            },
                        )
                    }
                }
            }
        }
    }

    AsyncActionView(
        async = state.startDmAction,
        progressDialog = {
            AsyncActionViewDefaults.ProgressDialog(
                progressText = stringResource(CommonStrings.common_starting_chat),
            )
        },
        onSuccess = { roomId ->
            state.eventSink(RoomListSearchEvent.ToggleSearchVisibility)
            onRoomClick(roomId)
        },
        errorMessage = { stringResource(CommonStrings.common_error) },
        onRetry = { state.eventSink(RoomListSearchEvent.CancelStartDM) },
        onErrorDismiss = { state.eventSink(RoomListSearchEvent.CancelStartDM) },
        confirmationDialog = { data ->
            if (data is ConfirmingStartDmWithMatrixUser) {
                CreateDmConfirmationBottomSheet(
                    matrixUser = data.matrixUser,
                    isUserIdentityUnknown = data.isUserIdentityUnknown,
                    onSendInvite = {
                        state.eventSink(RoomListSearchEvent.StartDM(data.matrixUser))
                    },
                    onDismiss = {
                        state.eventSink(RoomListSearchEvent.CancelStartDM)
                    },
                )
            }
        },
    )
}

@Composable
private fun SearchUserResultRow(
    searchResult: UserSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchResult.isUnresolved) {
        UnresolvedUserRow(
            modifier = modifier.clickable(onClick = onClick),
            avatarData = searchResult.matrixUser.getAvatarData(AvatarSize.UserListItem),
            id = searchResult.matrixUser.userId.displayNameWithAt,
        )
    } else {
        MatrixUserRow(
            modifier = modifier.clickable(onClick = onClick),
            matrixUser = searchResult.matrixUser,
            avatarSize = AvatarSize.UserListItem,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListSearchContentPreview(@PreviewParameter(RoomListSearchStateProvider::class) state: RoomListSearchState) = ElementPreview {
    RoomListSearchContent(
        state = state,
        hideInvitesAvatars = false,
        onRoomClick = {},
        eventSink = {},
    )
}
