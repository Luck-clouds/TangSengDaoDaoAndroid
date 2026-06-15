package com.chat.base.net
import android.util.Log
import com.alibaba.fastjson.JSON
import okhttp3.ResponseBody
import retrofit2.Converter
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Collection

class FastJsonResponseBodyConverter<T>(
    private val type: Type
) : Converter<ResponseBody, T> {

    override fun convert(value: ResponseBody): T? {
        val body = value.use { it.string() }
        return try {
            if (body.isBlank() && isCollectionType(type)) {
                @Suppress("UNCHECKED_CAST")
                emptyList<Any>() as T
            } else {
                val result: T? = JSON.parseObject(body, type)
                if (result == null && isCollectionType(type)) {
                    @Suppress("UNCHECKED_CAST")
                    emptyList<Any>() as T
                } else {
                    result ?: throw IllegalStateException("response body parsed null, type=$type")
                }
            }
        } catch (e: RuntimeException) {
            Log.e("FastJsonConverter", "parse failed type=$type", e)
            throw e
        }
    }

    private fun isCollectionType(type: Type): Boolean {
        val rawType = when (type) {
            is ParameterizedType -> type.rawType
            else -> type
        }
        return rawType is Class<*> && Collection::class.java.isAssignableFrom(rawType)
    }
}
