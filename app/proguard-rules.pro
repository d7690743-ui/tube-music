# TubeMusic — reglas ProGuard/R8 (hoy minifyEnabled=false, pero el build
# release exige que este archivo exista).
# Si se activa minify, estas reglas evitan romper el extractor y el reproductor.

# Modelos que viajan por Intents (Serializable) y estado del reproductor.
-keep class com.nexos.tubemusic.Track { *; }
-keep class com.nexos.tubemusic.PlayerBus { *; }
-keep class com.nexos.tubemusic.DownloadStore { *; }

# NewPipeExtractor usa reflexión interna.
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# FFmpegKit (paquete arthenica aunque el artefacto sea el fork maintained).
-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**

# Desugaring de librerías NIO.
-dontwarn java.lang.invoke.**
