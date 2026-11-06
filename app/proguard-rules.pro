# ProGuard & R8 configuration for Sentinel Music Android

# Вырезание вызовов android.util.Log в релизных сборках (MobSF android_kotlin_logging)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Сохранение моделей данных Gson / Retrofit
-keepclassmembers class com.sentinel.music.data.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
