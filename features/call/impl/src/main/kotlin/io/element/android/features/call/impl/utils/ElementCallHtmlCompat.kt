/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

/**
 * Embedded Element Call uses `Promise.withResolvers` (Chrome 119+). Huawei WebView is Chromium 114,
 * so the ES module fails to load and the user sees the standalone login / "Join as guest" screen.
 * See: https://github.com/element-hq/element-call/pull/3905
 */
internal object ElementCallHtmlCompat {
    private const val POLYFILL_SCRIPT =
        "<script>" +
            "if(!Promise.withResolvers){" +
            "Promise.withResolvers=function(){" +
            "var resolve,reject;" +
            "var promise=new Promise(function(a,b){resolve=a;reject=b});" +
            "return{promise:promise,resolve:resolve,reject:reject}" +
            "}}" +
            "</script>"

    fun injectPromiseWithResolversPolyfill(html: String): String {
        if (html.contains("Promise.withResolvers")) return html
        val moduleMarker = """<script type="module""""
        val moduleIndex = html.indexOf(moduleMarker)
        return if (moduleIndex >= 0) {
            html.substring(0, moduleIndex) + POLYFILL_SCRIPT + html.substring(moduleIndex)
        } else {
            html.replaceFirst("</head>", "$POLYFILL_SCRIPT</head>")
        }
    }
}
