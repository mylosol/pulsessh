# PulseSSH R8 / ProGuard rules.
#
# The app links against several plain-JVM libraries (Apache MINA SSHD, BouncyCastle,
# SQLCipher) that rely on reflection, ServiceLoader lookups and JNI. R8 cannot see
# those usages, so the classes involved are kept explicitly below. Each block states
# why it exists; if a rule can be narrowed later, narrow it rather than deleting it
# and finding out at runtime.

# ---------------------------------------------------------------------------
# Apache MINA SSHD
# Ciphers, MACs, key exchange factories and signature implementations are resolved
# by name through ServiceLoader / NamedFactory registries, never by direct
# reference, so nothing may be renamed or stripped. SSHD also probes for optional
# integrations (JGit, SLF4J bindings, NIO2 acceptors) whose absence is normal.
# ---------------------------------------------------------------------------
-keep class org.apache.sshd.** { *; }
-keepclassmembers class org.apache.sshd.** { *; }
-dontwarn org.apache.sshd.**
-keep,allowobfuscation @interface org.apache.sshd.**

# SSHD discovers implementations through META-INF/services entries.
-keepnames class * implements org.apache.sshd.common.NamedResource
-keepnames class * implements org.apache.sshd.common.NamedFactory

# ---------------------------------------------------------------------------
# BouncyCastle
# The JCE provider registers algorithms by building class names as strings
# ("org.bouncycastle.jcajce.provider.symmetric.AES$ECB" and friends) and
# instantiating them reflectively. Any renaming breaks key parsing and crypto.
# ---------------------------------------------------------------------------
-keep class org.bouncycastle.** { *; }
-keepclassmembers class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
# BouncyCastle references desktop-only and optional JDK APIs that do not exist on Android.
-dontwarn javax.naming.**
-dontwarn java.awt.**

# ---------------------------------------------------------------------------
# SQLCipher (net.zetetic)
# The encrypted SQLite implementation is a thin Java layer over a native library;
# native code calls back into these classes and their fields by JNI signature.
# ---------------------------------------------------------------------------
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclassmembers class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# ---------------------------------------------------------------------------
# Room
# Generated *_Impl classes are loaded reflectively by Room.getGeneratedImplementation,
# and entity/DAO members are read by name when building the schema and cursors.
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Hilt / Dagger
# Components, modules and generated *_HiltComponents / *_Factory classes are wired
# together reflectively at startup, and the injected member classes must keep their
# annotated fields and methods.
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class **_HiltComponents$* { *; }
-keep class **_HiltModules$* { *; }
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class * {
    @javax.inject.Inject <fields>;
    @javax.inject.Inject <methods>;
}
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-dontwarn dagger.hilt.**

# ---------------------------------------------------------------------------
# Kotlin coroutines
# The debug agent, the service-loader based main dispatcher and the atomic field
# updaters used by the internal machinery are all resolved reflectively.
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.atomicfu.**
# Kotlin metadata and intrinsics used by reflection-based libraries.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.Unit
