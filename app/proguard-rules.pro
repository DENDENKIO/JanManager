# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Hilt / Dagger
-keep class dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class com.google.dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class *
-dontwarn dagger.hilt.internal.aggregateddeps.**

# Kotlin Serialization
-keepattributes *Annotation*, EnclosingMethod, Signature, InnerClasses
-keepclassmembernames class kotlinx.serialization.json.** { *; }
-keep class com.example.janmanager.ui.navigation.Route** { *; }
-keepclassmembers class com.example.janmanager.ui.navigation.Route** { *; }
-keep class kotlinx.serialization.** { *; }

# AI Response Data
-keep class com.example.janmanager.util.AiResponseData { *; }
-keepclassmembers class com.example.janmanager.util.AiResponseData { *; }

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Enum protection for serialization
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
