package com.chat.sticker.service

import android.text.TextUtils
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKApiConfig
import com.chat.base.net.ApiService
import com.chat.base.net.HttpResponseCode
import com.chat.base.net.IRequestResultErrorInfoListener
import com.chat.base.net.IRequestResultListener
import com.chat.base.net.entity.CommonResponse
import com.chat.base.net.ud.UploadService
import com.chat.base.utils.WKFileUtils
import com.chat.sticker.entity.StickerItem
import com.chat.sticker.entity.StickerFavoriteItem
import com.chat.sticker.entity.StickerPackage
import com.chat.sticker.entity.StickerPanelData
import com.chat.sticker.utils.StickerGifConverter
import com.chat.sticker.utils.StickerTrace
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * 表情商店数据模型
 * Created by Luckclouds .
 */
class StickerModel private constructor() : WKBaseModel() {
    companion object {
        val instance: StickerModel by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { StickerModel() }
    }

    private val favoriteItems = mutableListOf<StickerItem>()

    fun isFavorite(targetType: String, targetId: String = "", emojiCode: String = "", mediaUrls: List<String> = emptyList()): Boolean {
        val normalizedUrls = mediaUrls.filter { isUsefulMediaPath(it) }
        synchronized(favoriteItems) {
            return favoriteItems.any { item ->
                when {
                    targetType == "builtin_emoji" && emojiCode.isNotEmpty() -> item.emojiCode == emojiCode
                    targetId.isNotEmpty() -> {
                        if (item.targetType.isNotEmpty() && item.targetType != targetType) return@any false
                        val itemTargetId = item.targetId.ifEmpty {
                            when (targetType) {
                                "custom_item" -> item.customId.ifEmpty { item.itemId }
                                "dynamic_emoji" -> item.emojiId.ifEmpty { item.itemId }
                                else -> item.itemId
                            }
                        }
                        itemTargetId == targetId
                    }
                    normalizedUrls.isNotEmpty() -> {
                        val itemUrls = listOf(item.gifUrl, item.originUrl, item.thumbUrl).filter { isUsefulMediaPath(it) }
                        normalizedUrls.any { targetUrl -> itemUrls.any { itemUrl -> sameMediaPath(targetUrl, itemUrl) } }
                    }
                    else -> false
                }
            }
        }
    }

    fun getPanel(callback: (Int, String, StickerPanelData) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/panel")
        requestAndErrorBack(createService(StickerService::class.java).panel(), object : IRequestResultErrorInfoListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/panel body=$result")
                callback(HttpResponseCode.success.toInt(), "", StickerPanelData.fromJson(result))
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/panel code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), StickerPanelData())
            }
        })
    }

    fun getFavorites(callback: (Int, String, MutableList<StickerItem>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/favorites")
        requestAndErrorBack(createService(StickerService::class.java).favorites(), object : IRequestResultErrorInfoListener<JSONArray> {
            override fun onSuccess(result: JSONArray) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/favorites body=$result")
                val cacheItems = StickerFavoriteItem.toStickerItems(result, includeEmptyMedia = true)
                val items = StickerFavoriteItem.toStickerItems(result)
                synchronized(favoriteItems) {
                    favoriteItems.clear()
                    favoriteItems.addAll(cacheItems)
                }
                StickerTrace.d("STICKER_TRACE_FAVORITE_PARSE count=${items.size} first=${StickerTrace.itemSummary(items.firstOrNull())}")
                callback(HttpResponseCode.success.toInt(), "", items)
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/favorites code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), mutableListOf())
            }
        })
    }

    fun getEmoji(keyword: String = "", groupNo: String = "", callback: (Int, String, MutableList<StickerItem>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/emoji keyword=$keyword groupNo=$groupNo")
        requestAndErrorBack(createService(StickerService::class.java).emojiList(keyword, groupNo), object : IRequestResultErrorInfoListener<JSONArray> {
            override fun onSuccess(result: JSONArray) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/emoji body=$result")
                callback(HttpResponseCode.success.toInt(), "", StickerItem.fromArray(result))
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/emoji code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), mutableListOf())
            }
        })
    }

    fun getCustom(callback: (Int, String, MutableList<StickerItem>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/custom")
        requestAndErrorBack(createService(StickerService::class.java).customList(), object : IRequestResultErrorInfoListener<JSONArray> {
            override fun onSuccess(result: JSONArray) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/custom body=$result")
                callback(HttpResponseCode.success.toInt(), "", StickerItem.fromArray(result))
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/custom code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), mutableListOf())
            }
        })
    }

    fun getMyPackages(callback: (Int, String, MutableList<StickerPackage>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/my-packages")
        requestAndErrorBack(createService(StickerService::class.java).myPackages(), object : IRequestResultErrorInfoListener<JSONArray> {
            override fun onSuccess(result: JSONArray) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/my-packages body=$result")
                val list = mutableListOf<StickerPackage>()
                for (i in 0 until result.size) {
                    list += StickerPackage.fromJson(result.getJSONObject(i)).copy(
                        isAdded = true
                    )
                }
                callback(HttpResponseCode.success.toInt(), "", list.sortedBy { it.sortNum }.toMutableList())
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/my-packages code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), mutableListOf())
            }
        })
    }

    fun getStorePackages(keyword: String, pageIndex: Int, pageSize: Int, callback: (Int, String, Int, MutableList<StickerPackage>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/store/packages keyword=$keyword pageIndex=$pageIndex pageSize=$pageSize")
        requestAndErrorBack(createService(StickerService::class.java).storePackages(keyword, pageIndex, pageSize), object : IRequestResultErrorInfoListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/store/packages body=$result")
                val list = mutableListOf<StickerPackage>()
                val array = result.getJSONArray("list")
                if (array != null) {
                    for (i in 0 until array.size) {
                        val item = array.getJSONObject(i)
                        list += StickerPackage.fromJson(item).copy(isAdded = item.getBooleanValue("is_added"))
                    }
                }
                callback(HttpResponseCode.success.toInt(), "", result.getIntValue("count"), list)
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/store/packages code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), 0, mutableListOf())
            }
        })
    }

    fun getPackageDetail(packageId: String, callback: (Int, String, StickerPackage, MutableList<StickerItem>) -> Unit) {
        StickerTrace.d("STICKER_TRACE_API_REQUEST GET /v1/sticker/package/detail packageId=$packageId")
        requestAndErrorBack(createService(StickerService::class.java).packageDetail(packageId), object : IRequestResultErrorInfoListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                StickerTrace.d("STICKER_TRACE_API_RESPONSE GET /v1/sticker/package/detail packageId=$packageId body=$result")
                callback(
                    HttpResponseCode.success.toInt(),
                    "",
                    StickerPackage.fromJson(result.getJSONObject("package")),
                    StickerItem.fromArray(result.getJSONArray("items"))
                )
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_FAIL GET /v1/sticker/package/detail packageId=$packageId code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), StickerPackage(), mutableListOf())
            }
        })
    }

    fun addFavorite(targetType: String, targetId: String = "", emojiCode: String = "", callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["target_type"] = targetType
        if (targetId.isNotEmpty()) body["target_id"] = targetId
        if (emojiCode.isNotEmpty()) body["emoji_code"] = emojiCode
        StickerTrace.d("STICKER_TRACE_API_REQUEST POST /v1/sticker/favorites body=$body")
        requestAndErrorBack(createService(StickerService::class.java).addFavorite(body), commonCallback(callback) {
            upsertFavoriteCache(targetType, targetId, emojiCode)
        })
    }

    fun removeFavorite(targetType: String, targetId: String = "", emojiCode: String = "", callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["target_type"] = targetType
        if (targetId.isNotEmpty()) body["target_id"] = targetId
        if (emojiCode.isNotEmpty()) body["emoji_code"] = emojiCode
        StickerTrace.d("STICKER_TRACE_API_REQUEST DELETE /v1/sticker/favorites body=$body")
        requestAndErrorBack(createService(StickerService::class.java).removeFavorite(body), commonCallback(callback) {
            removeFavoriteCache(targetType, targetId, emojiCode)
        })
    }

    fun addMyPackage(packageId: String, callback: (Int, String) -> Unit) {
        requestAndErrorBack(createService(StickerService::class.java).addMyPackage(packageId), commonCallback(callback))
    }

    fun removeMyPackage(packageId: String, callback: (Int, String) -> Unit) {
        requestAndErrorBack(createService(StickerService::class.java).removeMyPackage(packageId), commonCallback(callback))
    }

    fun deleteCustom(ids: List<String>, callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["custom_ids"] = ids
        requestAndErrorBack(createService(StickerService::class.java).deleteCustom(body), commonCallback(callback))
    }

    fun reorderCustom(ids: List<String>, callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["ids"] = ids
        requestAndErrorBack(createService(StickerService::class.java).reorderCustom(body), commonCallback(callback))
    }

    fun reorderMyPackages(ids: List<String>, callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["ids"] = ids
        StickerTrace.d("STICKER_TRACE_API_REQUEST PUT /v1/sticker/my/packages/reorder body=$body")
        requestAndErrorBack(createService(StickerService::class.java).reorderMyPackages(body), commonCallback(callback))
    }

    fun createCustom(localPath: String, callback: (Int, String, StickerItem?) -> Unit) {
        if (TextUtils.isEmpty(localPath) || !File(localPath).exists()) {
            StickerTrace.e("STICKER_TRACE_UPLOAD_PREPARE fail reason=file_not_found path=$localPath")
            callback(HttpResponseCode.error.toInt(), "file not found", null)
            return
        }
        val uploadFile = try {
            StickerGifConverter.prepareUploadFile(localPath)
        } catch (e: Exception) {
            StickerTrace.e("STICKER_TRACE_UPLOAD_PREPARE fail path=$localPath", e)
            callback(HttpResponseCode.error.toInt(), "convert gif failed", null)
            return
        }
        StickerTrace.d("STICKER_TRACE_UPLOAD_PREPARE success source=$localPath uploadPath=${uploadFile.path} converted=${uploadFile.convertedToGif}")
        request(createService(ApiService::class.java).getUploadFileUrl(WKApiConfig.baseUrl + "file/upload?type=sticker"), object : IRequestResultListener<com.chat.base.net.entity.UploadFileUrl> {
            override fun onSuccess(result: com.chat.base.net.entity.UploadFileUrl) {
                StickerTrace.d("STICKER_TRACE_UPLOAD_URL success url=${result.url}")
                uploadStickerFile(result.url, uploadFile.path) { uploadCode, uploadMsg, uploadPath ->
                    if (uploadCode != HttpResponseCode.success.toInt() || uploadPath.isEmpty()) {
                        StickerTrace.e("STICKER_TRACE_UPLOAD_FILE fail code=$uploadCode msg=$uploadMsg local=${uploadFile.path}")
                        callback(uploadCode, uploadMsg, null)
                        return@uploadStickerFile
                    }
                    val body = JSONObject()
                    body["name"] = uploadFile.fileName
                    body["upload_path"] = uploadPath
                    StickerTrace.d("STICKER_TRACE_CREATE_CUSTOM request body=$body")
                    requestAndErrorBack(createService(StickerService::class.java).createCustom(body), object : IRequestResultErrorInfoListener<JSONObject> {
                        override fun onSuccess(result: JSONObject) {
                            StickerTrace.d("STICKER_TRACE_CREATE_CUSTOM success body=$result")
                            callback(HttpResponseCode.success.toInt(), "", StickerItem.fromJson(result))
                        }

                        override fun onFail(code: Int, msg: String?, errJson: String?) {
                            StickerTrace.e("STICKER_TRACE_CREATE_CUSTOM fail code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                            callback(code, msg.orEmpty(), null)
                        }
                    })
                }
            }

            override fun onFail(code: Int, msg: String?) {
                StickerTrace.e("STICKER_TRACE_UPLOAD_URL fail code=$code msg=${msg.orEmpty()}")
                callback(code, msg.orEmpty(), null)
            }
        })
    }

    private fun uploadStickerFile(uploadUrl: String, localPath: String, callback: (Int, String, String) -> Unit) {
        val file = File(localPath)
        val mimeType = when {
            WKFileUtils.getInstance().isGif(localPath) -> "image/gif"
            else -> "image/*"
        }
        StickerTrace.d("STICKER_TRACE_UPLOAD_FILE request uploadUrl=$uploadUrl localPath=$localPath mimeType=$mimeType fileSize=${file.length()}")
        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
        requestAndErrorBack(createService(UploadService::class.java).upload(uploadUrl, part), object : IRequestResultErrorInfoListener<com.chat.base.net.entity.UploadResultEntity> {
            override fun onSuccess(result: com.chat.base.net.entity.UploadResultEntity) {
                StickerTrace.d("STICKER_TRACE_UPLOAD_FILE success path=${result.path.orEmpty()}")
                callback(HttpResponseCode.success.toInt(), "", result.path.orEmpty())
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_UPLOAD_FILE fail code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty(), "")
            }
        })
    }

    private fun upsertFavoriteCache(targetType: String, targetId: String, emojiCode: String) {
        synchronized(favoriteItems) {
            val exists = isFavorite(targetType, targetId, emojiCode)
            if (exists) return
            favoriteItems += StickerItem(
                itemId = targetId,
                targetType = targetType,
                targetId = targetId,
                emojiCode = emojiCode
            )
        }
    }

    private fun removeFavoriteCache(targetType: String, targetId: String, emojiCode: String) {
        synchronized(favoriteItems) {
            favoriteItems.removeAll { item ->
                when {
                    targetType == "builtin_emoji" && emojiCode.isNotEmpty() -> item.emojiCode == emojiCode
                    targetId.isNotEmpty() -> {
                        if (item.targetType.isNotEmpty() && item.targetType != targetType) return@removeAll false
                        val itemTargetId = item.targetId.ifEmpty {
                            when (targetType) {
                                "custom_item" -> item.customId.ifEmpty { item.itemId }
                                "dynamic_emoji" -> item.emojiId.ifEmpty { item.itemId }
                                else -> item.itemId
                            }
                        }
                        itemTargetId == targetId
                    }
                    else -> false
                }
            }
        }
    }

    private fun isUsefulMediaPath(value: String): Boolean {
        val cleanValue = value.trim()
        return cleanValue.length > 5 && (cleanValue.startsWith("http", true) || cleanValue.startsWith("/") || cleanValue.contains("/"))
    }

    private fun sameMediaPath(left: String, right: String): Boolean {
        val leftRaw = left.substringBefore("?").trim()
        val rightRaw = right.substringBefore("?").trim()
        val leftShow = WKApiConfig.getShowUrl(leftRaw).substringBefore("?")
        val rightShow = WKApiConfig.getShowUrl(rightRaw).substringBefore("?")
        return leftRaw == rightRaw ||
            leftShow == rightShow ||
            leftShow.endsWith(rightRaw) ||
            rightShow.endsWith(leftRaw)
    }

    private fun commonCallback(callback: (Int, String) -> Unit, onSuccessMutation: (() -> Unit)? = null): IRequestResultErrorInfoListener<CommonResponse> {
        return object : IRequestResultErrorInfoListener<CommonResponse> {
            override fun onSuccess(result: CommonResponse) {
                StickerTrace.d("STICKER_TRACE_API_COMMON_SUCCESS status=${result.status} msg=${result.msg.orEmpty()}")
                val code = if (result.status == 0) HttpResponseCode.success.toInt() else result.status
                if (code == HttpResponseCode.success.toInt()) {
                    onSuccessMutation?.invoke()
                }
                callback(code, result.msg.orEmpty())
            }

            override fun onFail(code: Int, msg: String?, errJson: String?) {
                StickerTrace.e("STICKER_TRACE_API_COMMON_FAIL code=$code msg=${msg.orEmpty()} err=${errJson.orEmpty()}")
                callback(code, msg.orEmpty())
            }
        }
    }
}
