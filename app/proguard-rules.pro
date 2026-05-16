# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 自定义混淆字典
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary dictionary.txt
-packageobfuscationdictionary dictionary.txt


-optimizationpasses 5

#包明不混合大小写
-dontusemixedcaseclassnames

#不去忽略非公共的库类
-dontskipnonpubliclibraryclasses
-dontskipnonpubliclibraryclassmembers

# keep 泛型
-keepattributes Signature
-keep interface android.database.Cursor { *; }

-keep class androidx.databinding.** { *; }
-keep public class * extends androidx.databinding.DataBinderMapper
-keep class com.qinghangim.app.TSApplication { *; }

# 保持 xml onClick 处理方法
-keepclassmembers class * extends androidx.appcompat.app.AppCompatActivity{
    public void *(android.view.View);
}

# 保持 XML 中直接引用的自定义 View 类名
-keepnames class com.chat.base.ui.components.**
-keepnames class com.chat.base.views.**
-keepnames class com.chat.base.jsbrigde.BridgeWebView
-keepnames class com.chat.base.act.VideoPlayer
-keepnames class com.chat.uikit.view.**
-keepnames class com.chat.flagship.picture.view.**

# 保持 XML inflate 需要的构造器
-keepclassmembers class com.chat.base.ui.components.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}
-keepclassmembers class com.chat.base.views.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}
-keepclassmembers class com.chat.base.jsbrigde.BridgeWebView {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}
-keepclassmembers class com.chat.base.act.VideoPlayer {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}
-keepclassmembers class com.chat.uikit.view.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}
-keepclassmembers class com.chat.flagship.picture.view.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context,android.util.AttributeSet);
    public <init>(android.content.Context,android.util.AttributeSet,int);
}

# 保持native方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}

#枚举不被混淆
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

 # 保持Parcelable不被混淆
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

#xpopu
-dontwarn com.lxj.xpopup.widget.**
-keep class com.lxj.xpopup.widget.**{*;}

#okhttp
-dontwarn okhttp3.**
#okio
-dontwarn okio.**

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
-keepattributes *Annotation
-keep class * implements java.lang.annotation.Annotation { *; }
-keep class com.alibaba.fastjson.* { *; }
-keep class com.ling.fast.bean** { *; }
#PictureSelector 2.0
-keep class com.luck.picture.lib.** { *; }
#Ucrop
-dontwarn com.yalantis.ucrop**
-keep class com.yalantis.ucrop** { *; }
-keep interface com.yalantis.ucrop** { *; }

#华为
-ignorewarnings
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
-keep class com.huawei.updatesdk.**{*;}
-keep class com.huawei.hms.**{*;}
-keep class com.huawei.android.hms.agent.**{*;}
-keep class com.huawei.hianalytics.**{*;}

-keepattributes Signature
-keepattributes Exceptions

#高德地图
-keep class com.amap.api.maps.**{*;}
-keep class com.autonavi.**{*;}
-keep class com.amap.api.trace.**{*;}
-keep class com.amap.api.location.**{*;}
-keep class com.amap.api.fence.**{*;}
-keep class com.loc.**{*;}
-keep class com.autonavi.aps.amapapi.model.**{*;}
-keep class com.amap.api.services.**{*;}
-keep class com.chat.map.ui.DatasKey { *; }


#实体类不需要混淆[需要和网络交换的实体]
#----------悟空 sdk 数据如果不需要sdk的实体参与网络数据交互就不需要混淆
-dontwarn com.xinbida.wukongim.**
-keep class com.xinbida.wukongim.**{*;}

#数据库加密
-keep,includedescriptorclasses class net.sqlcipher.** { *; }
-keep,includedescriptorclasses interface net.sqlcipher.** { *; }

#--------- 混淆dh curve25519-------
-keep class org.whispersystems.curve25519.**{*;}
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }

-dontwarn org.xsocket.**
-keep class org.xsocket.** {*;}

#----------基础模块（wkbase / com.chat.base）----------
-keepclassmembers class com.chat.base.entity.** { <fields>; }
-keep class com.chat.base.base.** { *; }
-keepclassmembers class com.chat.base.net.entity.** { <fields>; }
#----------登录模块（wklogin / com.chat.login）----------
-keepclassmembers class com.chat.login.entity.** { <fields>; }
#----------UIKit 模块（wkuikit / com.chat.uikit）----------
-keepclassmembers class com.chat.uikit.chat.msgmodel.** { <fields>; }
-keepclassmembers class com.chat.uikit.enity.** { <fields>; }
-keepclassmembers class com.chat.uikit.group.service.entity.** { <fields>; }
-keepclassmembers class com.chat.uikit.message.Ipentity { <fields>; }
-keepclassmembers class com.chat.uikit.message.SyncMsg { <fields>; }
-keepclassmembers class com.chat.uikit.message.SyncMsgHeader { <fields>; }
-keepclassmembers class com.chat.uikit.search.SearchUserEntity { <fields>; }
-keepclassmembers class com.chat.uikit.robot.entity.** { <fields>; }
-keep class com.chat.base.msg.model.WKGifContent {
    public <init>();
}
-keep class com.chat.uikit.chat.msgmodel.WKCardContent {
    public <init>();
}
-keep class com.chat.uikit.chat.msgmodel.WKFileContent {
    public <init>();
}
-keep class com.chat.uikit.chat.msgmodel.WKMultiForwardContent {
    public <init>();
}
-keep class com.chat.uikit.chat.provider.** {
    public <init>();
}
#----------群管理模块（历史包名保留）----------
-keepclassmembers class com.chat.groupmanage.entity.** { <fields>; }
#----------文件模块（历史包名保留）----------
-keep class com.chat.file.msgitem.FileContent { *; }
#----------收藏模块（历史包名保留）----------
-keepclassmembers class com.chat.favorite.entity.**{ <fields>; }
#----------扫一扫模块（wkscan / com.chat.scan）----------
-keepclassmembers class com.chat.scan.entity.** { <fields>; }
#----------朋友圈模块（wkmoments / com.chat.moments）----------
-keepclassmembers class com.chat.moments.entity.** { <fields>; }
#----------标签模块（历史包名保留）----------
-keepclassmembers class com.chat.label.entity.** { <fields>; }
#----------表情模块（wksticker / com.chat.sticker）----------
-keepclassmembers class com.chat.sticker.entity.** { <fields>; }
-keep class com.chat.sticker.msg.WKVectorStickerContent {
    public <init>();
}
-keep class com.chat.sticker.msg.WKEmojiStickerContent {
    public <init>();
}
-keep class com.chat.sticker.ui.provider.WKStickerProvider {
    public <init>();
    public <init>(int);
}
#----------客服模块（历史包名保留）----------
-keepclassmembers class com.chat.customerservice.entity.** { <fields>; }
#----------安全与隐私功能说明----------
# 当前安全与隐私功能并入 wkuikit.setting，没有独立的 com.chat.security.entity 包
#----------旗舰模块（wkflagship / com.chat.flagship）----------
-keepclassmembers class com.chat.flagship.entity.** { <fields>; }
-keepclassmembers class com.chat.flagship.msgmodel.** { <fields>; }
-keep class com.chat.flagship.msgmodel.WKScreenShotContent {
    public <init>();
}
-keep class com.chat.flagship.msgmodel.WKRichTextContent {
    public <init>();
}
-keep class com.chat.flagship.provider.** {
    public <init>();
}
#----------音视频模块（wkvideo / com.chat.video）----------
-keep class com.chat.video.contract.** { *; }
-keep class com.chat.video.session.** { *; }
-keep class com.chat.video.provider.** {
    public <init>();
}
-keep class owt.**{*;}
-keep class org.webrtc.**{*;}
#----------社区模块（历史包名保留）----------
-keepclassmembers class com.community.entity.** { <fields>; }
#----------用户名登录模块（历史包名保留）----------
-keepclassmembers class com.chat.wkusernamelogin.entity.** { <fields>; }
#----------Web3 模块（历史包名保留）----------
-keepclassmembers class com.chat.wkweb3.entity.** { <fields>; }
#----------工作台模块（历史包名保留）----------
-keepclassmembers class com.chat.workplace.entity.** { <fields>; }
#----------组织架构模块（历史包名保留）----------
-keepclassmembers class com.chat.organization.entity.** { <fields>; }
#----------消息置顶模块（历史包名保留）----------
-keepclassmembers class com.chat.pinned.message.entity.** { <fields>; }
#----------注册邀请模块（历史包名保留）----------
-keepclassmembers class com.chat.invite.entity.** { <fields>; }

-keep class org.web3j.**{*;}
-dontwarn org.web3j.**

#---------rxjava retrofit 混淆-----------------
-dontnote retrofit2.Platform

# RxJava RxAndroid
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}


#-------------gsy混淆-----------------
-keep class com.shuyu.gsyvideoplayer.** { *; }
-keep class com.shuyu.gsyvideoplayer.video.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.**
-keep class com.shuyu.gsyvideoplayer.video.base.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.video.base.**
-keep class com.shuyu.gsyvideoplayer.utils.** { *; }
-dontwarn com.shuyu.gsyvideoplayer.utils.**
-keep class tv.danmaku.ijk.** { *; }
-dontwarn tv.danmaku.ijk.**
-keep class androidx.media3.** {*;}
-keep interface androidx.media3.**

-keep class com.shuyu.alipay.** {*;}
-keep interface com.shuyu.alipay.**

#--------角标---------
-keep class me.leolin.shortcutbadger.**{*;}
#-------------x5webview------------
-dontwarn dalvik.**
-dontwarn com.tencent.smtt.**

-keep class com.tencent.smtt.** {
    *;
}
-keep class com.tencent.tbs.** {
    *;
}
-dontwarn com.tencent.bugly.**
-keep public class com.tencent.bugly.**{*;}

-keep class com.tbruyelle.rxpermissions3.**{*;}

#-------------小米推送-----------
#这里com.xiaomi.mipushdemo.DemoMessageRreceiver改成app中定义的完整类名
-keep class com.chat.push.push.XiaoMiMessageReceiver {*;}
#可以防止一个误报的 warning 导致无法成功编译，如果编译使用的 Android 版本是 23。
-dontwarn com.xiaomi.push.**

#-----------oppo----------------
-keep class com.heytap.msp.** { *;}

#------------FMC---------------
# 保留Firebase的类和成员不被混淆
-keep class com.google.firebase.** { *; }
-keep class org.apache.** { *; }
-keep class javax.** { *; }
-keep class uk.** { *; }

# 如果你使用Firebase动态链接或通知等，还需要添加以下规则
-keep class com.google.firebase.dynamiclinks.** { *; }
-keep class com.google.firebase.messaging.** { *; }

# 如果你使用Firebase Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }

# 如果你使用Firebase Crashlytics
-keep class com.google.firebase.crash.** { *; }
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**

#---------音视频------------------
-dontwarn org.json.**
-dontwarn com.google.gson.**
-dontwarn aidl.**
-keep class aidl.** { *; }
-keep class io.socket.** {*;}
-dontwarn io.socket.**

-keep class com.xinbida.rtc.WKRTCApplication {*;}
-keep class com.xinbida.rtc.WKRTCCallType {*;}
-keep class com.xinbida.rtc.utils.WKRTCManager {*;}
-keep class com.xinbida.rtc.inters.** {*;}
-keep class owt.** {*;}
-keep class org.webrtc.** {*;}

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;  public *;
}
