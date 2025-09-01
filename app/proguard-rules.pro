# =========================================================
# Owned by @bharath_boy on telegram
# =========================================================

# This rule makes the obfuscation process more aggressive.
# It can sometimes cause issues, so test your app thoroughly.
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Keep all public classes that are Android entry points.
# This is crucial for the app to function.
-keep public class com.example.paymenttracker.MainActivity
-keep public class com.example.paymenttracker.SmsForwardingService
-keep public class com.example.paymenttracker.SmsReceiver

# Keep the Parcelable data model classes and their fields.
# Parcelable requires fields and constructors to be kept for inter-component communication.
-keep class com.example.paymenttracker.Message implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    <init>(android.os.Parcel);
    <fields>;
}

-keep class com.example.paymenttracker.PaymentDetails implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
    <init>(android.os.Parcel);
    <fields>;
}

# Keep the TelegramSender class and its methods to prevent it from being removed
# or renamed, as its functionality is critical.
-keep public class com.example.paymenttracker.TelegramSender {
    public <init>(...);
    public *** sendPaymentDetails(...);
}

# Keep the SmsParser class to prevent crashes due to reflection.
# Obfuscation can still be applied to its internal helper methods.
-keep public class com.example.paymenttracker.SmsParser {
    public <init>(...);
    public static *** parse(...);
}

# Keep all methods and fields used by reflection in SmsParser
-keep class com.example.paymenttracker.PaymentDetails {
    <fields>;
    <methods>;
}

# Keep the OkHttp library classes that are used.
# This prevents the network library from breaking after obfuscation.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }

# Keep all JSON classes to ensure JSON parsing works correctly.
-keep class org.json.** { *; }

# Preserve signatures and annotations, which can be useful for some libraries.
-keepattributes Signature
-keepattributes *Annotation*