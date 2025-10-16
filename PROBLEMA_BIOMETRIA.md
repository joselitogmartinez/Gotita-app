# Problema de Verificación con Biometría

## 🔴 EL PROBLEMA IDENTIFICADO

Cuando el usuario inicia sesión con **biometría**, la aplicación **NO puede acceder a Firestore** porque las reglas de seguridad requieren una sesión activa de Firebase Authentication.

### ¿Por qué ocurre esto?

1. **Flujo actual de autenticación biométrica:**
   - Usuario abre la app
   - Se muestra el prompt biométrico
   - Usuario se autentica con huella/rostro
   - La app intenta leer datos del usuario desde Firestore
   - **❌ Firestore RECHAZA la solicitud**

2. **Las reglas de Firestore requieren:**
   ```javascript
   function isSignedIn() { return request.auth != null; }
   ```
   
   Y todas las operaciones en la colección `users` requieren `isSignedIn()`:
   ```javascript
   match /users/{userId} {
     allow get: if isSignedIn() && (request.auth.uid == userId || isAdmin());
   }
   ```

3. **El problema:**
   - En el archivo `AuthViewModel.kt`, cuando hay éxito biométrico, la función `onBiometricAuthenticationSuccess()` intenta acceder a Firestore directamente
   - Pero como **NO hay sesión de Firebase** (`request.auth == null`), Firestore bloquea el acceso
   - El usuario ve el error: "Error al verificar usuario (biometría)"

## ✅ SOLUCIÓN

### Opción 1: Guardar credenciales cifradas (RECOMENDADA)

Modificar el flujo para que después de un login exitoso con credenciales:
1. Se guarden las credenciales de forma segura en SharedPreferences
2. Cuando el usuario use biometría, se recuperen esas credenciales
3. Se inicie sesión automáticamente en Firebase con esas credenciales
4. Luego se acceda a Firestore con la sesión activa

**Ventajas:**
- Compatible con las reglas de seguridad actuales
- No requiere backend adicional
- Cumple con los requisitos de seguridad de Firebase

**Desventajas:**
- Las contraseñas se almacenan localmente (aunque cifradas)
- Si el usuario cambia la contraseña en otro dispositivo, fallará

### Opción 2: Usar Android Keystore + Custom Tokens (MÁS SEGURA)

Implementar un backend que genere tokens personalizados de Firebase después de verificar la biometría.

**Ventajas:**
- Más seguro, no almacena contraseñas
- Compatible con cambios de contraseña

**Desventajas:**
- Requiere implementar un backend (Firebase Functions o servidor propio)
- Más complejo de implementar

### Opción 3: Modificar reglas de Firestore (❌ NO RECOMENDADA)

Permitir lectura de usuarios sin autenticación.

**NO SE RECOMIENDA** porque:
- Expone datos de usuarios
- Riesgo de seguridad grave
- Cualquiera podría leer información de todos los usuarios

## 📝 IMPLEMENTACIÓN DE LA SOLUCIÓN 1

La solución que implementaré modifica el `AuthViewModel.kt` para:

1. **Al hacer login con credenciales:** Guardar email y contraseña en SharedPreferences
2. **Al usar biometría:** Recuperar las credenciales y hacer login en Firebase
3. **Después del login:** Acceder a Firestore normalmente con la sesión activa

### Archivos a modificar:
- `AuthViewModel.kt` - Agregar funciones para guardar/recuperar credenciales
- Modificar `loginUser()` para guardar credenciales tras login exitoso
- Modificar `onBiometricAuthenticationSuccess()` para iniciar sesión en Firebase

### Consideraciones de seguridad:
- Las contraseñas se guardan en SharedPreferences (no es perfecto pero es funcional)
- Para mayor seguridad, se podría usar Android Keystore para cifrado
- Al cerrar sesión, se pueden limpiar las credenciales si se desea

