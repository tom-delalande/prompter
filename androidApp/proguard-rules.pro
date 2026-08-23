-keep class co.delalande.prompter.** { *; }

# ML Kit GenAI speech recognition is wired at runtime through the
# firebase-components registry (classes referenced by name from merged
# manifest meta-data and static Component fields). R8 breaks that wiring,
# which makes SpeechRecognition.getClient() fail with a bare NPE
# ("Recognizer init failed: null") only in minified builds.
-keep class com.google.mlkit.** { *; }
-keep class com.google.firebase.components.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_genai_speech.** { *; }

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
