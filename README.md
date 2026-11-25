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

### 6. Roles y Gestión de Usuarios (Principio de Mínimo Privilegio)
* **Tres niveles de acceso:**
  - `LECTOR`: Rol asignado por defecto en el autoregistro desde el Login. Permite únicamente consultar el catálogo, ver el historial y exportar/descargar Excel, CSV o etiquetas QR.
  - `PROFESOR/A`: Asignado por un administrador; habilita el registro de movimientos de stock y la edición de fotografías de artículos.
  - `ADMIN`: Control total sobre el catálogo, importación masiva de Excel y gestión de usuarios/roles.
* **Desactivación lógica:** Nunca se eliminan usuarios físicamente de la base de datos para preservar la autoría histórica de todos los movimientos, y las bajas bloquean el acceso en tiempo real.

---

## 🔒 Auditoría de Seguridad y Buenas Prácticas

1. **Reglas de Seguridad de Cloud Firestore:**
   - Validación obligatoria de cuenta activa (`activo == true`) en servidor para todas las operaciones.
   - Autoregistro restringido a rol `LECTOR` (imposibilita escalada de privilegios o modificación de stock por usuarios no aprobados).
   - Actualización de artículos por `PROFESOR/A` limitada únicamente a `stockActual` (>= 0) e `imagenUrl`.
   - Historial de movimientos estrictamente inmutable (`allow update, delete: if false`) con verificación de identidad (`usuarioId == request.auth.uid`).
2. **Seguridad en Android:**
   - `android:allowBackup="false"` para evitar copias no cifradas de sesión y caché local.
   - Componentes internos no exportados (`android:exported="false"`) y verificación de rol en actividades administrativas.
   - Uso de `FileProvider` con permisos temporales de lectura y almacenamiento público vía `MediaStore.Downloads`.
   - Limpieza automática de metadatos EXIF privados y archivos temporales en caché al comprimir y subir imágenes.
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

    function currentUserDoc() {
      return get(/databases/$(database)/documents/usuarios/$(request.auth.uid));
    }

    function isActiveUser() {
      return isSignedIn() &&
        exists(/databases/$(database)/documents/usuarios/$(request.auth.uid)) &&
        currentUserDoc().data.activo == true;
    }

    function isAdmin() {
      return isActiveUser() && currentUserDoc().data.rol == 'ADMIN';
    }

    function canModifyStock() {
      return isActiveUser() && (
        currentUserDoc().data.rol == 'ADMIN' ||
        currentUserDoc().data.rol == 'PROFESORA'
      );
    }

    match /usuarios/{userId} {
      allow read: if isSignedIn() && (request.auth.uid == userId || isAdmin());
      allow create: if isSignedIn() && (
        (request.auth.uid == userId &&
         request.resource.data.rol == 'LECTOR' &&
         request.resource.data.activo == true) ||
        isAdmin()
      );
      allow update: if isAdmin() ||
                       (isActiveUser() &&
                        request.auth.uid == userId &&
                        request.resource.data.diff(resource.data).affectedKeys().hasOnly(['nombre', 'fotoUrl']) &&
                        request.resource.data.rol == resource.data.rol &&
                        request.resource.data.activo == resource.data.activo);
      allow delete: if isAdmin();
    }

    match /articulos/{articuloId} {
      allow read: if isActiveUser();
      allow create, delete: if isAdmin();
      allow update: if isAdmin() ||
                       (canModifyStock() &&
                        request.resource.data.diff(resource.data).affectedKeys().hasOnly(['stockActual', 'imagenUrl']) &&
                        request.resource.data.stockActual >= 0);
    }

    match /movimientos/{movimientoId} {
      allow read: if isActiveUser();
      allow create: if canModifyStock() &&
                       request.resource.data.usuarioId == request.auth.uid &&
                       request.resource.data.stockResultante >= 0;
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
