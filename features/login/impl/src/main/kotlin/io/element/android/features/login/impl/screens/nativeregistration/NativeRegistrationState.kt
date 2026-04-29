/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.nativeregistration

import android.os.Parcelable
import io.element.android.features.login.impl.accountprovider.AccountProvider
import io.element.android.features.login.impl.nativeauth.PendingRegistration
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.parcelize.Parcelize

data class NativeRegistrationState(
    val accountProvider: AccountProvider,
    val formState: NativeRegistrationFormState,
    val registerAction: AsyncData<SessionId>,
    val pendingRegistration: PendingRegistration?,
    val eventSink: (NativeRegistrationEvents) -> Unit,
) {
    val submitEnabled: Boolean
        get() = registerAction !is AsyncData.Loading &&
            formState.hasRegistrationIdentity &&
            formState.password.isNotBlank() &&
            formState.confirmPassword.isNotBlank()

    val isAwaitingEmailVerification: Boolean
        get() = pendingRegistration != null
}

@Parcelize
data class NativeRegistrationFormState(
    val username: String,
    val email: String,
    val registrationToken: String,
    val password: String,
    val confirmPassword: String,
) : Parcelable {
    val hasRegistrationIdentity: Boolean
        get() = email.isNotBlank() || (registrationToken.isNotBlank() && username.isNotBlank())

    companion object {
        val Default = NativeRegistrationFormState(
            username = "",
            email = "",
            registrationToken = "",
            password = "",
            confirmPassword = "",
        )
    }
}
