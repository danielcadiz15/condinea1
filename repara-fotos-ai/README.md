# Repara Fotos AI

Aplicación Android nativa (Kotlin + Jetpack Compose) para **diagnosticar y mejorar fotos dañadas, oscuras o fuera de foco leve** directamente en el dispositivo.

## Estado actual (MVP)

Versión funcional actual con:

- Selección de imagen desde galería.
- Diagnóstico automático local:
  - brillo/exposición,
  - nitidez aproximada y detección de foto movida,
  - score de calidad.
- Reparación local con herramientas:
  - auto reparación,
  - corrección de movimiento,
  - enfoque, escala de grises, sepia e inversión,
  - ajustes manuales (brillo, contraste, saturación, temperatura, nitidez).
- Presets visuales:
  - vívido, retrato, noche, documento y vintage.
- Vista **Antes / Después** lado a lado.
- Vista full-screen al tocar la imagen reparada.
- Historial: deshacer / rehacer / aplicar cambios.
- Procesamiento por lote con guardado automático (Premium).
- Monetización integrada (básico con anuncios + premium).
- Mensajes de error y flujo mínimo usable para pruebas reales.

## Aviso de límites

- No promete restauración perfecta de imágenes severamente dañadas.
- En desenfoque fuerte o corrupción parcial grave, la mejora puede ser limitada.
- Todo el procesamiento ocurre localmente; no sube imágenes a Internet.

## Guía de uso (rápida)

### 1) Abrir y preparar
1. Toca **Abrir imagen** y selecciona una foto.
2. Revisa el bloque de **Diagnóstico inteligente** (estado + calidad estimada).
3. Si la foto está movida, activa:
   - herramienta **Movimiento**, o
   - **Auto corregir foto movida** en ajustes.

### 2) Editar
1. En **Herramientas**, elige el modo de reparación.
2. En **Presets visuales**, prueba filtros rápidos.
3. Ajusta sliders de brillo, contraste, nitidez, saturación y temperatura.
4. Usa **Aplicar cambios** para guardar el estado en historial.
5. Usa **Deshacer / Rehacer** para comparar resultados.

### 3) Ver resultado y guardar
1. Compara **Original** vs **Reparada**.
2. Toca la imagen **Reparada** para verla en **pantalla completa**.
3. Toca **Guardar** para exportar en:
   - `Android/data/com.reparafotos.ai/files/Pictures/ReparaFotosAI`

### 4) Procesamiento por lote (Premium)
1. Toca **Procesar lote**.
2. Selecciona varias imágenes.
3. La app procesa y guarda automáticamente cada salida.
4. Verás progreso y resumen final.

## Guía de plan básico y premium

- **Básico**:
  - 3 aperturas gratis de foto para edición.
  - Desde la 4ta apertura: ver anuncio o pasarte a Premium.
  - Muestra banner y algunos interstitials.
- **Premium**:
  - Sin anuncios.
  - Acceso completo a funciones avanzadas (incluye lote y mejoras premium).
  - Restauración de compra disponible desde el bloque de plan.

## Texto sugerido para “Guía de uso” dentro de la app

Puedes usar este resumen en una futura pantalla “Ayuda”:

1. Abre una foto y revisa el diagnóstico.
2. Aplica herramientas o presets.
3. Ajusta sliders para afinar el resultado.
4. Usa deshacer/rehacer y aplica cambios cuando te guste.
5. Toca la imagen reparada para verla completa.
6. Guarda o procesa por lote (Premium).

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
