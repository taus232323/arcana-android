/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.passwordreset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.components.PasswordTextField
import io.element.android.features.login.impl.components.VerificationCodeTextField
import io.element.android.features.login.impl.components.SanitizedTextField
import io.element.android.features.login.impl.nativeauth.NativeAuthException
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.form.textFieldState
import io.element.android.libraries.designsystem.modifiers.onTabOrEnterKeyFocusNext
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetView(
    state: PasswordResetState,
    onBackClick: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autofillManager = LocalAutofillManager.current
    BackHandler {
        autofillManager?.cancel()
        if (state.step == PasswordResetStep.Email) {
            onBackClick()
        } else {
            state.eventSink(PasswordResetEvents.GoBack)
        }
    }

    val isLoading by remember(state.resetAction) {
        derivedStateOf { state.resetAction is AsyncData.Loading }
    }
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus(force = true)
        autofillManager?.commit()
        state.eventSink(PasswordResetEvents.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_password_reset_title),
                navigationIcon = {
                    BackButton(onClick = {
                        autofillManager?.cancel()
                        if (state.step == PasswordResetStep.Email) {
                            onBackClick()
                        } else {
                            state.eventSink(PasswordResetEvents.GoBack)
                        }
                    })
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(state = rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
                title = when (state.step) {
                    PasswordResetStep.Email -> stringResource(R.string.screen_password_reset_email_step_title)
                    PasswordResetStep.Code -> stringResource(R.string.screen_password_reset_code_step_title)
                    PasswordResetStep.Credentials -> stringResource(R.string.screen_password_reset_credentials_step_title)
                },
                subTitle = when (state.step) {
                    PasswordResetStep.Email -> stringResource(R.string.screen_password_reset_email_step_subtitle, state.accountProvider.title)
                    PasswordResetStep.Code -> stringResource(R.string.screen_password_reset_code_step_subtitle, state.formState.email)
                    PasswordResetStep.Credentials -> stringResource(R.string.screen_password_reset_credentials_step_subtitle, state.accountProvider.title)
                },
            )
            Spacer(Modifier.height(32.dp))

            when (state.step) {
                PasswordResetStep.Email -> EmailStepContent(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                )
                PasswordResetStep.Code -> CodeStepContent(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                )
                PasswordResetStep.Credentials -> CredentialsStepContent(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                )
            }

            Spacer(Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ButtonColumnMolecule {
                    when (state.step) {
                        PasswordResetStep.Email -> {
                            Button(
                                text = stringResource(CommonStrings.action_continue),
                                showProgress = isLoading,
                                onClick = ::submit,
                                enabled = state.submitEnabled || isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PasswordResetStep.Code -> {
                            Button(
                                text = stringResource(CommonStrings.action_confirm),
                                showProgress = isLoading,
                                onClick = ::submit,
                                enabled = state.submitEnabled || isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                text = stringResource(R.string.screen_password_reset_action_resend_email),
                                onClick = { state.eventSink(PasswordResetEvents.ResendEmail) },
                                enabled = !isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        PasswordResetStep.Credentials -> {
                            Button(
                                text = stringResource(R.string.screen_password_reset_action_update_password),
                                showProgress = isLoading,
                                onClick = ::submit,
                                enabled = state.submitEnabled || isLoading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    if (state.resetAction is AsyncData.Failure) {
        ErrorDialog(
            title = stringResource(CommonStrings.dialog_title_error),
            content = stringResource(passwordResetError(state.resetAction.error)),
            onSubmit = { state.eventSink(PasswordResetEvents.ClearError) },
        )
    }
    if (state.resetAction is AsyncData.Success) {
        ConfirmationDialog(
            title = stringResource(R.string.screen_password_reset_success_title),
            content = stringResource(R.string.screen_password_reset_success_message),
            submitText = stringResource(CommonStrings.action_ok),
            onSubmitClick = {
                state.eventSink(PasswordResetEvents.ClearSuccess)
                onDone()
            },
            onDismiss = {
                state.eventSink(PasswordResetEvents.ClearSuccess)
                onDone()
            },
        )
    }
}

@Composable
private fun EmailStepContent(
    state: PasswordResetState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var emailFieldState by textFieldState(stateValue = state.formState.email)
    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text = stringResource(R.string.screen_password_reset_email_step_body),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        SanitizedTextField(
            label = stringResource(R.string.screen_native_registration_email_label),
            value = emailFieldState,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager),
            contentType = ContentType.EmailAddress,
            onValueChange = {
                emailFieldState = it
                state.eventSink(PasswordResetEvents.SetEmail(it))
            },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
}

@Composable
private fun CodeStepContent(
    state: PasswordResetState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var verificationCodeFieldState by textFieldState(stateValue = state.formState.verificationCode)
    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text = stringResource(R.string.screen_password_reset_code_step_body, state.formState.email),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Spacer(Modifier.height(16.dp))
        VerificationCodeTextField(
            value = verificationCodeFieldState,
            label = stringResource(R.string.screen_login_verification_code_label),
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager),
            onValueChange = {
                verificationCodeFieldState = it
                state.eventSink(PasswordResetEvents.SetVerificationCode(it))
            },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.screen_password_reset_code_step_hint),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun CredentialsStepContent(
    state: PasswordResetState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var passwordFieldState by textFieldState(stateValue = state.formState.newPassword)
    var confirmPasswordFieldState by textFieldState(stateValue = state.formState.confirmPassword)
    val focusManager = LocalFocusManager.current

    Column {
        Text(
            text = stringResource(R.string.screen_password_reset_credentials_step_body),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(16.dp))
        PasswordTextField(
            value = passwordFieldState,
            label = stringResource(R.string.screen_password_reset_new_password_label),
            enabled = !isLoading,
            onValueChange = {
                passwordFieldState = it
                state.eventSink(PasswordResetEvents.SetNewPassword(it))
            },
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(16.dp))
        PasswordTextField(
            value = confirmPasswordFieldState,
            label = stringResource(R.string.screen_password_reset_confirm_password_label),
            enabled = !isLoading,
            onValueChange = {
                confirmPasswordFieldState = it
                state.eventSink(PasswordResetEvents.SetConfirmPassword(it))
            },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
}

private fun passwordResetError(error: Throwable): Int {
    return when (error) {
        NativeAuthException.EmailRequired -> R.string.screen_password_reset_error_email_required
        PasswordResetValidationException.PasswordMismatch -> R.string.screen_password_reset_error_password_mismatch
        NativeAuthException.InvalidEmail -> R.string.screen_password_reset_error_invalid_email
        NativeAuthException.InvalidVerificationCode -> R.string.screen_password_reset_error_invalid_code
        is NativeAuthException.RateLimited -> R.string.screen_password_reset_error_rate_limited
        is NativeAuthException.UnsupportedAuthenticationFlow -> R.string.screen_password_reset_error_unsupported_flow
        else -> R.string.screen_password_reset_error_unknown
    }
}

@PreviewsDayNight
@Composable
internal fun PasswordResetViewPreview(
    @PreviewParameter(PasswordResetStateProvider::class) state: PasswordResetState,
) = ElementPreview {
    PasswordResetView(
        state = state,
        onBackClick = {},
        onDone = {},
    )
}
