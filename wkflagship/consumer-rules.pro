# 消息回执成员由 Retrofit/Gson 反射创建。必须保留类与无参构造器，
# 否则 Release 开启 R8 后，点击非空的“未读”列表会因模型被裁剪而崩溃。
-keep class com.chat.flagship.entity.FlagshipReceiptUser { *; }
