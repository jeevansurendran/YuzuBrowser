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
package jp.hazuki.yuzubrowser.legacy.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import com.google.firebase.FirebaseApp
import com.google.firebase.dynamiclinks.ShortDynamicLink
import com.google.firebase.dynamiclinks.ktx.androidParameters
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.dynamiclinks.ktx.shortLinkAsync
import com.google.firebase.dynamiclinks.ktx.socialMetaTagParameters
import com.google.firebase.ktx.Firebase
import jp.hazuki.yuzubrowser.adblock.filter.fastmatch.FastMatcherFactory
import jp.hazuki.yuzubrowser.legacy.Constants
import jp.hazuki.yuzubrowser.legacy.Constants.share.GDL_DYNAMIC_URL
import jp.hazuki.yuzubrowser.legacy.Constants.share.SHARE_URL
import jp.hazuki.yuzubrowser.legacy.Constants.share.UTM_DESCRIPTION
import jp.hazuki.yuzubrowser.legacy.Constants.share.UTM_IMAGE_URL
import jp.hazuki.yuzubrowser.legacy.Constants.share.UTM_TITLE
import jp.hazuki.yuzubrowser.legacy.R
import jp.hazuki.yuzubrowser.ui.utils.PackageUtils
import java.util.*
import java.util.regex.Pattern

object WebUtils {
    private val URL_EXTRACTION = Pattern.compile("((?:http|https|file|market)://|(?:inline|data|about|content|javascript|mailto|view-source|yuzu|blob):)(\\S*)", Pattern.CASE_INSENSITIVE)
    private val URL_SUB_DOMAIN = Pattern.compile("://.*\\.", Pattern.LITERAL)
    fun extractionUrl(text: String?): String? {
        if (text == null) return null
        val matcher = URL_EXTRACTION.matcher(text)
        return if (matcher.find()) {
            matcher.group()
        } else {
            text
        }
    }

    fun isOverrideScheme(uri: Uri): Boolean {
        return when (uri.scheme!!.toLowerCase(Locale.ROOT)) {
            "http", "https", "file", "inline", "data", "about", "content", "javascript", "view-source", "blob" -> false
            else -> true
        }
    }

    fun makeSearchUrlFromQuery(query: String, search_url: String?, search_place_holder: String?): String {
        return URLUtil.composeSearchUrl(query.trim { it <= ' ' }, search_url, search_place_holder)
    }

    fun createShareWebIntent(url: String?, title: String?): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, url)
        if (title != null) intent.putExtra(Intent.EXTRA_SUBJECT, title)
        return intent
    }

    fun shareWeb(context: Context, url: String?, title: String?) {
        if (url == null) return
        val deepLinkUrl = SHARE_URL.buildUpon().appendQueryParameter("aURL", url).build()
        FirebaseApp.initializeApp(context)
        val dynamicLinkTask = Firebase.dynamicLinks.shortLinkAsync(ShortDynamicLink.Suffix.SHORT) {
            link = deepLinkUrl
            domainUriPrefix = GDL_DYNAMIC_URL
            androidParameters {}
            socialMetaTagParameters {
                this.title = UTM_TITLE
                description = UTM_DESCRIPTION
                imageUrl = Uri.parse(UTM_IMAGE_URL)
            }
        }
        dynamicLinkTask.addOnSuccessListener {
            val intent = createShareWebIntent(it.shortLink.toString(), title)
            try {
                context.startActivity(Intent.createChooser(intent, context.getText(R.string.share)))
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }

        dynamicLinkTask.addOnFailureListener {
            val intent = createShareWebIntent(url, title)
            try {
                context.startActivity(Intent.createChooser(intent, context.getText(R.string.share)))
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }
    }

    fun createOpenInOtherAppIntent(url: String?): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    fun createOpenInOtherAppIntent(intent: Intent, url: String?): Intent {
        intent.data = Uri.parse(url)
        return intent
    }

    fun openInOtherApp(context: Context, url: String?) {
        if (url == null) return
        try {
            context.startActivity(PackageUtils.createChooser(context, url, context.getText(R.string.open_other_app)))
        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    fun makeUrlPatternWithThrow(factory: FastMatcherFactory, pattern: String?): Pattern? {
        var pattern_url = pattern
        if (pattern_url == null || pattern_url.isEmpty()) return null
        return if (pattern_url[0] == '[' && pattern_url[pattern_url.length - 1] == ']') {
            Pattern.compile(pattern_url.substring(1, pattern_url.length - 1))
        } else {
            pattern_url = factory.fastCompile(pattern_url)
            if (pattern_url.startsWith(".*\\.")) {
                pattern_url = "((?![./]).)*" + pattern_url.substring(3)
            } else if (pattern_url.contains("://.*\\.")) {
                pattern_url = URL_SUB_DOMAIN.matcher(pattern_url).replaceFirst("://((?![\\./]).)*\\.")
            }
            if (pattern_url!!.startsWith("http.*://") && pattern_url.length >= 10) {
                pattern_url = "https?://" + pattern_url.substring(9)
            }
            if (maybeContainsUrlScheme(pattern_url)) Pattern.compile("^$pattern_url") else Pattern.compile("^\\w+://$pattern_url")
        }
    }

    private val sSchemeContainsPattern = Pattern.compile("^\\w+:", Pattern.CASE_INSENSITIVE)
    fun maybeContainsUrlScheme(url: String?): Boolean {
        return url != null && sSchemeContainsPattern.matcher(url).find()
    }
}
