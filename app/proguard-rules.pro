# Release shrinking (step 11.2).

# kotlinx.serialization: the library ships its own rules; keep our models' generated
# serializers anyway, because the stored JSON (config.json/state.json, backups) must keep
# reading after every update.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.mahm0udnasr.sword.**$$serializer { *; }
-keepclassmembers class com.mahm0udnasr.sword.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.mahm0udnasr.sword.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Entry points Android creates by name (the manifest keeps them; listed for clarity).
-keep class com.mahm0udnasr.sword.service.SwordAccessibilityService
-keep class com.mahm0udnasr.sword.admin.SwordDeviceAdmin
-keep class com.mahm0udnasr.sword.system.HealthCheck$Worker { <init>(...); }

# Readable crash traces from users' phones.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
