/*
 * Copyright (C) 2017-2019 Hazuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.hazuki.yuzubrowser.legacy

import android.net.Uri
import jp.hazuki.yuzubrowser.ui.INTENT_ACTION_PREFIX
import jp.hazuki.yuzubrowser.ui.INTENT_EXTRA_PREFIX

class Constants {
    object activity {
        const val MAIN_BROWSER = "jp.hazuki.yuzubrowser.browser.BrowserActivity"
    }

    object intent {
        const val ACTION_OPEN_DEFAULT = "$INTENT_ACTION_PREFIX.default"
        const val EXTRA_MODE_FULLSCREEN = "$INTENT_EXTRA_PREFIX.fullscreen"
        const val EXTRA_MODE_ORIENTATION = "$INTENT_EXTRA_PREFIX.orientation"
        const val EXTRA_URL = "$INTENT_EXTRA_PREFIX.url"
        const val EXTRA_USER_AGENT = "$INTENT_EXTRA_PREFIX.user_agent"
        const val EXTRA_OPEN_FROM_YUZU = "$INTENT_EXTRA_PREFIX.open.from.yuzu"

        const val ACTION_FINISH = "$INTENT_ACTION_PREFIX.finish"
        const val ACTION_NEW_TAB = "$INTENT_ACTION_PREFIX.newTab"
        const val EXTRA_ROOT_URI = "$INTENT_EXTRA_PREFIX.root"

        /** res block */
        const val ACTION_BLOCK_IMAGE = "$INTENT_ACTION_PREFIX.action_block_image"
    }

    object share {
        val SHARE_URL: Uri = Uri.parse("https://yuzu.share/")
        const val GDL_DYNAMIC_URL = "https://hazuki.page.link/"
        const val UTM_TITLE = "Download Yuzu App"
        const val UTM_DESCRIPTION = "Try YuzuBrowser today!"
        const val UTM_IMAGE_URL = "https://dl3.cbsistatic.com/catalog/2020/03/25/cd7de15c-73e1-46ff-bae9-e53b464b1278/imgingest-5193827876681925843.png"
    }
}
