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

package jp.hazuki.yuzubrowser.adblock.core

import android.net.Uri
import jp.hazuki.yuzubrowser.adblock.filter.Filter
import jp.hazuki.yuzubrowser.adblock.filter.fastmatch.FastMatcherList

class FilterMatcherList(
        private val fastMatcherList: FastMatcherList,
        private val customData: List<Filter>
) {

    fun match(uri: Uri, pageUrl: Uri, contentType: Int, isThirdParty: Boolean): Boolean =
            customData.any { it.match(uri, pageUrl, contentType, isThirdParty) } || fastMatcherList.any { it.match(uri) }
}