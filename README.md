# Control de Stock Textil FP (IES Leonardo da Vinci) 🧵📱

Aplicación móvil nativa en Android diseñada para la gestión integral y en tiempo real del inventario del almacén de **Confección y Moda (Formación Profesional)**.

Desarrollada con **Java, Material Design 3, arquitectura MVVM, Firebase (Cloud Firestore y Authentication) y Cloudinary**.

---

## 🚀 Características Principales

### 1. Catálogo e Inventario en Tiempo Real
* **Consumo continuo y discreto:** Gestión de telas y entretelas en metros (con decimales), hilos por bobinas y fornituras por unidades.
* **Alertas visuales:** Indicadores automáticos de agotado (`❌`) o stock bajo mínimos (`⚠️`).
* **Buscador en tiempo real y filtros:** Filtrado por categoría (`HILOS`, `TEJIDO`, `ENTRETELA`, `BOTONES`, `CREMALLERAS`, etc.) y chip dedicado a artículos bajo mínimos.
* **Fotografía de tejidos y productos:** Captura desde cámara o galería con compresión local automática (~120 KB) alojada en Cloudinary CDN sin coste.

### 2. Concurrencia y Transacciones Atómicas
* **Garantía ACID en Firestore:** Todas las modificaciones de stock se realizan mediante `db.runTransaction(...)` para evitar condiciones de carrera cuando varias profesoras registran material simultáneamente.
* **Prevención de stock negativo:** Validación atómica de existencias previas a cualquier salida o préstamo.

### 3. Escáner de Códigos QR y Generador de Etiquetas
* **Escaneo ultrarrápido:** Reconocimiento con **Google ML Kit Barcode Scanning** y **CameraX** que abre directamente la ficha del artículo en 1 segundo.
* **Etiqueta QR individual:** Visualización e impresión de etiqueta con referencia, nombre, ubicación y código QR.
* **Hojas de etiquetas para estanterías (PDF):** Generador masivo de hojas tamaño A4 en PDF listas para imprimir en papel autoadhesivo y pegar en baldas o piezas de tela.

### 4. Historial Inmutable de Movimientos
* **Auditoría completa:** Registro de cada acción: `ENTRADA`, `SALIDA_CONSUMO`, `PRESTAMO`, `DEVOLUCION` y `AJUSTE`.
* **Inmutabilidad estricta:** Protegido por reglas de seguridad en Firestore (no permite edición ni borrado para garantizar trazabilidad).

### 5. Carga y Descarga en Excel y CSV
* **Exportación (.xlsx y CSV):** Descarga del inventario completo al día para compartir por email, WhatsApp o Drive.
* **Importación masiva (.xlsx):** Sincronización del catálogo mediante hojas de cálculo Excel utilizando la biblioteca FastExcel (sin dependencias de AWT).
* **Protección contra inyección CSV:** Sanitización de celdas para mitigar ataques de fórmulas maliciosas (CWE-1236).

### 6. Roles y Gestión de Profesoras
* **Doble perfil:** `ADMIN` (Jefa de departamento) y `PROFESORA`.
* **Autoregistro desde Login:** Las profesoras pueden crear su cuenta directamente (asignando siempre rol `PROFESORA`).
* **Desactivación lógica:** Nunca se eliminan usuarias físicamente para preservar la autoría histórica de los movimientos.

---

## 🔒 Auditoría de Seguridad y Buenas Prácticas

1. **Reglas de Seguridad de Cloud Firestore:**
   - Autenticación obligatoria para lectura.
   - Creación y borrado de artículos restringido a administradoras.
   - Historial de movimientos estrictamente inmutable (`allow update, delete: if false`).
   - Mitigación de escalada de privilegios en autoregistro (imposibilita asignarse rol `ADMIN` a sí misma).
2. **Seguridad en Android:**
   - Componentes internos no exportados (`android:exported="false"`).
   - `FileProvider` con permisos temporales de lectura para compartir PDFs e imágenes.
   - Sin claves privadas ni secretos duros en el código fuente (credenciales excluidas en `.gitignore`).
3. **ProGuard / R8:**
   - Reglas configuradas en `proguard-rules.pro` para evitar la ofuscación de los modelos de Firestore (`Articulo`, `Ubicacion`, `Usuario`, `Movimiento`).

---

## 🛠️ Cómo Generar el APK

En Android Studio:

### Opción A: Generar APK de prueba (Fácil)
1. Ve al menú superior: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Al finalizar, aparecerá una notificación abajo a la derecha con el botón **locate** para abrir la carpeta donde está el archivo `.apk` listo para instalar en cualquier móvil.

### Opción B: Generar APK mediante Terminal Gradle
* **Entorno Desarrollo:**
  ```bash
  ./gradlew assembleDevDebug
  ```
* **Entorno Producción:**
  ```bash
  ./gradlew assembleProdRelease
  ```
El archivo generado se ubicará en:
`app/app/build/outputs/apk/<flavor>/<buildType>/app-<flavor>-<buildType>.apk`