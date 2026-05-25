/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.atomic.pages.HeaderFooterPage
import io.element.android.libraries.designsystem.background.OnboardingBackground
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.ProgressDialog
import io.element.android.libraries.designsystem.components.dialogs.RetryDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.OutlinedButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.launch

@ContributesNode(AppScope::class)
@AssistedInject
class ArcanaInviteNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val inviteRepository: ArcanaInviteRepository,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun onInviteAccepted(roomId: RoomId)
    }

    data class Inputs(
        val token: String,
        val webUrl: String?,
    ) : NodeInputs

    private val inputs = inputs<Inputs>()
    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val activity = requireNotNull(LocalActivity.current)
        val isDarkTheme = ElementTheme.isLightTheme.not()
        val scope = rememberCoroutineScope()

        var loadState by remember { mutableStateOf<ArcanaInviteLoadState>(ArcanaInviteLoadState.Loading) }
        var invite by remember { mutableStateOf<ArcanaInviteDetails?>(null) }
        var acceptAction by remember { mutableStateOf<AsyncAction<Unit>>(AsyncAction.Uninitialized) }
        var acceptedRoomId by remember { mutableStateOf<RoomId?>(null) }

        fun loadInvite() {
            scope.launch {
                loadState = ArcanaInviteLoadState.Loading
                loadState = inviteRepository.loadInvite(inputs.token).fold(
                    onSuccess = { details ->
                        invite = details
                        ArcanaInviteLoadState.Ready(details)
                    },
                    onFailure = { throwable ->
                        ArcanaInviteLoadState.Error(throwable)
                    }
                )
            }
        }

        fun acceptInvite(details: ArcanaInviteDetails) {
            scope.launch {
                acceptAction = AsyncAction.Loading
                acceptAction = inviteRepository.acceptInvite(details.token).fold(
                    onSuccess = { roomId ->
                        acceptedRoomId = roomId
                        AsyncAction.Success(Unit)
                    },
                    onFailure = { throwable ->
                        AsyncAction.Failure(throwable)
                    }
                )
            }
        }

        LaunchedEffect(inputs.token) {
            loadInvite()
        }

        ArcanaInviteContent(
            modifier = modifier,
            loadState = loadState,
            invite = invite,
            isAccepting = acceptAction is AsyncAction.Loading,
            onPrimaryAction = {
                invite?.let { currentInvite ->
                    if (currentInvite.isUsed && currentInvite.roomId != null) {
                        callback.onInviteAccepted(currentInvite.roomId)
                    } else {
                        acceptInvite(currentInvite)
                    }
                }
            },
            onRetry = ::loadInvite,
            onClose = ::navigateUp,
            onOpenWeb = { url ->
                activity.openUrlInChromeCustomTab(null, isDarkTheme, url)
            },
            webFallbackUrl = inputs.webUrl,
        )

        ProgressDialogIfNeeded(acceptAction = acceptAction)

        when (val action = acceptAction) {
            is AsyncAction.Success -> {
                acceptedRoomId?.let { roomId ->
                    LaunchedEffect(roomId) {
                        acceptedRoomId = null
                        acceptAction = AsyncAction.Uninitialized
                        callback.onInviteAccepted(roomId)
                    }
                }
            }
            is AsyncAction.Failure -> {
                RetryDialog(
                    content = stringResource(R.string.screen_arcana_invite_accept_error_message),
                    onRetry = {
                        acceptAction = AsyncAction.Uninitialized
                        invite?.let(::acceptInvite)
                    },
                    onDismiss = { acceptAction = AsyncAction.Uninitialized },
                )
            }
            else -> Unit
        }
    }

    @Composable
    private fun ProgressDialogIfNeeded(acceptAction: AsyncAction<Unit>) {
        when (acceptAction) {
            is AsyncAction.Loading -> ProgressDialog(
                text = stringResource(R.string.screen_arcana_invite_joining_message),
            )
            else -> Unit
        }
    }
}

private sealed interface ArcanaInviteLoadState {
    data object Loading : ArcanaInviteLoadState
    data class Ready(val details: ArcanaInviteDetails) : ArcanaInviteLoadState
    data class Error(val throwable: Throwable) : ArcanaInviteLoadState
}

@Composable
private fun ArcanaInviteContent(
    loadState: ArcanaInviteLoadState,
    invite: ArcanaInviteDetails?,
    isAccepting: Boolean,
    onPrimaryAction: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onOpenWeb: (String) -> Unit,
    webFallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
    when (loadState) {
            ArcanaInviteLoadState.Loading -> {
            HeaderFooterPage(
                modifier = modifier.fillMaxSize(),
                background = { OnboardingBackground() },
                header = {
                    IconTitleSubtitleMolecule(
                        modifier = Modifier.padding(top = 60.dp, bottom = 28.dp),
                        title = stringResource(R.string.screen_arcana_invite_title),
                        subTitle = stringResource(R.string.screen_arcana_invite_loading_message),
                        iconStyle = BigIcon.Style.Default(CompoundIcons.ChatSolid()),
                    )
                },
                content = {},
                footer = {},
            )
        }

        is ArcanaInviteLoadState.Error -> {
            ArcanaInviteErrorView(
                modifier = modifier,
                onRetry = onRetry,
                onOpenWeb = webFallbackUrl?.let { { onOpenWeb(it) } },
                onClose = onClose,
            )
        }

        is ArcanaInviteLoadState.Ready -> {
            ArcanaInviteReadyView(
                modifier = modifier,
                invite = invite ?: loadState.details,
                isAccepting = isAccepting,
                onPrimaryAction = onPrimaryAction,
                onOpenWeb = onOpenWeb,
                onClose = onClose,
                webFallbackUrl = webFallbackUrl,
            )
        }
    }
}

@Composable
private fun ArcanaInviteReadyView(
    invite: ArcanaInviteDetails,
    isAccepting: Boolean,
    onPrimaryAction: () -> Unit,
    onOpenWeb: (String) -> Unit,
    onClose: () -> Unit,
    webFallbackUrl: String?,
    modifier: Modifier = Modifier,
) {
    val inviterName = invite.inviterDisplayName
        ?: invite.inviterUserId
        ?: stringResource(R.string.screen_arcana_invite_unknown_inviter)
    val roomName = invite.roomName ?: stringResource(R.string.screen_arcana_invite_private_chat)
    val fallbackUrl = invite.webUrl ?: webFallbackUrl
    val primaryActionLabel = if (invite.isUsed && invite.roomId != null) {
        stringResource(R.string.screen_arcana_invite_action_open_chat)
    } else {
        stringResource(CommonStrings.action_accept)
    }
    val primaryActionEnabled = !isAccepting && (!invite.isUsed || invite.roomId != null)

    HeaderFooterPage(
        modifier = modifier.fillMaxSize(),
        background = { OnboardingBackground() },
        header = {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 60.dp, bottom = 28.dp),
                title = stringResource(R.string.screen_arcana_invite_title),
                subTitle = stringResource(R.string.screen_arcana_invite_subtitle),
                iconStyle = BigIcon.Style.Default(CompoundIcons.ChatSolid()),
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                if (invite.isUsed) {
                    InviteStatusRow(
                        label = stringResource(R.string.screen_arcana_invite_status_label),
                        value = stringResource(R.string.screen_arcana_invite_status_used),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                InviteDetailRow(
                    label = stringResource(R.string.screen_arcana_invite_room_label),
                    value = roomName,
                )
                Spacer(modifier = Modifier.height(16.dp))
                InviteDetailRow(
                    label = stringResource(R.string.screen_arcana_invite_inviter_label),
                    value = inviterName,
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (invite.expiresAt != null) {
                    InviteDetailRow(
                        label = stringResource(R.string.screen_arcana_invite_expires_label),
                        value = invite.expiresAt,
                    )
                }
            }
        },
        footer = {
            ButtonColumnMolecule {
                Button(
                    text = primaryActionLabel,
                    enabled = primaryActionEnabled,
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (fallbackUrl != null) {
                    OutlinedButton(
                        text = stringResource(R.string.screen_arcana_invite_action_open_browser_page),
                        onClick = { onOpenWeb(fallbackUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = stringResource(CommonStrings.action_decline),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun ArcanaInviteErrorView(
    onRetry: () -> Unit,
    onOpenWeb: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HeaderFooterPage(
        modifier = modifier.fillMaxSize(),
        background = { OnboardingBackground() },
        header = {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 60.dp, bottom = 28.dp),
                title = stringResource(R.string.screen_arcana_invite_error_title),
                subTitle = stringResource(R.string.screen_arcana_invite_error_message),
                iconStyle = BigIcon.Style.Default(CompoundIcons.Warning()),
            )
        },
        content = {},
        footer = {
            ButtonColumnMolecule {
                Button(
                    text = stringResource(CommonStrings.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onOpenWeb != null) {
                    OutlinedButton(
                        text = stringResource(R.string.screen_arcana_invite_action_open_browser_page),
                        onClick = onOpenWeb,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    text = stringResource(CommonStrings.action_close),
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@Composable
private fun InviteDetailRow(
    label: String,
    value: String,
) {
    Text(
        text = label,
        style = ElementTheme.typography.fontBodySmMedium,
        color = ElementTheme.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = value,
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textPrimary,
    )
}

@Composable
private fun InviteStatusRow(
    label: String,
    value: String,
) {
    Text(
        text = label,
        style = ElementTheme.typography.fontBodySmMedium,
        color = ElementTheme.colors.textSecondary,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = value,
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textPrimary,
    )
}

@PreviewsDayNight
@Composable
internal fun ArcanaInviteReadyPreview() = ElementPreview {
    ArcanaInviteReadyView(
        invite = ArcanaInviteDetails(
            token = "token-123",
            inviterDisplayName = "Alice",
            inviterUserId = "@alice:celesteai.ru",
            roomName = "Arcana",
            roomId = RoomId("!roomid:celesteai.ru"),
            webUrl = "https://arcana.celesteai.ru/invite/token-123",
            expiresAt = "2026-05-25T12:00:00Z",
            isUsed = false,
            isDm = true,
        ),
        isAccepting = false,
        onPrimaryAction = {},
        onOpenWeb = {},
        onClose = {},
        webFallbackUrl = "https://arcana.celesteai.ru/invite/token-123",
    )
}

@PreviewsDayNight
@Composable
internal fun ArcanaInviteUsedPreview() = ElementPreview {
    ArcanaInviteReadyView(
        invite = ArcanaInviteDetails(
            token = "token-123",
            inviterDisplayName = "Alice",
            inviterUserId = "@alice:celesteai.ru",
            roomName = "Arcana",
            roomId = RoomId("!roomid:celesteai.ru"),
            webUrl = "https://arcana.celesteai.ru/invite/token-123",
            expiresAt = "2026-05-25T12:00:00Z",
            isUsed = true,
            isDm = true,
        ),
        isAccepting = false,
        onPrimaryAction = {},
        onOpenWeb = {},
        onClose = {},
        webFallbackUrl = "https://arcana.celesteai.ru/invite/token-123",
    )
}

@PreviewsDayNight
@Composable
internal fun ArcanaInviteErrorPreview() = ElementPreview {
    ArcanaInviteErrorView(
        onRetry = {},
        onOpenWeb = {},
        onClose = {},
    )
}

@PreviewsDayNight
@Composable
internal fun ArcanaInviteLoadingPreview() = ElementPreview {
    ArcanaInviteContent(
        loadState = ArcanaInviteLoadState.Loading,
        invite = null,
        isAccepting = false,
        onPrimaryAction = {},
        onRetry = {},
        onClose = {},
        onOpenWeb = {},
        webFallbackUrl = null,
    )
}
