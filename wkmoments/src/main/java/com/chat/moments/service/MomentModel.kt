package com.chat.moments.service

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKApiConfig
import com.chat.base.net.ApiService
import com.chat.base.net.HttpResponseCode
import com.chat.base.net.IRequestResultListener
import com.chat.base.net.entity.UploadFileUrl
import com.chat.base.net.entity.UploadResultEntity
import com.chat.base.net.ud.UploadService
import com.chat.base.utils.WKLogUtils
import com.chat.base.utils.WKTimeUtils
import com.chat.moments.entity.MomentAudienceSelection
import com.chat.moments.entity.MomentComment
import com.chat.moments.entity.MomentComposeMedia
import com.chat.moments.entity.MomentFeedPage
import com.chat.moments.entity.MomentLike
import com.chat.moments.entity.MomentMedia
import com.chat.moments.entity.MomentNotice
import com.chat.moments.entity.MomentPost
import com.chat.moments.entity.MomentProfile
import com.chat.moments.entity.MomentUser
import com.chat.moments.entity.MomentUserChoice
import com.chat.moments.entity.MomentUserStateSetting
import com.chat.moments.entity.MomentVisibilityType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MomentModel private constructor() : WKBaseModel() {

    companion object {
        val instance: MomentModel by lazy { MomentModel() }
    }

    private val service by lazy { createService(MomentService::class.java) }
    private val successCode = HttpResponseCode.success.toInt()
    private val errorCode = HttpResponseCode.error.toInt()

    fun loadTimeline(uid: String?, pageIndex: Int, pageSize: Int, callback: (Int, String, MomentFeedPage) -> Unit) {
        val observable = if (uid.isNullOrEmpty()) {
            service.getFeed(pageIndex, pageSize)
        } else {
            service.getUserFeed(uid, pageIndex, pageSize)
        }
        request(observable, object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "", parseFeedPage(result))
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, MomentFeedPage())
            }
        })
    }

    fun getProfile(uid: String, callback: (Int, String, MomentProfile) -> Unit) {
        request(service.getProfile(uid), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "", parseProfile(result))
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, MomentProfile(uid = uid))
            }
        })
    }

    fun getStrangerVisibility(callback: (Int, String, String) -> Unit) {
        request(service.getStrangerVisibility(), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "", unwrapObject(result).getString("scope") ?: "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, "")
            }
        })
    }

    fun updateStrangerVisibility(scope: String, callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["scope"] = scope
        request(service.updateStrangerVisibility(body), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    fun getMomentUserState(uid: String, callback: (Int, String, MomentUserStateSetting) -> Unit) {
        request(service.getMomentUserState(uid), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                val data = unwrapObject(result)
                callback(
                    successCode,
                    "",
                    MomentUserStateSetting(
                        toUid = data.getString("to_uid") ?: uid,
                        hideMyMoment = data.getIntValue("hide_my_moment") == 1 || data.getBooleanValue("hide_my_moment"),
                        hideHisMoment = data.getIntValue("hide_his_moment") == 1 || data.getBooleanValue("hide_his_moment")
                    )
                )
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, MomentUserStateSetting(toUid = uid))
            }
        })
    }

    fun updateHideMyMoment(uid: String, enabled: Boolean, callback: (Int, String) -> Unit) {
        request(service.setHideMyMoment(uid, if (enabled) 1 else 0), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    fun updateHideHisMoment(uid: String, enabled: Boolean, callback: (Int, String) -> Unit) {
        request(service.setHideHisMoment(uid, if (enabled) 1 else 0), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    fun updateCover(localPath: String, callback: (Int, String, String) -> Unit) {
        uploadFile(localPath, "momentcover") { code, msg, remotePath ->
            if (code != successCode || remotePath.isNullOrEmpty()) {
                callback(code, msg, "")
                return@uploadFile
            }
            val body = JSONObject()
            body["cover"] = remotePath
            request(service.updateProfileCover(body), object : IRequestResultListener<JSONObject> {
                override fun onSuccess(result: JSONObject) {
                    callback(successCode, "", remotePath)
                }

                override fun onFail(code: Int, msg: String) {
                    callback(code, msg, "")
                }
            })
        }
    }

    fun publishMoment(
        text: String,
        medias: List<MomentComposeMedia>,
        locationTitle: String,
        mentionSelection: MomentAudienceSelection,
        visibilitySelection: MomentAudienceSelection,
        callback: (Int, String) -> Unit
    ) {
        uploadComposeMedia(medias) { code, msg, uploaded ->
            if (code != successCode) {
                callback(code, msg)
                return@uploadComposeMedia
            }
            val body = JSONObject()
            body["text"] = text
            body["client_req_id"] = System.currentTimeMillis().toString()
            if (locationTitle.isNotEmpty()) {
                val location = JSONObject()
                location["title"] = locationTitle
                body["location"] = location
            }
            if (uploaded.isNotEmpty()) {
                if (uploaded.first().type == MomentComposeMedia.TYPE_VIDEO) {
                    val first = uploaded.first()
                    val video = JSONObject()
                    video["media_url"] = first.localPath
                    video["cover_url"] = first.coverPath
                    video["width"] = first.width
                    video["height"] = first.height
                    video["duration"] = first.durationMs
                    video["size"] = first.size
                    body["video"] = video
                } else {
                    val images = JSONArray()
                    uploaded.forEachIndexed { index, media ->
                        val image = JSONObject()
                        image["media_url"] = media.localPath
                        image["sort_index"] = index + 1
                        image["width"] = media.width
                        image["height"] = media.height
                        image["size"] = media.size
                        images.add(image)
                    }
                    body["images"] = images
                }
            }
            body["mention"] = buildMentionBody(mentionSelection)
            body["visibility"] = buildVisibilityBody(visibilitySelection)
            request(service.publish(body), object : IRequestResultListener<JSONObject> {
                override fun onSuccess(result: JSONObject) {
                    callback(successCode, "")
                }

                override fun onFail(code: Int, msg: String) {
                    callback(code, msg)
                }
            })
        }
    }

    fun toggleLike(postId: String, callback: (Int, String, Boolean) -> Unit) {
        request(service.toggleLike(postId), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "", unwrapObject(result).getIntValue("liked") == 1)
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, false)
            }
        })
    }

    fun addComment(postId: String, content: String, replyCommentId: String?, callback: (Int, String, String) -> Unit) {
        val body = JSONObject()
        body["content"] = content
        if (!replyCommentId.isNullOrEmpty()) {
            body["reply_comment_id"] = replyCommentId
        }
        request(service.addComment(postId, body), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "", unwrapObject(result).getString("comment_id") ?: "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, "")
            }
        })
    }

    fun deleteComment(postId: String, commentId: String, callback: (Int, String) -> Unit) {
        request(service.deleteComment(postId, commentId), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    fun deletePost(postId: String, callback: (Int, String) -> Unit) {
        request(service.deletePost(postId), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    fun syncNotices(version: Long, limit: Int, callback: (Int, String, List<MomentNotice>, Long) -> Unit) {
        request(service.syncNotices(version, limit), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                val data = unwrapObject(result)
                callback(
                    successCode,
                    "",
                    parseNoticeList(data.getJSONArray("list")),
                    data.getLongValue("version")
                )
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg, emptyList(), version)
            }
        })
    }

    fun markNoticesRead(ids: List<Long>, readAll: Boolean, callback: (Int, String) -> Unit) {
        val body = JSONObject()
        body["read_all"] = readAll
        body["ids"] = JSONArray().apply { ids.forEach { add(it) } }
        request(service.markNoticesRead(body), object : IRequestResultListener<JSONObject> {
            override fun onSuccess(result: JSONObject) {
                callback(successCode, "")
            }

            override fun onFail(code: Int, msg: String) {
                callback(code, msg)
            }
        })
    }

    private fun uploadComposeMedia(
        medias: List<MomentComposeMedia>,
        callback: (Int, String, List<MomentComposeMedia>) -> Unit
    ) {
        if (medias.isEmpty()) {
            callback(successCode, "", emptyList())
            return
        }
        val uploaded = mutableListOf<MomentComposeMedia>()
        uploadComposeMediaInternal(medias, 0, uploaded, callback)
    }

    private fun uploadComposeMediaInternal(
        medias: List<MomentComposeMedia>,
        index: Int,
        uploaded: MutableList<MomentComposeMedia>,
        callback: (Int, String, List<MomentComposeMedia>) -> Unit
    ) {
        if (index >= medias.size) {
            callback(successCode, "", uploaded)
            return
        }
        val media = medias[index]
        if (media.type == MomentComposeMedia.TYPE_VIDEO) {
            uploadFile(media.coverPath.orEmpty(), "moment") { coverCode, coverMsg, coverPath ->
                if (coverCode != successCode || coverPath.isNullOrEmpty()) {
                    callback(coverCode, coverMsg, uploaded)
                    return@uploadFile
                }
                uploadFile(media.localPath, "moment") { videoCode, videoMsg, videoPath ->
                    if (videoCode != successCode || videoPath.isNullOrEmpty()) {
                        callback(videoCode, videoMsg, uploaded)
                        return@uploadFile
                    }
                    uploaded += media.copy(localPath = videoPath, coverPath = coverPath)
                    uploadComposeMediaInternal(medias, index + 1, uploaded, callback)
                }
            }
            return
        }
        uploadFile(media.localPath, "moment") { code, msg, remotePath ->
            if (code != successCode || remotePath.isNullOrEmpty()) {
                callback(code, msg, uploaded)
                return@uploadFile
            }
            uploaded += media.copy(localPath = remotePath)
            uploadComposeMediaInternal(medias, index + 1, uploaded, callback)
        }
    }

    private fun uploadFile(localPath: String, type: String, callback: (Int, String, String?) -> Unit) {
        if (localPath.isEmpty()) {
            callback(errorCode, "file path empty", null)
            return
        }
        val file = File(localPath)
        if (!file.exists()) {
            callback(errorCode, "file not found", null)
            return
        }
        val extension = file.extension.ifEmpty { "jpg" }
        val path = "/moment/${WKTimeUtils.getInstance().currentMills}.$extension"
        request(
            createService(ApiService::class.java).getUploadFileUrl("${WKApiConfig.baseUrl}file/upload?type=$type&path=$path"),
            object : IRequestResultListener<UploadFileUrl> {
                override fun onSuccess(result: UploadFileUrl) {
                    val requestBody = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
                    val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
                    request(createService(UploadService::class.java).upload(result.url, part), object : IRequestResultListener<UploadResultEntity> {
                        override fun onSuccess(result: UploadResultEntity) {
                            callback(successCode, "", result.path)
                        }

                        override fun onFail(code: Int, msg: String) {
                            callback(code, msg, null)
                        }
                    })
                }

                override fun onFail(code: Int, msg: String) {
                    callback(code, msg, null)
                }
            }
        )
    }

    private fun buildMentionBody(selection: MomentAudienceSelection): JSONObject {
        val body = JSONObject()
        body["uids"] = JSONArray().apply { selection.users.forEach { add(it.uid) } }
        body["tag_ids"] = JSONArray().apply { selection.tags.forEach { add(it.id) } }
        return body
    }

    private fun buildVisibilityBody(selection: MomentAudienceSelection): JSONObject {
        val body = JSONObject()
        body["type"] = when (selection.type) {
            MomentVisibilityType.PRIVATE -> MomentVisibilityType.PRIVATE
            MomentVisibilityType.PARTIAL_VISIBLE -> MomentVisibilityType.PARTIAL_VISIBLE
            MomentVisibilityType.EXCLUDE_VISIBLE -> MomentVisibilityType.EXCLUDE_VISIBLE
            else -> MomentVisibilityType.PUBLIC
        }
        body["uids"] = JSONArray().apply { selection.users.forEach { add(it.uid) } }
        body["tag_ids"] = JSONArray().apply { selection.tags.forEach { add(it.id) } }
        return body
    }

    private fun parseFeedPage(root: JSONObject): MomentFeedPage {
        val data = unwrapObject(root)
        val page = MomentFeedPage()
        page.uid = data.getString("uid") ?: ""
        page.cover = data.getString("cover") ?: ""
        parsePostList(data.getJSONArray("list")).forEach { page.list += it }
        return page
    }

    private fun parseProfile(root: JSONObject): MomentProfile {
        val data = unwrapObject(root)
        return MomentProfile(
            uid = data.getString("uid") ?: "",
            cover = data.getString("cover") ?: "",
            version = data.getLongValue("version")
        )
    }

    private fun parsePostList(array: JSONArray?): List<MomentPost> {
        if (array == null) return emptyList()
        return array.mapNotNull { item -> (item as? JSONObject)?.let(::parsePost) }
    }

    private fun parsePost(data: JSONObject): MomentPost {
        val post = MomentPost()
        post.postId = stringValue(data, "post_id", "id")
        post.text = data.getString("text") ?: ""
        post.createdAt = data.getString("created_at") ?: ""
        post.canDelete = data.getIntValue("can_delete") == 1 || data.getBooleanValue("can_delete")
        post.likedByMe = data.getIntValue("liked_by_me") == 1 || data.getBooleanValue("liked_by_me")
        post.user = parseUser(data.getJSONObject("user"))
        post.locationTitle = data.getString("location_name")
            ?: data.getString("location_title")
            ?: data.getJSONObject("location")?.getString("title")
            ?: ""
        data.getJSONArray("mentions")?.forEach { item ->
            val obj = item as? JSONObject ?: return@forEach
            post.mentionUsers += MomentUserChoice(
                uid = stringValue(obj, "uid"),
                name = stringValue(obj, "name", "nickname")
            )
        }
        data.getJSONArray("medias")?.forEach { item ->
            val obj = item as? JSONObject ?: return@forEach
            post.medias += MomentMedia(
                type = stringValue(obj, "type", "media_type"),
                url = stringValue(obj, "url", "media_url"),
                coverUrl = stringValue(obj, "cover_url"),
                width = obj.getIntValue("width"),
                height = obj.getIntValue("height"),
                duration = obj.getLongValue("duration"),
                size = obj.getLongValue("size"),
                sortIndex = obj.getIntValue("sort_index")
            )
        }
        data.getJSONArray("likes")?.forEach { item ->
            val obj = item as? JSONObject ?: return@forEach
            post.likes += MomentLike(
                uid = stringValue(obj, "uid"),
                name = stringValue(obj, "name", "nickname")
            )
        }
        data.getJSONArray("comments")?.forEach { item ->
            val obj = item as? JSONObject ?: return@forEach
            post.comments += MomentComment(
                commentId = stringValue(obj, "comment_id", "id"),
                content = obj.getString("content") ?: "",
                createdAt = obj.getString("created_at") ?: "",
                user = parseUser(obj.getJSONObject("user")),
                replyUser = parseReplyUser(obj)
            )
        }
        post.medias.sortBy { it.sortIndex }
        WKLogUtils.d("MomentModel", "此动态原始created_at moment post raw created_at postId=${post.postId} createdAt=${post.createdAt}")
        return post
    }

    private fun parseNoticeList(array: JSONArray?): List<MomentNotice> {
        if (array == null) return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JSONObject ?: return@mapNotNull null
            MomentNotice(
                id = obj.getLongValue("id"),
                noticeType = stringValue(obj, "notice_type"),
                isRead = obj.getIntValue("is_read") == 1 || obj.getBooleanValue("is_read"),
                version = obj.getLongValue("version"),
                createdAt = obj.getString("created_at") ?: "",
                fromUser = parseUser(obj.getJSONObject("from_user")),
                postId = obj.getString("post_id") ?: "",
                commentId = obj.getString("comment_id") ?: "",
                content = obj.getString("content") ?: "",
                mediaThumb = obj.getString("media_thumb") ?: obj.getString("thumb") ?: ""
            )
        }
    }

    private fun parseUser(data: JSONObject?): MomentUser {
        if (data == null) return MomentUser()
        return MomentUser(
            uid = stringValue(data, "uid"),
            name = stringValue(data, "name", "nickname"),
            avatar = stringValue(data, "avatar")
        )
    }

    private fun parseReplyUser(data: JSONObject): MomentUser? {
        val replyUid = stringValue(data, "reply_uid")
        val replyName = stringValue(data, "reply_name")
        if (replyUid.isNotEmpty() || replyName.isNotEmpty()) {
            return MomentUser(
                uid = replyUid,
                name = replyName.ifEmpty { replyUid },
                avatar = ""
            )
        }
        return data.getJSONObject("reply_user")?.let(::parseUser)?.takeIf {
            it.uid.isNotEmpty() || it.name.isNotEmpty()
        }
    }

    private fun unwrapObject(root: JSONObject): JSONObject {
        return root.getJSONObject("data") ?: root
    }

    private fun stringValue(data: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = data.getString(key)
            if (!value.isNullOrEmpty()) {
                return value
            }
        }
        return ""
    }
}
