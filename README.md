# TubeMusic

Descarga audio de YouTube en el móvil. App Android nativa en Kotlin,
sin dependencias salvo NewPipeExtractor.

## Uso

1. Busca canción o artista (resultados con carátula y duración).
2. Toca un resultado para reproducirlo en streaming.
3. Con ⬇ lo descargas a `Music/TubeMusic/`.
4. La pestaña Biblioteca lista tus descargas para oírlas sin red.

Reproductor estilo Spotify: mini player, pantalla completa con seek,
cola siguiente/anterior, y servicio en segundo plano con notificación
(funciona con la pantalla apagada).

## Cómo funciona

- Extracción con [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) `v0.27.1`
  (vía JitPack, sin API oficial de YouTube).
- Descarga directa del stream de audio con progreso.
- Guardado vía MediaStore (Android 10+ sin permisos extra).

Cada push a `main` compila el APK debug en GitHub Actions
(artefacto `tubemusic-debug`).

## Límites actuales

- Descargas en primer plano (si cierras la app se cancelan).
- Sin listas de reproducción guardadas ni cola de descargas.
- Si YouTube cambia algo y deja de funcionar, hay que actualizar la
  versión de NewPipeExtractor y recompilar.

## Licencia

GPL-3.0-only (ver `LICENSE`). Exigido por NewPipeExtractor.
Descargar de YouTube viola sus términos de servicio y la app no puede
publicarse en Play Store. Uso personal.
