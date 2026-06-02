package com.chat.rtc;

import android.app.Application;

public class WKRTCApplication {
    private static class Binder {
        private static final WKRTCApplication APP = new WKRTCApplication();
    }

    public static WKRTCApplication getInstance() {
        return Binder.APP;
    }

    public void init(Application application) {
        RtcManager.getInstance().init(application);
    }
}
