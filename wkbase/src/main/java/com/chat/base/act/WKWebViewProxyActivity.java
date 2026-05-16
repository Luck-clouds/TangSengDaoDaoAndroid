package com.chat.base.act;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.chat.base.config.WKApiConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WKWebViewProxyActivity extends Activity {
    private static final String DEEP_LINK_SCHEME = "com.xinbida.tangsengdaodao";
    private static final String DEEP_LINK_HOST = "url";
    private static final List<String> URL_QUERY_KEYS = Arrays.asList("url", "target", "targetUrl", "redirect_url");

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forwardToInternalWebView(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        forwardToInternalWebView(intent);
        finish();
    }

    private void forwardToInternalWebView(@Nullable Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        String allowedUrl = resolveAllowedUrl(data);
        if (TextUtils.isEmpty(allowedUrl)) {
            return;
        }
        startActivity(WKWebViewActivity.createIntent(this, allowedUrl));
    }

    @Nullable
    private String resolveAllowedUrl(@Nullable Uri deepLinkUri) {
        if (deepLinkUri == null) {
            return null;
        }
        if (!DEEP_LINK_SCHEME.equalsIgnoreCase(deepLinkUri.getScheme())) {
            return null;
        }
        if (!DEEP_LINK_HOST.equalsIgnoreCase(deepLinkUri.getHost())) {
            return null;
        }
        String candidateUrl = extractUrl(deepLinkUri);
        if (TextUtils.isEmpty(candidateUrl)) {
            return null;
        }
        Uri targetUri = Uri.parse(candidateUrl.trim());
        if (!"https".equalsIgnoreCase(targetUri.getScheme())) {
            return null;
        }
        String targetHost = normalizeHost(targetUri.getHost());
        if (TextUtils.isEmpty(targetHost) || !isWhitelistedHost(targetHost)) {
            return null;
        }
        return targetUri.toString();
    }

    @Nullable
    private String extractUrl(Uri deepLinkUri) {
        for (String key : URL_QUERY_KEYS) {
            String value = deepLinkUri.getQueryParameter(key);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        Set<String> queryParameterNames = deepLinkUri.getQueryParameterNames();
        if (queryParameterNames.size() == 1) {
            String onlyKey = queryParameterNames.iterator().next();
            String value = deepLinkUri.getQueryParameter(onlyKey);
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isWhitelistedHost(String targetHost) {
        String baseHost = getBaseWebHost();
        if (TextUtils.isEmpty(baseHost)) {
            return false;
        }
        return targetHost.equals(baseHost) || targetHost.endsWith("." + baseHost);
    }

    @Nullable
    private String getBaseWebHost() {
        if (TextUtils.isEmpty(WKApiConfig.baseWebUrl)) {
            return null;
        }
        Uri baseUri = Uri.parse(WKApiConfig.baseWebUrl);
        if (!"https".equalsIgnoreCase(baseUri.getScheme())) {
            return null;
        }
        return normalizeHost(baseUri.getHost());
    }

    @Nullable
    private String normalizeHost(@Nullable String host) {
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        return host.toLowerCase(Locale.US);
    }
}
