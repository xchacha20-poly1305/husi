# Names stay. The obfuscation pass still has to run: keepattributes is a no-op
# under -dontobfuscate, and optimize cannot rewrite Kotlin/Compose inline debug
# tables (Composer.cache → `$this$cache$iv`). HotSpot then rejects the class;
# Android is fine because R8 drops those tables:
#   ClassFormatError: Duplicated LocalVariableTable attribute entry for
#   '$this$cache$iv' in class file .../style/VariantTokensKt
# Do not keep LocalVariableTable / LocalVariableTypeTable.
-keep,allowshrinking,allowoptimization class ** { *; }
-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,*Annotation*,EnclosingMethod

-keep class fr.husi.** { *; }
-keep class go.** { *; }

-keep public class org.ini4j.spi.** { <init>(); }

-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** { native <methods>; }

-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }

# Mordant loads its TerminalInterfaceProvider through ServiceLoader, so the
# shrinker drops every implementation and Clikt dies formatting its own help:
#   ServiceConfigurationError: Provider TerminalInterfaceProviderJna not found
-keep class com.github.ajalt.clikt.** { *; }
-keep class com.github.ajalt.mordant.** { *; }

-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.beans.Transient
-dontwarn java.beans.VetoableChangeListener
-dontwarn java.beans.VetoableChangeSupport
-dontwarn java.beans.PropertyVetoException
-dontwarn kotlinx.parcelize.Parcelize
-dontwarn com.oracle.svm.core.annotate.**
-dontwarn com.github.ajalt.mordant.terminal.terminalinterface.ffm.**
-dontwarn com.github.ajalt.mordant.terminal.terminalinterface.nativeimage.**

# ProGuard's type generalization/specialization passes rewrite a call site to a
# supertype without rewriting the callee's descriptor, and the JVM verifier
# rejects the result:
#   VerifyError: Bad type on operand stack, in androidx.navigation3.ui.NavDisplay
#   Type 'androidx/navigationevent/NavigationEventInfo' is not assignable to
#   'androidx/navigation3/scene/SceneInfo'
# Every other optimization pass stays on.
-optimizations !method/specialization/*,!method/generalization/*,!field/specialization/*,!field/generalization/*

# Nucleus talks to the Linux desktop (tray, notifications, dark mode) over DBus,
# and dbus-java finds its transport through ServiceLoader, so the shrinker sees
# no caller and deletes org.freedesktop.dbus.transport.** wholesale:
#   ServiceConfigurationError: Provider
#   org.freedesktop.dbus.transport.jre.NativeTransportProvider not found
-keep class * implements org.freedesktop.dbus.spi.transport.ITransportProvider { *; }
# Remote objects are java.lang.reflect.Proxy instances, so every method on a
# DBus interface is only ever reached by name.
-keep interface * extends org.freedesktop.dbus.interfaces.DBusInterface { *; }
