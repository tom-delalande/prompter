-keep class co.delalande.prompter.** { *; }

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

-keep,includedescriptorclasses class co.delalande.prompter.**$$serializer { *; }
-keepclassmembers class co.delalande.prompter.** {
    *** Companion;
}
-keepclasseswithmembers class co.delalande.prompter.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip verbose/debug/info logging in release builds.
# The lazy message lambdas are removed together with the calls, so no string
# interpolation cost remains. warn/error still log.
-assumenosideeffects class co.delalande.prompter.log.Log {
    public void v(java.lang.String, kotlin.jvm.functions.Function0);
    public void d(java.lang.String, kotlin.jvm.functions.Function0);
    public void i(java.lang.String, kotlin.jvm.functions.Function0);
}
