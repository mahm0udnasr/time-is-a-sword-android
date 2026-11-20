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

# Glance stores "receiver → widget class" by class NAME and reads it back after an update.
# R8 renames classes differently in every build, so the stored names would point at the wrong
# widget (seen: the Prayer widget drawn with the Today design). Keep the real names.
-keep class com.mahm0udnasr.sword.widget.TodayWidget { *; }
-keep class com.mahm0udnasr.sword.widget.PrayerWidget { *; }
-keep class com.mahm0udnasr.sword.widget.TodayWidgetReceiver { *; }
-keep class com.mahm0udnasr.sword.widget.PrayerWidgetReceiver { *; }
