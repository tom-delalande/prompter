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
