-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.riffado.meetings.** {
    *** Companion;
}
-keepclasseswithmembers class com.riffado.meetings.** {
    kotlinx.serialization.KSerializer serializer(...);
}
