# ClipForge Studio

Editor Android inicial de video con interfaz oscura y acento lima. Esta primera versión implementa selección de video, reproducción, ajuste de puntos de entrada/salida y exportación del recorte con Media3 Transformer.

## Estado

Es una base funcional en desarrollo, no un reemplazo completo de CapCut ni una app AAA. Aún no incluye edición multipista, títulos, transiciones, filtros, mezcla de audio, keyframes, proyectos persistentes ni exportación avanzada.

## APK

Cada push a `main` (y ejecución manual) compila un APK debug con GitHub Actions. Descárgalo en la pestaña **Actions → Build Android APK → Artifacts**.

## Compilar localmente

Con JDK 17 y Gradle 8.10.2: `gradle assembleDebug`. APK: `app/build/outputs/apk/debug/app-debug.apk`.
