package com.qinghangim.app

import android.app.Activity
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.TextUtils
import android.util.Log
import androidx.multidex.MultiDexApplication
import com.chat.base.WKBaseApplication
import com.chat.base.config.WKApiConfig
import com.chat.base.config.WKConfig
import com.chat.base.config.WKConstants
import com.chat.base.config.WKSharedPreferencesUtil
import com.chat.base.endpoint.EndpointManager
import com.chat.base.ui.Theme
import com.chat.base.utils.ActManagerUtils
import com.chat.base.utils.WKPlaySound
import com.chat.base.utils.WKTimeUtils
import com.chat.base.utils.language.WKMultiLanguageUtil
import com.chat.flagship.WKFlagshipApplication
import com.chat.login.WKLoginApplication
import com.chat.moments.WKMomentsApplication
// 厂商推送/FCM 已关闭；恢复 :wkpush 依赖后取消此 import 的注释。
// import com.chat.push.WKPushApplication
import com.chat.rtc.WKRTCApplication
import com.chat.scan.WKScanApplication
import com.chat.sticker.WKStickerApplication
import com.chat.uikit.TabActivity
import com.chat.uikit.WKUIKitApplication
import com.chat.uikit.chat.manager.WKIMUtils
import com.chat.uikit.setting.TeenModeManager
import com.chat.uikit.user.service.UserModel
import com.chat.video.WKVideoApplication
import com.qinghangim.app.R
import kotlin.system.exitProcess

class TSApplication : MultiDexApplication() {
    companion object {
        private const val TAG = "TSApplication"
        private const val API_BASE_URL_KEY = "api_base_url"
        private const val DEFAULT_API_URL = "https://api.qinghangim.com"
        @Volatile
        var appInForeground: Boolean = false
    }

    @Volatile
    private var businessInitialized = false

    override fun onCreate() {
        super.onCreate()
        val processName = getProcessName(this, Process.myPid())
        if (processName != null) {
            val defaultProcess = processName == getAppPackageName()
            if (defaultProcess) {
                initBeforePrivacyConsent()
                if (!WKSharedPreferencesUtil.getInstance()
                        .getBoolean("show_agreement_dialog")) {
                    initializeAfterPrivacyConsent()
                }
            }
        }
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(p0: Activity, p1: Bundle?) {
            }

            override fun onActivityStarted(p0: Activity) {
            }

            override fun onActivityResumed(p0: Activity) {
                ActManagerUtils.getInstance().currentActivity = p0
            }

            override fun onActivityPaused(p0: Activity) {
            }

            override fun onActivityStopped(p0: Activity) {
            }

            override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
            }

            override fun onActivityDestroyed(p0: Activity) {
            }
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (applicationContext != null && applicationContext.resources != null && applicationContext.resources.configuration != null && applicationContext.resources.configuration.uiMode != newConfig.uiMode) {
            WKMultiLanguageUtil.getInstance().setConfiguration()
            Theme.applyTheme()
            killAppProcess()
        }
    }

    private fun killAppProcess() {
        ActManagerUtils.getInstance().clearAllActivity()
        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(WKMultiLanguageUtil.getInstance().attachBaseContext(base))
    }

    /**
     * Only initializes resources required to render the privacy dialog. No business
     * module or third-party SDK may be initialized before the user has consented.
     */
    private fun initBeforePrivacyConsent() {
        WKMultiLanguageUtil.getInstance().init(this)
        WKBaseApplication.getInstance().init(getAppPackageName(), this)
        Theme.applyTheme()
        initApi()
    }

    /** Called on a normal launch after consent, or immediately after the user agrees. */
    @Synchronized
    fun initializeAfterPrivacyConsent() {
        if (businessInitialized) return
        businessInitialized = true

        // Complete WKBaseApplication's deferred initialization first because all
        // following modules depend on its cache, configuration and endpoint setup.
        WKBaseApplication.getInstance().init(getAppPackageName(), this)
        WKLoginApplication.getInstance().init(this)
        WKScanApplication.getInstance().init(this)
        WKUIKitApplication.getInstance().init(this)
        WKFlagshipApplication.getInstance().init(this)
        WKVideoApplication.getInstance().init(this)
        WKMomentsApplication.getInstance().init(this)
        WKStickerApplication.getInstance().init(this)
        WKRTCApplication.getInstance().init(this)
        // 厂商推送/FCM 已关闭。恢复依赖后可在隐私协议同意后的此处重新初始化。
        // WKPushApplication.getInstance().init(getAppPackageName(), this)
        addAppFrontBack()
        addListener()
    }

    private fun initApi() {
        val apiURL = normalizeApiUrl(WKSharedPreferencesUtil.getInstance().getSP(API_BASE_URL_KEY))
        WKApiConfig.initBaseURLIncludeIP(apiURL)
        Log.i(TAG, "api base url: $apiURL")
    }

    private fun normalizeApiUrl(apiURL: String?): String {
        if (TextUtils.isEmpty(apiURL)) {
            return DEFAULT_API_URL
        }
        var normalized = apiURL!!.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        return normalized.trimEnd('/')
    }

    private fun getAppPackageName(): String {
        return packageName  // 动态获取实际的 applicationId
    }

    private fun getProcessName(cxt: Context, pid: Int): String? {
        val am = cxt.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningApps = am.runningAppProcesses ?: return null
        for (app in runningApps) {
            if (app.pid == pid) {
                return app.processName
            }
        }
        return null
    }

    private fun addAppFrontBack() {
        val helper = AppFrontBackHelper()
        helper.register(this, object : AppFrontBackHelper.OnAppStatusListener {
            override fun onFront() {
                appInForeground = true
                if (!TextUtils.isEmpty(WKConfig.getInstance().token)) {
                    if (WKBaseApplication.getInstance().disconnect) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            EndpointManager.getInstance()
                                .invoke("chow_check_lock_screen_pwd", null)
                        }, 1000)
                    }
                    WKIMUtils.getInstance().initIMListener()
                    WKUIKitApplication.getInstance().startChat()
                    UserModel.getInstance().getOnlineUsers()

                }
            }

            override fun onBack() {
                appInForeground = false
                val result = EndpointManager.getInstance().invoke("rtc_is_calling", null)
                var isCalling = false
                if (result != null) {
                    isCalling = result as Boolean
                }
                val rtcRegistered = EndpointManager.getInstance().invoke("is_register_rtc", null) as? Boolean ?: false
                if (WKBaseApplication.getInstance().disconnect && !isCalling && !rtcRegistered) {
                    WKUIKitApplication.getInstance().stopConn()
                }
                WKIMUtils.getInstance().removeListener()
                WKSharedPreferencesUtil.getInstance()
                    .putLong("lock_start_time", WKTimeUtils.getInstance().currentSeconds)
                TeenModeManager.getInstance().markVerifyPending()

            }
        })
    }

    private fun addListener() {

        createNotificationChannel()
        EndpointManager.getInstance().setMethod("update_base_url") { value ->
            val apiURL = normalizeApiUrl(value as? String)
            WKApiConfig.initBaseURLIncludeIP(apiURL)
            Log.i(TAG, "api base url updated: $apiURL")
            null
        }
        EndpointManager.getInstance().setMethod("main_show_home_view") { `object` ->
            if (`object` != null) {
                val from = `object` as Int
                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra("from", from)
                startActivity(intent)
            }
            null
        }
        EndpointManager.getInstance().setMethod("show_tab_home") {
            val intent = Intent(applicationContext, TabActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            null
        }

        EndpointManager.getInstance().setMethod("play_new_msg_Media") {
            WKPlaySound.getInstance().playRecordMsg(R.raw.newmsg)
            null
        }
        EndpointManager.getInstance().setMethod("app_is_foreground") {
            appInForeground
        }
        EndpointManager.getInstance().setMethod("play_rtc_media") {
            WKPlaySound.getInstance().playLoop(R.raw.newrtc)
            null
        }
        EndpointManager.getInstance().setMethod("stop_rtc_media") {
            WKPlaySound.getInstance().stopLoop()
            null
        }
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = applicationContext.getString(R.string.new_msg_notification)
            val description = applicationContext.getString(R.string.new_msg_notification_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(WKConstants.newMsgChannelID, name, importance)
            channel.description = description
            channel.enableVibration(true) //是否有震动
            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + applicationContext.packageName + "/" + R.raw.newmsg),
                Notification.AUDIO_ATTRIBUTES_DEFAULT
            )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = applicationContext.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        createNotificationRTCChannel()
    }

    private fun createNotificationRTCChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = applicationContext.getString(R.string.new_rtc_notification)
            val description = applicationContext.getString(R.string.new_rtc_notification_desc)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(WKConstants.newRTCChannelID, name, importance)
            channel.description = description
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(0, 100, 100, 100, 100, 100)
            channel.setSound(
                Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + applicationContext.packageName + "/" + R.raw.newrtc),
                Notification.AUDIO_ATTRIBUTES_DEFAULT
            )
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = applicationContext.getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

}
