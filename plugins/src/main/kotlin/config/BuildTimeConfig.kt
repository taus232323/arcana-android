/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

object BuildTimeConfig {
    const val APPLICATION_ID = "ru.celesteai.arcana"
    const val APPLICATION_NAME = "Arcana"
    // Firebase Android app for package ru.celesteai.arcana (release).
    // Debug/nightly use applicationIdSuffix — add matching apps in Firebase if needed.
    const val GOOGLE_APP_ID_RELEASE = "1:488818307130:android:0751e7a7097d6bb4ea0503"
    const val GOOGLE_APP_ID_DEBUG = "1:488818307130:android:0751e7a7097d6bb4ea0503"
    const val GOOGLE_APP_ID_NIGHTLY = "1:488818307130:android:0751e7a7097d6bb4ea0503"

    val METADATA_HOST_REVERSED: String? = "ru.celesteai.arcana"
    val URL_WEBSITE: String? = "https://arcana.celesteai.ru"
    val URL_LOGO: String? = null
    val URL_COPYRIGHT: String? = "https://arcana.celesteai.ru/terms#copyright"
    val URL_ACCEPTABLE_USE: String? = "https://arcana.celesteai.ru/terms"
    val URL_PRIVACY: String? = "https://arcana.celesteai.ru/privacy"
    val URL_POLICY: String? = "https://arcana.celesteai.ru/privacy"
    val CLIENT_PERMALINK_BASE_URL: String? = "https://arcana.celesteai.ru/#/"
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = null
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = null
    val SERVICES_MAPTILER_DARK_MAPID: String? = null
    val SERVICES_POSTHOG_HOST: String? = null
    val SERVICES_POSTHOG_APIKEY: String? = null
    val SERVICES_SENTRY_DSN: String? = null
    val SERVICES_SENTRY_DSN_RUST: String? = null
    val BUG_REPORT_URL: String? = null
    val BUG_REPORT_APP_NAME: String? = null

    const val PUSH_CONFIG_INCLUDE_FIREBASE = true
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true
}
