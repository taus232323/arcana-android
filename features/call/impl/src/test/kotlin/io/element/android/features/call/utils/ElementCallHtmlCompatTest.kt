/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.utils

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.utils.ElementCallHtmlCompat
import org.junit.Test

class ElementCallHtmlCompatTest {
    @Test
    fun `polyfill is inserted before the module script`() {
        val html = """<!doctype html><html><head><script>window.global = window;</script>""" +
            """<script type="module" src="./assets/index.js"></script></head><body></body></html>"""

        val patched = ElementCallHtmlCompat.injectPromiseWithResolversPolyfill(html)

        assertThat(patched).contains("Promise.withResolvers")
        assertThat(patched.indexOf("Promise.withResolvers"))
            .isLessThan(patched.indexOf("""<script type="module""""))
    }

    @Test
    fun `polyfill is not duplicated when already present`() {
        val html = """<head><script>if(!Promise.withResolvers){}</script>""" +
            """<script type="module" src="./assets/index.js"></script></head>"""

        val patched = ElementCallHtmlCompat.injectPromiseWithResolversPolyfill(html)

        assertThat(patched).isEqualTo(html)
    }
}
