# Keep Google Auth and Drive API
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.api.** { *; }

# Keep Room Database completely intact
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# Keep WorkManager completely intact
-keep class androidx.work.** { *; }
-keepclassmembers class * extends androidx.work.Worker { *; }

# Keep your own models (assuming they are in this package)
-keep class com.example.tripexpensemanager.model.** { *; }

# Ignore missing classes from Apache HTTP and Java Naming/GSS
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**