/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.loginpassword

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.features.login.impl.R
import io.element.android.libraries.matrix.test.A_PASSWORD
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.pressBack
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class LoginPasswordViewTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `clicking on back invoke back callback`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>(expectEvents = false)
        ensureCalledOnce { callback ->
            rule.setLoginPasswordView(
                aLoginPasswordState(
                    eventSink = eventsRecorder
                ),
                onBackClick = callback,
            )
            rule.pressBack()
        }
    }

    @Test
    fun `changing login invokes the expected event`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                eventSink = eventsRecorder,
            ),
        )
        val loginHint = rule.activity.getString(R.string.screen_login_login_label)
        rule.onNodeWithText(loginHint).performTextInput(A_USER_NAME)
        eventsRecorder.assertSingle(
            LoginPasswordEvents.SetLogin(A_USER_NAME)
        )
    }

    @Test
    fun `changing password invokes the expected event`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                eventSink = eventsRecorder,
            ),
        )
        val passwordHint = rule.activity.getString(CommonStrings.common_password)
        rule.onNodeWithText(passwordHint).performTextInput(A_PASSWORD)
        eventsRecorder.assertSingle(
            LoginPasswordEvents.SetPassword(A_PASSWORD)
        )
    }

    @Test
    fun `clicking on sign in sends expected event`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                formState = aLoginFormState(login = A_USER_NAME, password = A_PASSWORD),
                eventSink = eventsRecorder,
            ),
        )
        val signInStr = rule.activity.getString(R.string.action_sign_in)
        rule.onNodeWithText(signInStr).assertIsEnabled()
        rule.onNodeWithText(signInStr).performClick()
        eventsRecorder.assertSingle(
            LoginPasswordEvents.Submit
        )
    }

    @Test
    fun `when login is empty, sign in button is not enabled`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>(expectEvents = false)
        rule.setLoginPasswordView(
            aLoginPasswordState(
                formState = aLoginFormState(password = A_PASSWORD),
                eventSink = eventsRecorder,
            ),
        )
        val signInStr = rule.activity.getString(R.string.action_sign_in)
        rule.onNodeWithText(signInStr).assertIsNotEnabled()
    }

    @Test
    fun `when password is empty, sign in button is not enabled`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>(expectEvents = false)
        rule.setLoginPasswordView(
            aLoginPasswordState(
                formState = aLoginFormState(login = A_USER_NAME),
                eventSink = eventsRecorder,
            ),
        )
        val signInStr = rule.activity.getString(R.string.action_sign_in)
        rule.onNodeWithText(signInStr).assertIsNotEnabled()
    }

    @Test
    fun `changing verification code invokes the expected event`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                step = LoginPasswordStep.VerificationCode,
                pendingEmailLogin = aPendingEmailLogin(),
                formState = aLoginFormState(
                    login = A_USER_NAME,
                    verificationCode = "",
                ),
                eventSink = eventsRecorder,
            ),
        )
        rule.onNodeWithTag(TestTags.loginVerificationCode.value).performTextInput("123456")
        eventsRecorder.assertSingle(
            LoginPasswordEvents.SetVerificationCode("123456")
        )
    }

    @Test
    fun `resend code button is shown in verification state`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>(expectEvents = false)
        rule.setLoginPasswordView(
            aLoginPasswordState(
                step = LoginPasswordStep.VerificationCode,
                pendingEmailLogin = aPendingEmailLogin(),
                formState = aLoginFormState(
                    login = A_USER_NAME,
                    verificationCode = "123456",
                ),
                eventSink = eventsRecorder,
            ),
        )
        rule.onNodeWithText(rule.activity.getString(R.string.screen_login_action_resend_code)).assertIsEnabled()
    }

    @Test
    fun `verification screen hides the password field`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>(expectEvents = false)
        rule.setLoginPasswordView(
            aLoginPasswordState(
                step = LoginPasswordStep.VerificationCode,
                pendingEmailLogin = aPendingEmailLogin(),
                formState = aLoginFormState(
                    login = A_USER_NAME,
                    verificationCode = "123456",
                ),
                eventSink = eventsRecorder,
            ),
        )
        rule.onNodeWithTag(TestTags.loginPassword.value).assertDoesNotExist()
        rule.onNodeWithTag(TestTags.loginVerificationCode.value).assertExists()
    }

    @Config(qualifiers = "h1024dp")
    @Test
    fun `clicking on Confirm sends expected event`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                step = LoginPasswordStep.VerificationCode,
                pendingEmailLogin = aPendingEmailLogin(),
                formState = aLoginFormState(
                    login = A_USER_NAME,
                    verificationCode = "123456",
                ),
                eventSink = eventsRecorder,
            ),
        )
        val confirmStr = rule.activity.getString(CommonStrings.action_confirm)
        rule.onNodeWithText(confirmStr).assertIsEnabled()
        rule.onNodeWithText(confirmStr).performClick()
        eventsRecorder.assertSingle(
            LoginPasswordEvents.Submit
        )
    }

    @Test
    fun `pressing back in verification state cancels verification`() {
        val eventsRecorder = EventsRecorder<LoginPasswordEvents>()
        rule.setLoginPasswordView(
            aLoginPasswordState(
                step = LoginPasswordStep.VerificationCode,
                pendingEmailLogin = aPendingEmailLogin(),
                formState = aLoginFormState(
                    login = A_USER_NAME,
                    verificationCode = "123456",
                ),
                eventSink = eventsRecorder,
            ),
        )
        rule.pressBack()
        eventsRecorder.assertSingle(LoginPasswordEvents.GoBack)
    }

    private fun <R : TestRule> AndroidComposeTestRule<R, ComponentActivity>.setLoginPasswordView(
        state: LoginPasswordState,
        onBackClick: () -> Unit = EnsureNeverCalled(),
    ) {
        setContent {
            LoginPasswordView(
                state = state,
                onBackClick = onBackClick,
            )
        }
    }
}
