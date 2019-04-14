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

package jp.hazuki.yuzubrowser.adblock.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.android.DaggerIntentService
import jp.hazuki.yuzubrowser.adblock.BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA
import jp.hazuki.yuzubrowser.adblock.filter.abp.*
import jp.hazuki.yuzubrowser.adblock.filter.abp.io.AbpFilterWriter
import jp.hazuki.yuzubrowser.adblock.repository.AdBlockPref
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDatabase
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import javax.inject.Inject

class AbpUpdateService : DaggerIntentService("AbpUpdateService") {

    @Inject
    internal lateinit var okHttpClient: OkHttpClient

    @Inject
    internal lateinit var abpDatabase: AbpDatabase

    override fun onHandleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_UPDATE_ABP -> {
                val param1 = intent.getParcelableExtra<AbpEntity>(EXTRA_ABP_ENTRY)
                val result = intent.getParcelableExtra<ResultReceiver?>(EXTRA_RESULT)
                updateAbpEntity(param1, result)
            }
            ACTION_UPDATE_ALL -> {
                val forceUpdate = intent.getBooleanExtra(EXTRA_FORCE_UPDATE, false)
                val result = intent.getParcelableExtra<ResultReceiver?>(EXTRA_RESULT)
                updateAll(forceUpdate, result)
            }
        }
    }

    private fun updateAll(forceUpdate: Boolean, resultReceiver: ResultReceiver?) = runBlocking {
        var result = false
        var nextUpdateTime = Long.MAX_VALUE
        val now = System.currentTimeMillis()
        abpDatabase.abpDao().getAll().forEach {
            if (forceUpdate || it.isNeedUpdate()) {
                val localResult = updateInternal(it)
                if (localResult && it.expires > 0) {
                    val nextTime = it.expires * AN_HOUR + now
                    if (nextTime < nextUpdateTime) nextUpdateTime = nextTime
                }
                result = result or localResult
            }
        }

        AdBlockPref.get(applicationContext).abpNextUpdateTime = if (nextUpdateTime != Long.MAX_VALUE) {
            nextUpdateTime
        } else {
            System.currentTimeMillis() + A_DAY
        }
        if (result) {
            LocalBroadcastManager
                    .getInstance(applicationContext)
                    .sendBroadcast(Intent(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA))
        }
        resultReceiver?.send(RESULT_CODE_UPDATE_ALL, null)
    }

    private fun updateAbpEntity(entity: AbpEntity, result: ResultReceiver?) = runBlocking {
        if (updateInternal(entity)) {
            result?.send(RESULT_CODE_UPDATED, Bundle().apply { putParcelable(EXTRA_ABP_ENTRY, entity) })
            LocalBroadcastManager
                    .getInstance(applicationContext)
                    .sendBroadcast(Intent(BROADCAST_ACTION_UPDATE_AD_BLOCK_DATA))
        } else {
            result?.send(RESULT_CODE_FAILED, Bundle().apply { putParcelable(EXTRA_ABP_ENTRY, entity) })
        }
    }

    private suspend fun updateInternal(entity: AbpEntity): Boolean {
        return when {
            entity.url == "yuzu://adblock/filter" -> updateAssets(entity)
            entity.url.startsWith("http") -> updateHttp(entity)
            entity.url.startsWith("file") -> updateFile(entity)
            else -> false
        }
    }

    private suspend fun updateHttp(entity: AbpEntity): Boolean {
        val request = Request.Builder()
                .url(entity.url)
                .get()
                .build()
        val call = okHttpClient.newCall(request)
        try {
            val response = call.execute()

            response.body()?.run {
                source().inputStream().bufferedReader().use { reader ->
                    return decode(reader, entity)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun updateFile(entity: AbpEntity): Boolean {
        val file = File(Uri.parse(entity.url).path)
        if (file.lastModified() < entity.lastLocalUpdate) return false

        try {
            file.inputStream().bufferedReader().use { reader ->
                return decode(reader, entity)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

    private suspend fun updateAssets(entity: AbpEntity): Boolean {
        if (entity.version == "1") return false

        assets.open("adblock/yuzu_filter.txt").bufferedReader().use {
            return decode(it, entity)
        }
    }

    private suspend fun decode(reader: BufferedReader, entity: AbpEntity): Boolean {
        val decoder = AbpFilterDecoder()
        if (!decoder.checkHeader(reader)) return false

        val set = decoder.decode(reader, entity.url)

        val info = set.filterInfo
        entity.title = info.title
        entity.expires = info.expires ?: -1
        entity.homePage = info.homePage
        entity.version = info.version
        entity.lastUpdate = info.lastUpdate
        entity.lastLocalUpdate = System.currentTimeMillis()
        val dir = getAbpDir()
        val writer = AbpFilterWriter()
        writer.write(dir.getAbpBlackListFile(entity).outputStream().buffered(), set.blackList)
        writer.write(dir.getAbpWhiteListFile(entity).outputStream().buffered(), set.whiteList)
        writer.write(dir.getAbpWhitePageListFile(entity).outputStream().buffered(), set.whitePageList)

        abpDatabase.abpDao().update(entity)
        return true
    }

    companion object {
        private const val ACTION_UPDATE_ALL = "jp.hazuki.yuzubrowser.adblock.service.action.UpdateAll"
        private const val ACTION_UPDATE_ABP = "jp.hazuki.yuzubrowser.adblock.service.action.UpdateAbp"

        private const val EXTRA_ABP_ENTRY = "jp.hazuki.yuzubrowser.adblock.service.extra.entry"
        private const val EXTRA_RESULT = "jp.hazuki.yuzubrowser.adblock.service.extra.result"
        private const val EXTRA_FORCE_UPDATE = "jp.hazuki.yuzubrowser.adblock.service.extra.update.force"

        private const val RESULT_CODE_UPDATED = 1
        private const val RESULT_CODE_FAILED = 2
        private const val RESULT_CODE_UPDATE_ALL = 3

        private const val AN_HOUR = 60 * 60 * 1000
        private const val A_DAY = 24 * AN_HOUR

        fun updateAll(context: Context, forceUpdate: Boolean = false, result: UpdateResult? = null) {
            val intent = Intent(context, AbpUpdateService::class.java).apply {
                action = ACTION_UPDATE_ALL
                putExtra(EXTRA_FORCE_UPDATE, forceUpdate)
                putExtra(EXTRA_RESULT, result)
            }
            context.startService(intent)
        }

        fun update(context: Context, abpEntity: AbpEntity, result: UpdateResult? = null) {
            val intent = Intent(context, AbpUpdateService::class.java).apply {
                action = ACTION_UPDATE_ABP
                putExtra(EXTRA_ABP_ENTRY, abpEntity)
                putExtra(EXTRA_RESULT, result)
            }
            context.startService(intent)
        }
    }

    abstract class UpdateResult(handler: Handler?) : ResultReceiver(handler) {

        final override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
            when (resultCode) {
                RESULT_CODE_UPDATED -> onUpdated(resultData!!.getParcelable(EXTRA_ABP_ENTRY)!!)
                RESULT_CODE_FAILED -> onFailedUpdate(resultData!!.getParcelable(EXTRA_ABP_ENTRY)!!)
                RESULT_CODE_UPDATE_ALL -> onUpdateAll()
            }
        }

        abstract fun onFailedUpdate(entity: AbpEntity)

        abstract fun onUpdated(entity: AbpEntity)

        abstract fun onUpdateAll()
    }
}
