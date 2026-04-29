# Repara Fotos AI

Aplicación Android nativa (Kotlin + Jetpack Compose) para **diagnosticar y mejorar fotos dañadas, oscuras o fuera de foco leve** directamente en el dispositivo.

## Estado actual (MVP)

Versión base funcional con:

- Selección de imagen desde galería.
- Diagnóstico automático local:
  - brillo/exposición,
  - nitidez aproximada,
  - score de calidad.
- Reparación básica local:
  - ajuste de contraste y brillo,
  - re-encode de salida para estabilizar artefactos.
- Vista **Antes / Después** lado a lado.
- Mensajes de error y flujo mínimo usable para pruebas reales.

## Aviso de límites

- No promete restauración perfecta de imágenes severamente dañadas.
- En desenfoque fuerte o corrupción parcial grave, la mejora puede ser limitada.
- Todo el procesamiento ocurre localmente; no sube imágenes a Internet.

## Build local

Desde la raíz de este proyecto:

```bash
./gradlew :app:assembleDebug
```

Si tu entorno no tiene Android SDK configurado:

```properties
sdk.dir=/ruta/a/android-sdk
```

## CI / APK automático

Este repositorio usa el workflow:

- `.github/workflows/repara-fotos-apk.yml`

Qué hace:

1. Compila `app-debug.apk` en cada push.
2. Publica artifact descargable.
3. Actualiza prerelease rolling:
   - tag: `repara-fotos-ai-latest-debug`
   - nombre: `Repara Fotos AI - Latest Debug`

## Próximos pasos sugeridos

- Pipeline de reparación por lotes (WorkManager).
- Filtros avanzados (denoise, sharpen adaptativo, deblocking).
- Exportación de reportes por imagen reparada.
- Modo Premium (sin anuncios + procesamiento por lotes + calidad alta).
