/**
 * Script para asignar custom claims de administrador a usuarios
 *
 * INSTRUCCIONES:
 * 1. Ve a Firebase Console: https://console.firebase.google.com
 * 2. Selecciona tu proyecto "LaGotita"
 * 3. Ve a: Configuración del proyecto (⚙️) > Cuentas de servicio
 * 4. Haz clic en "Generar nueva clave privada"
 * 5. Descarga el archivo JSON y guárdalo como "serviceAccountKey.json" en la raíz del proyecto
 * 6. Ejecuta: node set-admin.js
 */

const admin = require('firebase-admin');
const fs = require('fs');
const path = require('path');

// Intentar cargar la Service Account Key
const serviceAccountPath = path.join(__dirname, 'serviceAccountKey.json');

if (!fs.existsSync(serviceAccountPath)) {
  console.error('❌ ERROR: No se encontró el archivo serviceAccountKey.json');
  console.error('\n📝 PASOS PARA OBTENER LA SERVICE ACCOUNT KEY:\n');
  console.error('1. Ve a: https://console.firebase.google.com');
  console.error('2. Selecciona tu proyecto');
  console.error('3. Ve a: Configuración del proyecto (⚙️) > Cuentas de servicio');
  console.error('4. Haz clic en "Generar nueva clave privada"');
  console.error('5. Guarda el archivo descargado como "serviceAccountKey.json" en la raíz del proyecto');
  console.error('6. Vuelve a ejecutar este script\n');
  process.exit(1);
}

const serviceAccount = require('./serviceAccountKey.json');

// Inicializar Firebase Admin
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

// Lista de emails de administradores (según las capturas)
const adminEmails = [
  'asistentepresupuesto2@lagotitaec.com',
  'jgironm20@miumg.edu.gt',
  'joselitogiron.a@gmail.com'
];

async function setAdminClaims() {
  console.log('🔄 Iniciando asignación de claims de administrador...\n');
  const db = admin.firestore();

  for (const email of adminEmails) {
    try {
      // Buscar usuario por email en Firebase Authentication
      const user = await admin.auth().getUserByEmail(email);

      console.log(`📧 Procesando: ${email}`);
      console.log(`   UID: ${user.uid}`);

      // Establecer custom claim de admin
      await admin.auth().setCustomUserClaims(user.uid, { admin: true });
      console.log(`   ✅ Custom claim 'admin: true' asignado`);

      // Actualizar documento en Firestore
      await db.collection('users').doc(user.uid).set({
        uid: user.uid,
        email: email,
        name: user.displayName || email.split('@')[0],
        role: 'ADMIN',
        active: true
      }, { merge: true });

      console.log(`   ✅ Documento de Firestore actualizado con role: 'ADMIN'`);
      console.log('');

    } catch (error) {
      console.error(`   ❌ Error procesando ${email}:`, error.message);
      console.log('');
    }
  }

  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('🎉 Proceso completado exitosamente');
  console.log('');
  console.log('⚠️  IMPORTANTE: Los usuarios deben:');
  console.log('   1. Cerrar sesión en la app');
  console.log('   2. Volver a iniciar sesión');
  console.log('   Para que los cambios tomen efecto');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  process.exit(0);
}

// Ejecutar
setAdminClaims().catch(error => {
  console.error('❌ Error fatal:', error);
  process.exit(1);
});
