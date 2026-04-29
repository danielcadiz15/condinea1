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
   - Detección local de capacidad forense (Shizuku instalado / ADB visible).
   - Mantiene procesamiento local; el puente forense sigue siendo opcional.

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
- Escaneo adicional por **Storage Access Framework (SAF)**:
  - selección de árbol/carpeta por el usuario,
  - persistencia del permiso URI,
  - escaneo de contenido accesible dentro del árbol seleccionado.
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
  - selector de perfil de escaneo (rápido / balanceado / profundo),
  - estado de capacidad forense detectada (Shizuku/ADB),
  - botón de ayuda con explicación para habilitar Shizuku,
  - selector de carpeta SAF para modo avanzado/forense,
  - iniciar / pausar / reanudar / cancelar escaneo,
  - barra de progreso en tiempo real + etapa activa + zona/ruta de escaneo,
  - filtro por carpeta origen,
  - galería/lista en vivo con selección múltiple,
  - restaurar seleccionadas o todas las visibles,
  - selector de carpeta destino antes de restaurar,
  - filtros y vista grid/lista,
  - detalle técnico de hallazgos + apertura externa de imagen/audio/video.
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

## Probar desde Chrome en Android (sin Android Studio)

El repositorio incluye un workflow de GitHub Actions:

- `.github/workflows/android-apk.yml`

Qué hace:

1. Compila `app-debug.apk` en cada push.
2. Sube el APK como artifact del workflow.
3. Actualiza una prerelease fija con tag `android-latest-debug`.

Cómo probar desde tu teléfono:

1. Entra al repositorio en GitHub desde Chrome.
2. Ve a **Releases** y abre `Forense Recovery Android - Latest Debug`.
3. Descarga `app-debug.apk`.
4. Instálalo (habilitando “instalar apps desconocidas” para Chrome/Archivos si hace falta).

También puedes descargar el artifact desde la pestaña **Actions** en la ejecución más reciente.

## Configuración de monetización (AdMob + Google Play Billing)

La app ya está preparada para usar IDs reales sin hardcodearlos en el código.

### 1) Crear archivo local de configuración

En la raíz del proyecto crea un archivo llamado:

`monetization.properties`

Puedes partir de `monetization.properties.example`:

```properties
ADMOB_APP_ID=ca-app-pub-xxxxxxxxxxxxxxxx~yyyyyyyyyy
ADMOB_BANNER_AD_UNIT_ID=ca-app-pub-xxxxxxxxxxxxxxxx/zzzzzzzzzz
ADMOB_INTERSTITIAL_AD_UNIT_ID=ca-app-pub-xxxxxxxxxxxxxxxx/aaaaaaaaaa
BILLING_PREMIUM_SUB_MONTHLY_ID=forense_premium_monthly
BILLING_PREMIUM_SUB_YEARLY_ID=forense_premium_yearly
BILLING_PREMIUM_LIFETIME_ID=forense_premium_lifetime
```

> `monetization.properties` está ignorado por git para no exponer credenciales/IDs reales.

### 2) AdMob

En Google AdMob:

1. Crea la app.
2. Crea una unidad **Banner**.
3. Crea una unidad **Interstitial**.
4. Copia esos IDs al `monetization.properties`.

La app toma automáticamente:

- `ADMOB_APP_ID` para `AndroidManifest` (`com.google.android.gms.ads.APPLICATION_ID`)
- `ADMOB_BANNER_AD_UNIT_ID` para el banner
- `ADMOB_INTERSTITIAL_AD_UNIT_ID` para interstitial

Si no defines los valores, el proyecto usa IDs de prueba de Google.

### 3) Google Play Billing

En Play Console:

1. Crea productos con los IDs:
   - `forense_premium_monthly` (suscripción)
   - `forense_premium_yearly` (suscripción)
   - `forense_premium_lifetime` (in-app no consumible)
2. Si quieres cambiar esos IDs, actualízalos en `monetization.properties`.
3. Agrega cuentas de prueba de licencia.
4. Prueba compra, restauración y desbloqueo premium.

### 4) Recomendación de release

- Mantén build interno con IDs de prueba.
- Para publicar, usa `monetization.properties` con IDs reales y genera APK/AAB de release.
- Verifica en Android real:
  - banner visible solo en básico,
  - interstitial funcionando,
  - paywall correcto,
  - restaurar compras.

## Próximos pasos sugeridos

- Integración activa de comandos forenses vía Shizuku/ADB (ejecución real, no solo detección).
- Correlación automática más robusta entre miniaturas, originales y fragmentos carved.
- Reproductor multimedia embebido (actualmente se abre con app externa vía intent).
- Tests instrumentados del motor de escaneo y exportación.
