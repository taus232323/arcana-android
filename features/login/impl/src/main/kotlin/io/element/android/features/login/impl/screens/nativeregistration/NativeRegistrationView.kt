/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.features.login.impl.nativeauth.NativeAuthException
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
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeRegistrationView(
    state: NativeRegistrationState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autofillManager = LocalAutofillManager.current
    BackHandler {
        autofillManager?.cancel()
        onBackClick()
    }

    val isLoading by remember(state.registerAction) {
        derivedStateOf { state.registerAction is AsyncData.Loading }
    }
    val focusManager = LocalFocusManager.current

    fun submit() {
        focusManager.clearFocus(force = true)
        autofillManager?.commit()
        state.eventSink(NativeRegistrationEvents.Submit)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_native_registration_title),
                navigationIcon = {
                    BackButton(onClick = {
                        autofillManager?.cancel()
                        onBackClick()
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
                title = stringResource(R.string.screen_account_provider_signup_title, state.accountProvider.title),
                subTitle = if (state.isAwaitingEmailVerification) {
                    stringResource(R.string.screen_native_registration_email_verification_subtitle, state.formState.email)
                } else {
                    stringResource(R.string.screen_native_registration_subtitle, state.accountProvider.title)
                },
            )
            Spacer(Modifier.height(32.dp))
            if (state.isAwaitingEmailVerification) {
                Text(
                    text = stringResource(R.string.screen_native_registration_email_verification_title, state.formState.email),
                    style = ElementTheme.typography.fontBodyLgRegular,
                    color = ElementTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.screen_native_registration_email_verification_hint),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            } else {
                NativeRegistrationForm(
                    state = state,
                    isLoading = isLoading,
                    onSubmit = ::submit,
                )
            }
            Spacer(Modifier.height(24.dp))
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                ButtonColumnMolecule {
                    if (state.isAwaitingEmailVerification) {
                        Button(
                            text = stringResource(R.string.screen_native_registration_action_continue_after_email),
                            showProgress = isLoading,
                            onClick = { state.eventSink(NativeRegistrationEvents.ConfirmEmailVerified) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            text = stringResource(R.string.screen_native_registration_action_resend_email),
                            onClick = { state.eventSink(NativeRegistrationEvents.ResendEmail) },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Button(
                            text = stringResource(CommonStrings.action_continue),
                            showProgress = isLoading,
                            onClick = ::submit,
                            enabled = state.submitEnabled || isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }

    if (state.registerAction is AsyncData.Failure) {
        ErrorDialog(
            title = stringResource(id = CommonStrings.dialog_title_error),
            content = stringResource(nativeRegistrationError(state.registerAction.error)),
            onSubmit = { state.eventSink(NativeRegistrationEvents.ClearError) },
        )
    }
}

@Composable
private fun NativeRegistrationForm(
    state: NativeRegistrationState,
    isLoading: Boolean,
    onSubmit: () -> Unit,
) {
    var usernameFieldState by textFieldState(stateValue = state.formState.username)
    var emailFieldState by textFieldState(stateValue = state.formState.email)
    var tokenFieldState by textFieldState(stateValue = state.formState.registrationToken)
    var passwordFieldState by textFieldState(stateValue = state.formState.password)
    var confirmPasswordFieldState by textFieldState(stateValue = state.formState.confirmPassword)
    val focusManager = LocalFocusManager.current

    Column {
        TextField(
            label = stringResource(R.string.screen_native_registration_username_label),
            value = usernameFieldState,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager)
                .semantics { contentType = ContentType.Username },
            placeholder = stringResource(CommonStrings.common_username),
            onValueChange = {
                val sanitized = it.sanitize()
                usernameFieldState = sanitized
                state.eventSink(NativeRegistrationEvents.SetUsername(sanitized))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        TextField(
            label = stringResource(R.string.screen_native_registration_email_label),
            value = emailFieldState,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .onTabOrEnterKeyFocusNext(focusManager)
                .semantics { contentType = ContentType.EmailAddress },
            placeholder = stringResource(R.string.screen_native_registration_email_label),
            onValueChange = {
                val sanitized = it.sanitize()
                emailFieldState = sanitized
                state.eventSink(NativeRegistrationEvents.SetEmail(sanitized))
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            singleLine = true,
        )
        Spacer(Modifier.height(16.dp))
        PasswordField(
            value = tokenFieldState,
            label = stringResource(R.string.screen_native_registration_token_label),
            enabled = !isLoading,
            onValueChange = {
                val sanitized = it.sanitize()
                tokenFieldState = sanitized
                state.eventSink(NativeRegistrationEvents.SetRegistrationToken(sanitized))
            },
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(16.dp))
        PasswordField(
            value = passwordFieldState,
            label = stringResource(CommonStrings.common_password),
            enabled = !isLoading,
            onValueChange = {
                val sanitized = it.sanitize()
                passwordFieldState = sanitized
                state.eventSink(NativeRegistrationEvents.SetPassword(sanitized))
            },
            imeAction = ImeAction.Next,
            onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
        )
        Spacer(Modifier.height(16.dp))
        PasswordField(
            value = confirmPasswordFieldState,
            label = stringResource(R.string.screen_native_registration_password_confirm_label),
            enabled = !isLoading,
            onValueChange = {
                val sanitized = it.sanitize()
                confirmPasswordFieldState = sanitized
                state.eventSink(NativeRegistrationEvents.SetConfirmPassword(sanitized))
            },
            imeAction = ImeAction.Done,
            onImeAction = onSubmit,
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    label: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
        label = label,
        onValueChange = onValueChange,
        placeholder = label,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (passwordVisible) CompoundIcons.VisibilityOn() else CompoundIcons.VisibilityOff()
            val description =
                if (passwordVisible) stringResource(CommonStrings.a11y_hide_password) else stringResource(CommonStrings.a11y_show_password)
            Box(Modifier.clickable { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = image,
                    contentDescription = description,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onNext = { onImeAction() }),
        singleLine = true,
    )
}

private fun nativeRegistrationError(error: Throwable): Int {
    return when (error) {
        NativeAuthException.EmailRequired -> R.string.screen_native_registration_error_email_required
        NativeAuthException.RegistrationTokenRequired -> R.string.screen_native_registration_error_token_required
        NativeRegistrationValidationException.PasswordMismatch -> R.string.screen_native_registration_error_password_mismatch
        NativeAuthException.InvalidUsername -> R.string.screen_native_registration_error_invalid_username
        NativeAuthException.UsernameInUse -> R.string.screen_native_registration_error_username_in_use
        NativeAuthException.InvalidEmail -> R.string.screen_native_registration_error_invalid_email
        NativeAuthException.EmailAlreadyInUse -> R.string.screen_native_registration_error_email_in_use
        NativeAuthException.InvalidRegistrationToken -> R.string.screen_native_registration_error_invalid_token
        is NativeAuthException.RateLimited -> R.string.screen_native_registration_error_rate_limited
        is NativeAuthException.UnsupportedAuthenticationFlow -> R.string.screen_native_registration_error_unsupported_flow
        else -> R.string.screen_native_registration_error_unknown
    }
}

private fun String.sanitize(): String = replace("\n", "")

@PreviewsDayNight
@Composable
internal fun NativeRegistrationViewPreview(
    @PreviewParameter(NativeRegistrationStateProvider::class) state: NativeRegistrationState,
) = ElementPreview {
    NativeRegistrationView(
        state = state,
        onBackClick = {},
    )
}
