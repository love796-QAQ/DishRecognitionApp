#########################################
# ProGuard rules for DishRecognitionApp #
#########################################

# --- ONNX Runtime (AI 推理库) ---
-keep class ai.onnxruntime.** { *; }

# --- OpenCV (图像处理库) ---
-keep class org.opencv.** { *; }

# --- UVCCamera (USB 摄像头 SDK) ---
-keep class com.serenegiant.** { *; }

# --- JSON (模板存储用) ---
-keepclassmembers class * {
    @org.json.* <fields>;
}
