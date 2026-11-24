# Control de Stock Textil 🧵📱

Aplicación móvil nativa en Android diseñada para la gestión integral y en tiempo real del inventario en almacenes y talleres de **Confección, Moda y Diseño Textil**.

Desarrollada con **Java, Material Design 3, arquitectura MVVM, Firebase (Cloud Firestore y Authentication) y Cloudinary**.

---

## 🚀 Características Principales

### 1. Catálogo e Inventario en Tiempo Real
* **Consumo continuo y discreto:** Gestión de telas y entretelas en metros (con precisión decimal), hilos por bobinas y fornituras/accesorios por unidades.
* **Alertas visuales de stock:** Indicadores automáticos de agotado (`❌`) o stock bajo mínimos (`⚠️`).
* **Buscador y filtros reactivos:** Filtrado en tiempo real por categoría (`HILOS`, `TEJIDO`, `ENTRETELA`, `BOTONES`, `CREMALLERAS`, etc.) y chip dedicado a artículos bajo mínimos de seguridad.
* **Fotografía de tejidos y productos:** Captura desde cámara o galería con compresión local automática (~120 KB) y subida directa a Cloudinary CDN sin coste de servidor intermedio.

### 2. Concurrencia y Transacciones Atómicas
* **Garantía ACID en Firestore:** Todas las modificaciones de stock se realizan mediante transacciones atómicas (`db.runTransaction(...)`) para prevenir condiciones de carrera cuando múltiples usuarias registran entradas o salidas simultáneamente.
* **Prevención de stock negativo:** Validación atómica en servidor antes de autorizar cualquier salida o préstamo de material.

### 3. Escáner de Códigos QR y Generador de Etiquetas
* **Escaneo ultrarrápido:** Reconocimiento con **Google ML Kit Barcode Scanning** y **CameraX** que abre directamente la ficha del artículo en 1 segundo.
* **Etiqueta QR individual:** Visualización e impresión de etiqueta con referencia, nombre, ubicación física y código QR.
* **Hojas de etiquetas para estanterías (PDF):** Generador masivo de hojas tamaño A4 en PDF listas para imprimir en papel autoadhesivo y pegar en baldas o piezas de tela.

### 4. Historial Inmutable de Movimientos
* **Trazabilidad completa:** Registro detallado de cada acción: `ENTRADA`, `SALIDA_CONSUMO`, `PRESTAMO`, `DEVOLUCION` y `AJUSTE`.
* **Inmutabilidad estricta:** Protegido por reglas de seguridad en Firestore (no permite edición ni borrado para garantizar una auditoría inalterable).

### 5. Carga y Descarga en Excel y CSV
* **Exportación (.xlsx y CSV):** Descarga del inventario completo al día para compartir por email, WhatsApp o almacenamiento en la nube.
* **Importación masiva (.xlsx):** Sincronización del catálogo mediante hojas de cálculo Excel utilizando la biblioteca FastExcel (sin dependencias lentas de AWT).
* **Protección contra inyección CSV:** Sanitización de celdas para mitigar ataques de fórmulas maliciosas (CWE-1236).

### 6. Roles y Gestión de Usuarias
* **Doble perfil:** `ADMIN` (Jefatura de almacén/taller) y `PROFESORA` (Docentes y usuarias autorizadas).
* **Autoregistro desde Login:** Las usuarias pueden crear su cuenta directamente (asignando siempre el rol base `PROFESORA`).
* **Desactivación lógica:** Nunca se eliminan usuarias físicamente de la base de datos para preservar la autoría histórica de todos los movimientos.

---

## 🔒 Auditoría de Seguridad y Buenas Prácticas

1. **Reglas de Seguridad de Cloud Firestore:**
   - Autenticación obligatoria para lectura y operaciones.
   - Creación y borrado de artículos reservado exclusivamente a rol `ADMIN`.
   - Historial de movimientos estrictamente inmutable (`allow update, delete: if false`).
   - Mitigación de escalada de privilegios en el autoregistro (imposibilita asignarse rol `ADMIN` a sí misma).
2. **Seguridad en Android:**
   - Componentes internos no exportados (`android:exported="false"`).
   - Uso de `FileProvider` con permisos temporales de lectura para compartir PDFs e imágenes.
   - Cero credenciales o claves privadas hardcodeadas en el código fuente.
3. **ProGuard / R8:**
   - Reglas configuradas en `proguard-rules.pro` para mantener intactos los modelos de Firestore (`Articulo`, `Ubicacion`, `Usuario`, `Movimiento`).

---

## ⚙️ Configuración del Entorno y Archivos No Incluidos

Por motivos de seguridad, privacidad y buenas prácticas de desarrollo, ciertos archivos con credenciales, claves de acceso o datos privados han sido excluidos del control de versiones mediante `.gitignore`. 

Para compilar, ejecutar o desplegar el proyecto en un entorno propio, es necesario configurar los siguientes elementos:

### 1. Configuración de Firebase (`google-services.json`)
* **Propósito:** Conecta la aplicación con los servicios de autenticación y la base de datos Cloud Firestore de Google Firebase.
* **Cómo obtenerlo:**
  1. Ve a la [Consola de Firebase](https://console.firebase.google.com/) y crea un proyecto.
  2. Registra una aplicación Android con el Application ID: `com.ifpleonardo.controlstocktextil`.
  3. Descarga el archivo `google-services.json`.
* **Ubicación requerida:**
  - `app/app/src/dev/google-services.json` (para la variante de desarrollo)
  - `app/app/src/prod/google-services.json` (para la variante de producción)

### 2. Reglas de Seguridad de Cloud Firestore (`firestore.rules`)
* **Propósito:** Proteger la base de datos contra accesos no autorizados, escalada de privilegios y borrado de auditorías.
* **Cómo aplicarlas:** En la consola de Firebase, dirígete a **Firestore Database > Reglas**, pega las siguientes reglas y haz clic en **Publicar**:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function isSignedIn() {
      return request.auth != null;
    }
    function isAdmin() {
      return isSignedIn() &&
        exists(/databases/$(database)/documents/usuarios/$(request.auth.uid)) &&
        get(/databases/$(database)/documents/usuarios/$(request.auth.uid)).data.rol == 'ADMIN';
    }

    // Colección de Usuarios:
    // - Lectura para cualquier usuaria autenticada.
    // - Autoregistro restringido a rol 'PROFESORA'.
    // - Solo ADMIN puede modificar roles o desactivar usuarias.
    match /usuarios/{userId} {
      allow read: if isSignedIn();
      allow create: if isSignedIn() && (
        (request.auth.uid == userId && request.resource.data.rol == 'PROFESORA') ||
        isAdmin()
      );
      allow update: if (isAdmin()) || 
                       (isSignedIn() && request.auth.uid == userId && 
                        request.resource.data.rol == resource.data.rol && 
                        request.resource.data.activo == resource.data.activo);
      allow delete: if isAdmin();
    }

    // Colección de Artículos de Inventario:
    // - Creación y borrado reservados a ADMIN.
    // - Modificación de stock mediante transacciones permitida a usuarias autenticadas.
    match /articulos/{articuloId} {
      allow read: if isSignedIn();
      allow create, delete: if isAdmin();
      allow update: if isSignedIn();
    }

    // Historial de Movimientos:
    // - Registro inmutable: no permite edición ni borrado a ningún usuario.
    match /movimientos/{movimientoId} {
      allow read, create: if isSignedIn();
      allow update, delete: if false;
    }
  }
}
```

### 3. Credenciales de Cuenta de Servicio (`serviceAccountKey.json`) - *Opcional*
* **Propósito:** Necesario únicamente si se desean ejecutar los scripts auxiliares de Python (`scripts/create_admin_user.py` o `scripts/import_excel.py`) para inicializar datos desde un ordenador con Firebase Admin SDK.
* **Cómo obtenerlo:** En la consola de Firebase, entra a **Configuración del proyecto > Cuentas de servicio > Generar nueva clave privada**.
* **Ubicación:** Guardar el archivo JSON en `scripts/serviceAccountKey.json`.

### 4. Datos de Catálogo e Inventario Inicial (`.xlsx`) - *Opcional*
* **Propósito:** Cargar inicialmente un volumen amplio de existencias.
* **Cómo utilizarlo:** Puede utilizarse cualquier archivo `.xlsx` con columnas como Referencia, Nombre, Categoría, Ubicación, Stock actual, Stock mínimo, etc., importándolo directamente desde la opción de Excel en la app (interfaz gráfica) o mediante el script `scripts/import_excel.py`.

### 5. Configuración de Cloudinary (CDN de Imágenes)
* La aplicación incluye un preset *Unsigned* en `CloudinaryUploader.java` para subir imágenes sin exponer secretos privados. Si se desea usar una cuenta propia de Cloudinary, basta con modificar las constantes `CLOUD_NAME` y `UPLOAD_PRESET` en `com.ifpleonardo.controlstocktextil.util.CloudinaryUploader`.

---

## 🛠️ Cómo Generar el APK

En Android Studio:

### Opción A: Generar APK de prueba (Recomendada)
1. Ve al menú superior: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
2. Al finalizar, aparecerá una notificación en la esquina inferior derecha con el enlace **locate** para abrir la carpeta con el archivo `.apk` listo para instalar en cualquier móvil.

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

---

## 📄 Licencia

Copyright (c) 2026 Luis Bermejo. Todos los derechos reservados.  
Consulta el archivo [LICENSE.md](LICENSE.md) para más detalles.
