/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeauth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.element.android.features.login.impl.nativeauth.PendingEmailLogin
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.features.login.impl.screens.loginpassword.LoginPasswordStep
import io.element.android.features.login.impl.screens.loginpassword.aPendingEmailLogin
import io.element.android.features.login.impl.screens.nativeregistration.NativeRegistrationStep
import io.element.android.features.login.impl.screens.nativeregistration.aPendingRegistration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAuthStateRestorationTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `saveable native auth state survives recreation`() {
        val tester = StateRestorationTester(rule)

        tester.setContent {
            NativeAuthSaveableStateHarness()
        }

        rule.onNodeWithTag(TAG_REGISTRATION_STEP).assertTextEquals(NativeRegistrationStep.Email.name)
        rule.onNodeWithTag(TAG_REGISTRATION_PENDING).assertTextEquals("none")
        rule.onNodeWithTag(TAG_LOGIN_STEP).assertTextEquals(LoginPasswordStep.Credentials.name)
        rule.onNodeWithTag(TAG_LOGIN_PENDING).assertTextEquals("none")

        rule.onNodeWithTag(TAG_PRIME).performClick()

        rule.onNodeWithTag(TAG_REGISTRATION_STEP).assertTextEquals(NativeRegistrationStep.Credentials.name)
        rule.onNodeWithTag(TAG_REGISTRATION_PENDING).assertTextEquals("alice@example.com")
        rule.onNodeWithTag(TAG_LOGIN_STEP).assertTextEquals(LoginPasswordStep.VerificationCode.name)
        rule.onNodeWithTag(TAG_LOGIN_PENDING).assertTextEquals("alice@example.com")

        tester.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag(TAG_REGISTRATION_STEP).assertTextEquals(NativeRegistrationStep.Credentials.name)
        rule.onNodeWithTag(TAG_REGISTRATION_PENDING).assertTextEquals("alice@example.com")
        rule.onNodeWithTag(TAG_LOGIN_STEP).assertTextEquals(LoginPasswordStep.VerificationCode.name)
        rule.onNodeWithTag(TAG_LOGIN_PENDING).assertTextEquals("alice@example.com")
    }

    @Composable
    private fun NativeAuthSaveableStateHarness() {
        var registrationStep by rememberSaveable { mutableStateOf(NativeRegistrationStep.Email) }
        var pendingRegistration by rememberSaveable { mutableStateOf<PendingRegistration?>(null) }
        var loginStep by rememberSaveable { mutableStateOf(LoginPasswordStep.Credentials) }
        var pendingEmailLogin by rememberSaveable { mutableStateOf<PendingEmailLogin?>(null) }

        Column {
            Text(text = registrationStep.name, modifier = Modifier.testTag(TAG_REGISTRATION_STEP))
            Text(text = pendingRegistration?.email ?: "none", modifier = Modifier.testTag(TAG_REGISTRATION_PENDING))
            Text(text = loginStep.name, modifier = Modifier.testTag(TAG_LOGIN_STEP))
            Text(text = pendingEmailLogin?.email ?: "none", modifier = Modifier.testTag(TAG_LOGIN_PENDING))
            Button(
                onClick = {
                    registrationStep = NativeRegistrationStep.Credentials
                    pendingRegistration = aPendingRegistration()
                    loginStep = LoginPasswordStep.VerificationCode
                    pendingEmailLogin = aPendingEmailLogin()
                },
                modifier = Modifier.testTag(TAG_PRIME),
            ) {
                Text("Prime")
            }
        }
    }

    private companion object {
        const val TAG_REGISTRATION_STEP = "registration_step"
        const val TAG_REGISTRATION_PENDING = "registration_pending"
        const val TAG_LOGIN_STEP = "login_step"
        const val TAG_LOGIN_PENDING = "login_pending"
        const val TAG_PRIME = "prime_state"
    }
}
