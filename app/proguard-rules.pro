# R8 for the release APK: removes unused code and optimizes it. Names are kept
# (no obfuscation), so stack traces stay readable; obfuscation is on hold (docs/status.md).
-dontobfuscate

# libVLC and ONNX Runtime reach their Java classes, fields and methods from native code (JNI),
# which R8 cannot see. Keep both libraries whole.
-keep class org.videolan.** { *; }
-keep class ai.onnxruntime.** { *; }
