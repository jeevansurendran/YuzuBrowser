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

package jp.hazuki.yuzubrowser.adblock.filter.abp.io

import com.google.re2j.Pattern
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.toInt
import jp.hazuki.yuzubrowser.adblock.filter.toShortInt
import java.io.InputStream

class AbpFilterReader(private val input: InputStream) {

    fun checkHeader(): Boolean {
        val header = ABP_CACHE_HEADER.toByteArray()
        val data = ByteArray(header.size)
        input.read(data)
        return header contentEquals data
    }

    fun readAll(): List<AbpFilter> {
        val intBuf = ByteArray(4)
        val shortBuf = ByteArray(2)
        input.read(intBuf)
        val size = intBuf.toInt()
        val list = ArrayList<AbpFilter>(size)
        var patternBuffer = ByteArray(32)

        loop@ for (loop in 0 until size) {
            val type = input.read()
            if (type < 0) break

            if (input.read(shortBuf) != 2) break
            val contentType = shortBuf.toShortInt()

            val ignoreCase = when (input.read()) {
                0 -> false
                1 -> true
                else -> break@loop
            }

            val thirdParty = when (input.read()) {
                0 -> 0
                1 -> 1
                0xff -> -1
                else -> break@loop
            }

            val patternSize = input.readVariableInt(shortBuf, intBuf)
            if (patternSize == -1) break
            if (patternBuffer.size < patternSize) {
                patternBuffer = ByteArray(patternSize)
            }
            if (input.read(patternBuffer, 0, patternSize) != patternSize) break
            val pattern = String(patternBuffer, 0, patternSize)

            val domainsSize = input.read()
            if (domainsSize == -1) break

            val domains = when (domainsSize) {
                0 -> null
                1 -> {
                    val textSize = input.readVariableInt(shortBuf, intBuf)
                    if (textSize == -1) break@loop
                    if (patternBuffer.size < textSize) {
                        patternBuffer = ByteArray(textSize)
                    }
                    if (input.read(patternBuffer, 0, textSize) != textSize) break@loop
                    val domain = String(patternBuffer, 0, textSize)
                    val include = when (input.read()) {
                        0 -> false
                        1 -> true
                        else -> break@loop
                    }
                    SingleDomainMap(include, domain)
                }
                else -> {
                    val map = ArrayDomainMap(domainsSize)
                    for (i in 0 until domainsSize) {
                        val textSize = input.readVariableInt(shortBuf, intBuf)
                        if (textSize == -1) break@loop
                        if (patternBuffer.size < textSize) {
                            patternBuffer = ByteArray(textSize)
                        }
                        if (input.read(patternBuffer, 0, textSize) != textSize) break@loop
                        val domain = String(patternBuffer, 0, textSize)
                        val include = when (input.read()) {
                            0 -> false
                            1 -> true
                            else -> break@loop
                        }
                        map[domain] = include
                    }
                    map
                }
            }

            val filter = when (type) {
                ABP_TYPE_CONTAINS -> AbpContainsFilter(pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_START -> AbpStartsWithFilter(pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_END -> AbpEndWithFilter(pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_START_END -> AbpStartEndFilter(pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_RE2_REGEX -> AbpRe2Filter(Pattern.compile(pattern), pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_JVM_REGEX -> AbpRegexFilter(pattern.toRegex(), pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_RE2_SSP_REGEX -> AbpRe2SspFilter(Pattern.compile("//$pattern"), pattern, contentType, ignoreCase, domains, thirdParty)
                ABP_TYPE_JVM_SSP_REGEX -> AbpRegexSspFilter("//$pattern".toRegex(), pattern, contentType, ignoreCase, domains, thirdParty)
                else -> break@loop
            }
            list.add(filter)
        }
        return list
    }
}