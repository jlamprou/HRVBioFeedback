# Polar BLE SDK
-keep class com.polar.sdk.** { *; }
-keep class fi.polar.remote.representation.protobuf.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Hilt
-keep class dagger.hilt.** { *; }
