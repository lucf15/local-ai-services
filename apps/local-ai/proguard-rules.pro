# LiteRT creates callback objects and uses JNI. Preserve its Java/JNI boundary.
-keep class com.google.ai.edge.litertlm.** { *; }
