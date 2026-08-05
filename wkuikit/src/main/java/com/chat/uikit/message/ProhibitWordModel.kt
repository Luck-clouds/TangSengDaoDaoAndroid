package com.chat.uikit.message

import android.util.Log
import com.chat.base.base.WKBaseModel
import com.chat.base.config.WKConstants
import com.chat.base.endpoint.EndpointCategory
import com.chat.base.endpoint.EndpointManager
import com.chat.base.net.IRequestResultListener
import com.chat.base.utils.WKReader
import com.chat.uikit.db.ProhibitWordDB
import com.chat.uikit.enity.ProhibitWord

class ProhibitWordModel private constructor() : WKBaseModel() {
    companion object {
        private const val TAG = "WKProhibitWordsSync"
        val instance = SingletonHolder.holder
    }

    private object SingletonHolder {
        val holder = ProhibitWordModel()
    }

    private var words: ArrayList<ProhibitWord> = ArrayList()
    fun getAll(): List<ProhibitWord> {
        if (words.isEmpty()) {
            words = ProhibitWordDB.instance.getAll()
        }
        return words
    }

    fun sync() {
        if (!WKConstants.isLogin()) return
        val version = ProhibitWordDB.instance.getMaxVersion()
        Log.i(TAG, "start, localVersion=$version")
        request(createService(MsgService::class.java).syncProhibitWord(version),
            object : IRequestResultListener<List<ProhibitWord>> {
                override fun onSuccess(result: List<ProhibitWord>) {
                    if (WKReader.isNotEmpty(result)) {
                        ProhibitWordDB.instance.save(result)
                        words.clear()
                        getAll()
                        val list: List<Any>? = EndpointManager.getInstance()
                            .invokes(EndpointCategory.refreshProhibitWord, 1)
                    }
                    Log.i(TAG, "success, received=${result.size}, active=${getAll().size}")
                }

                override fun onFail(code: Int, msg: String?) {
                    Log.e(TAG, "failed, code=$code, msg=$msg")
                }
            })
    }
}
