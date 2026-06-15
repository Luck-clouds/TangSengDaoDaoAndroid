package com.chat.base.glide;

import android.text.TextUtils;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.Headers;
import com.bumptech.glide.load.model.LazyHeaders;
import com.chat.base.config.WKConfig;

public class MyGlideUrlWithId extends GlideUrl {

    private final String id;

    public MyGlideUrlWithId(String url, String id) {
        super(url, buildHeaders());
        this.id = id;
    }

    private static Headers buildHeaders() {
        String token = WKConfig.getInstance().getToken();
        if (TextUtils.isEmpty(token)) {
            return Headers.DEFAULT;
        }
        return new LazyHeaders.Builder()
                .addHeader("token", token)
                .build();
    }

    @Override
    public String getCacheKey() {
        return !TextUtils.isEmpty(id) ? id : super.getCacheKey();
    }

}
