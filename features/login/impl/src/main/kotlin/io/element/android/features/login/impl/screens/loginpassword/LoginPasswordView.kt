/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

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
import io.element.android.features.login.impl.components.PasswordTextField
import io.element.android.features.login.impl.components.VerificationCodeTextField
import io.element.android.features.login.impl.components.SanitizedTextField
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.error.loginError
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.molecules.ButtonColumnMolecule
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
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
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPasswordView(
    state: LoginPasswordState,
    onBackClick: () -> Unit,
    onForgotPasswordClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val autofillManager = LocalAutofillManager.current

    BackHandler {
        autofillManager?.cancel()
        if (state.isAwaitingEmailVerification) {
            state.eventSink(LoginPasswordEvents.GoBack)
        } else {
            onBackClick()
        }
    }

    val isLoading by remember(state.loginAction) {
        derivedStateOf {
            state.loginAction is AsyncData.Loading
        }
    }
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus(force = true)
        autofillManager?.commit()
        state.eventSink(LoginPasswordEvents.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                    navigationIcon = {
                        BackButton(onClick = {
                            autofillManager?.cancel()
                            if (state.isAwaitingEmailVerification) {
                                state.eventSink(LoginPasswordEvents.GoBack)
                            } else {
                                onBackClick()
                            }
                        })
                },
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding)
                .consumeWindowInsets(padding)
                .verticalScroll(state = scrollState)
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            IconTitleSubtitleMolecule(
                modifier = Modifier.padding(top = 20.dp, start = 16.dp, end = 16.dp),
                iconStyle = BigIcon.Style.Default(CompoundIcons.UserProfileSolid()),
                title = if (state.isAwaitingEmailVerification) {
                    stringResource(R.string.screen_login_email_verification_title)
                } else {
                    stringResource(R.string.screen_login_credentials_title)
                },
                subTitle = if (state.isAwaitingEmailVerification) {
                    stringResource(R.string.screen_login_email_verification_subtitle)
                } else {
                    stringResource(R.string.screen_login_credentials_subtitle)
                }
            )
            Spacer(Modifier.height(32.dp))

            if (state.isAwaitingEmailVerification) {
                LoginVerificationContent(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                )
            } else {
                LoginForm(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                    onForgotPasswordClick = onForgotPasswordClick,
                )
            }

            Spacer(Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ButtonColumnMolecule {
                    Button(
                        text = if (state.isAwaitingEmailVerification) stringResource(CommonStrings.action_confirm) else stringResource(R.string.action_sign_in),
                        showProgress = isLoading,
                        onClick = ::submit,
                        enabled = state.submitEnabled || isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.loginContinue)
                    )
                    if (state.isAwaitingEmailVerification) {
                        TextButton(
                            text = stringResource(R.string.screen_login_action_resend_code),
                            onClick = { state.eventSink(LoginPasswordEvents.ResendVerificationCode) },
                            enabled = !isLoading && state.canResendVerificationCode,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            if (state.loginAction is AsyncData.Failure) {
                LoginErrorDialog(error = state.loginAction.error, onDismiss = {
                    state.eventSink(LoginPasswordEvents.ClearError)
                })
            }
        }
    }
}

@Composable
private fun LoginForm(
    state: LoginPasswordState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onForgotPasswordClick: (String) -> Unit,
) {
    var loginFieldState by textFieldState(stateValue = state.formState.login)
    var passwordFieldState by textFieldState(stateValue = state.formState.password)

    val focusManager = LocalFocusManager.current
    val eventSink = state.eventSink

    Column {
        Text(
            text = stringResource(R.string.screen_login_form_header),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        SanitizedTextField(
            label = stringResource(R.string.screen_login_login_label),
            value = loginFieldState,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager)
                .testTag(TestTags.loginEmailUsername),
            contentType = ContentType.Username,
            onValueChange = {
                loginFieldState = it
                eventSink(LoginPasswordEvents.SetLogin(it))
            },
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            showClearButton = true,
        )
        Spacer(Modifier.height(20.dp))
        PasswordTextField(
            value = passwordFieldState,
            enabled = !isLoading,
            label = stringResource(CommonStrings.common_password),
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager)
                .testTag(TestTags.loginPassword),
            onValueChange = {
                passwordFieldState = it
                eventSink(LoginPasswordEvents.SetPassword(it))
            },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            text = stringResource(CommonStrings.action_forgot_password),
            onClick = {
                onForgotPasswordClick(state.formState.login.trim())
            },
            enabled = !isLoading,
        )
    }
}

@Composable
private fun LoginVerificationContent(
    state: LoginPasswordState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var verificationCodeFieldState by textFieldState(stateValue = state.formState.verificationCode)
    val focusManager = LocalFocusManager.current
    val eventSink = state.eventSink

    Column {
        Text(
            text = stringResource(R.string.screen_login_email_verification_hint),
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
                .onTabOrEnterKeyFocusNext(focusManager)
                .testTag(TestTags.loginVerificationCode),
            onValueChange = {
                verificationCodeFieldState = it
                eventSink(LoginPasswordEvents.SetVerificationCode(it))
            },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
            showClearButton = true,
        )
    }
}

@Composable
private fun LoginErrorDialog(error: Throwable, onDismiss: () -> Unit) {
    ErrorDialog(
        title = stringResource(id = CommonStrings.dialog_title_error),
        content = stringResource(loginError(error)),
        onSubmit = onDismiss
    )
}

@PreviewsDayNight
@Composable
internal fun LoginPasswordViewPreview(@PreviewParameter(LoginPasswordStateProvider::class) state: LoginPasswordState) = ElementPreview {
    LoginPasswordView(
        state = state,
        onBackClick = {},
        onForgotPasswordClick = {},
    )
}
