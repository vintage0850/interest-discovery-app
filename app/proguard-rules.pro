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

# ---- Google Calendar 連携（Retrofit + kotlinx.serialization）----
# 依存ライブラリの consumer-proguard-rules で大半はカバーされるが、
# リフレクション経由で生成される serializer が release ビルドで剥がれると
# 実機でのみ CalendarEventRequest/Response のシリアライズが失敗する（ビルドは通ってしまう）ため、
# 通信に使う DTO は明示的に温存する。
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.example.myapplication.data.calendar.**$$serializer { *; }
-keepclassmembers class com.example.myapplication.data.calendar.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.myapplication.data.calendar.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking interface com.example.myapplication.data.calendar.GoogleCalendarApi

# ---- WorkManager（空き時間検知）----
# WorkManager の既定 WorkerFactory は (Context, WorkerParameters) の2引数コンストラクタを
# リフレクションで探す。@JvmOverloads で生成されるその2引数コンストラクタが release ビルドで
# 剥がれると実機でのみ Worker の生成に失敗するため、明示的に温存する。
-keep class com.example.myapplication.work.FreeTimeCheckWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.example.myapplication.work.DiscoveryFreeTimeWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keep class com.example.myapplication.work.DiscoveryPeriodicReportWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}