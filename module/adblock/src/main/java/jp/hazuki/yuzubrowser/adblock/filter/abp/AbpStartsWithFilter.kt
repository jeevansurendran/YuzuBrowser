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

package jp.hazuki.yuzubrowser.adblock.filter.abp

import android.net.Uri

class AbpStartsWithFilter(
        filter: String,
        contentType: Int,
        ignoreCase: Boolean,
        domains: DomainMap?,
        thirdParty: Int
) : AbpFilter(filter, contentType, ignoreCase, domains, thirdParty) {
    override val type: Int
        get() = ABP_TYPE_START

    override fun check(url: Uri): Boolean {
        return url.schemeSpecificPart.regionMatches(2, pattern, 0, pattern.length, ignoreCase)
    }
}