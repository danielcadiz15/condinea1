# Forense Recovery Android

Aplicación Android nativa (Kotlin + Jetpack Compose) orientada a **recuperación forense local** para uso propio o autorizado.

## Aviso legal y ético

- Solo debe usarse en dispositivos propios o con autorización explícita.
- No oculta actividad, no espía, no exfiltra datos.
- Todo el procesamiento ocurre localmente en el dispositivo.
- **No garantiza recuperación total** de archivos eliminados, porque Android impone límites de acceso (scoped storage, cifrado, TRIM, permisos, sandbox de apps).

## Stack técnico

- Kotlin
- Jetpack Compose (UI)
- MVVM limpio
- Room (SQLite)
- WorkManager
- Coroutines
- MediaStore
- Storage Access Framework (preparado para ampliar)
- Exportación: CSV / JSON / HTML / ZIP + log técnico
- Min SDK 26
- Target SDK 35

## Estructura de carpetas

`app/src/main/java/com/forenserecovery/android/`

- `data/` persistencia (Room, mappers, repositorio)
- `domain/` modelos, contratos y casos de uso
- `scanner/` escaneo, detección de firmas y carving básico
- `recovery/` coordinación de escaneo y Worker
- `ui/` Compose, pantallas y ViewModel
- `export/` exportadores de informe/artefactos
- `permissions/` estrategia de permisos por modo
- `utils/` utilidades (hash, etc.)

## Modos de acceso

1. **Básico**
   - Usa permisos de medios (`READ_MEDIA_*` o `READ_EXTERNAL_STORAGE` en APIs antiguas).
   - Escaneo por MediaStore y rutas compartidas accesibles.

2. **Avanzado**
   - Requiere además **All Files Access** (`MANAGE_EXTERNAL_STORAGE`) cuando aplique (Android 11+).
   - Amplía rutas de escaneo compartido.

3. **Forense (opcional)**
   - Flujo preparado para integraciones futuras con Shizuku/ADB.
   - Actualmente mantiene procesamiento local y mismo motor base.

4. **Root (futuro)**
   - No implementado en esta base inicial.
   - Arquitectura preparada para agregar backend de acceso root sin romper la capa UI/domain.

## Funcionalidades implementadas

- Escaneo por MediaStore:
  - imágenes, videos, audios.
- Escaneo de rutas compartidas accesibles:
  - `DCIM`, `Pictures`, `Movies`, `Music`, `Download`,
  - `WhatsApp/Media`, `Telegram`, `Android/media`,
  - `.thumbnails` y cachés accesibles.
- Detector por firma mágica:
  - JPG, PNG, WEBP/RIFF, MP4/ftyp, OGG/OPUS, MP3, AMR, PDF.
- Carving básico:
  - escaneo por streams de archivos grandes accesibles,
  - búsqueda de firmas internas y extracción de fragmentos.
- Clasificación de hallazgos:
  - `COMPLETE`, `PARTIAL`, `THUMBNAIL`, `CORRUPT`, `DUPLICATE`.
- Dedupe por hash SHA-256.
- UI:
  - aviso legal/ético,
  - selector de modo,
  - iniciar / pausar / reanudar / cancelar escaneo,
  - progreso en tiempo real,
  - filtros y vista grid/lista,
  - detalle técnico de hallazgos.
- Exportación:
  - CSV, JSON, HTML, ZIP de recuperados y log técnico.

## Esquema Room (RecoveryItem)

Campos principales:

- `id`
- `type`
- `mimeType`
- `originalPath`
- `recoveredPath`
- `sizeBytes`
- `sha256`
- `width`
- `height`
- `duration`
- `createdAt`
- `modifiedAt`
- `scanSource`
- `confidence`
- `status`
- `notes`

## Permisos y límites reales

- El modo avanzado puede requerir apertura manual del ajuste de All Files Access.
- Directorios privados de otras apps (`/Android/data/...`) pueden seguir restringidos según versión/políticas OEM.
- Archivos realmente borrados y sobreescritos no son recuperables en muchos casos.
- El carving actual es básico y conservador (fragmentos acotados), para minimizar riesgo de consumo excesivo.

## Build

Desde la carpeta raíz de este proyecto:

```bash
./gradlew :app:assembleDebug
```

Si el entorno no tiene Android SDK configurado, define `ANDROID_HOME` o crea `local.properties` con:

```properties
sdk.dir=/ruta/a/android-sdk
```

## Próximos pasos sugeridos

- Integración real con Shizuku/ADB en modo forense.
- SAF avanzado para seleccionar volúmenes/directorios por árbol.
- Reproductor multimedia embebido en la pantalla de detalle.
- Tests instrumentados del motor de escaneo y exportación.
