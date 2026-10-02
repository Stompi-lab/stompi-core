# WorkManager instantiates this worker by name and constructor.
-keep,allowoptimization class tech.stompi.wallet.UpdatesWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
# BIP39 loads english.txt relative to this class; retain its package/name.
-keepnames class org.bitcoinj.crypto.MnemonicCode
# Protobuf generated message fields are accessed through schema reflection.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
# Preserve any future Android Javascript bridge annotations.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Compact DEX package names; explicitly required with AGP 9.0.
-repackageclasses
