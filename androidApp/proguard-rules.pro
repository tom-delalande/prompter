-keep class com.teleprompter.** { *; }

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.teleprompter.**$$serializer { *; }
-keepclassmembers class com.teleprompter.** {
    *** Companion;
}
-keepclasseswithmembers class com.teleprompter.** {
    kotlinx.serialization.KSerializer serializer(...);
}
