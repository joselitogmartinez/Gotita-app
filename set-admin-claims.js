const admin = require('firebase-admin');
const fs = require('fs');

// Verificar que existe el archivo de service account
const serviceAccountPath = './serviceAccountKey.json';
if (!fs.existsSync(serviceAccountPath)) {
  console.error('❌ ERROR: No se encontró el archivo serviceAccountKey.json');
  console.error('\n📝 PASOS PARA OBTENER LA SERVICE ACCOUNT KEY:');
  console.error('1. Ve a: https://console.firebase.google.com');
  console.error('2. Selecciona tu proyecto');
  console.error('3. Ve a: ⚙️ Configuración del proyecto > Cuentas de servicio');
  console.error('4. Haz clic en "Generar nueva clave privada"');
  console.error('5. Guarda el archivo descargado como "serviceAccountKey.json" en la raíz del proyecto');
  console.error('6. Ejecuta: npm install firebase-admin');
  console.error('7. Ejecuta: node set-admin-claims.js\n');
  process.exit(1);
}

// Inicializar Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const auth = admin.auth();
const firestore = admin.firestore();

// Lista de usuarios administradores (según las capturas de Firebase)
const adminUsers = [
  {
    email: 'asistentepresupuesto2@lagotitaec.com',
    uid: 'F5i46Prx8IlCosOPpo2HQf3qix2',
    name: 'Asistente Presupuesto'
  },
  {
    email: 'jgironm20@miumg.edu.gt',
    uid: 'AmpRXdyfIPVZQiv5HvIxsqYM0L32',
    name: 'Augusto'
  },
  {
    email: 'joselitogiron.a@gmail.com',
    uid: 'JSgY0yd0ufTH7WurYlEwkRreZSv1',
    name: 'Joselito Girón'
  }
];

async function setAdminClaimsAndUpdateFirestore() {
  console.log('🚀 Iniciando proceso de asignación de permisos de administrador...\n');

  for (const user of adminUsers) {
    try {
      console.log(`📧 Procesando: ${user.email}`);
      console.log(`   UID: ${user.uid}`);

      // 1. Asignar custom claim admin: true en Firebase Authentication
      await auth.setCustomUserClaims(user.uid, { admin: true });
      console.log('   ✅ Custom claim "admin: true" asignado en Firebase Authentication');

      // 2. Actualizar documento en Firestore con role: "ADMIN"
      await firestore.collection('users').doc(user.uid).set({
        uid: user.uid,
        email: user.email,
        name: user.name,
        role: 'ADMIN',
        active: true
      }, { merge: true });
      console.log('   ✅ Documento actualizado en Firestore con role: "ADMIN"');

      // 3. Verificar que el claim se asignó correctamente
      const userRecord = await auth.getUser(user.uid);
      const customClaims = userRecord.customClaims || {};
      const hasAdminClaim = customClaims.admin === true;

      if (hasAdminClaim) {
        console.log('   ✅ Verificación exitosa - Custom claim confirmado');
      } else {
        console.log('   ⚠️  Advertencia - Custom claim no confirmado');
      }

      console.log('');

    } catch (error) {
      console.error(`   ❌ Error procesando ${user.email}:`, error.message);
      console.log('');
    }
  }

  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('🎉 PROCESO COMPLETADO EXITOSAMENTE');
  console.log('');
  console.log('✅ Custom claims "admin: true" asignados en Firebase Authentication');
  console.log('✅ Documentos actualizados en Firestore con role: "ADMIN"');
  console.log('');
  console.log('⚠️  IMPORTANTE - Los usuarios deben:');
  console.log('   1. Cerrar sesión en la app');
  console.log('   2. Volver a iniciar sesión');
  console.log('   3. O usar la función "Actualizar permisos" en la app');
  console.log('');
  console.log('💡 Ahora los administradores podrán:');
  console.log('   • Ver el listado de usuarios');
  console.log('   • Agregar/editar/eliminar productos');
  console.log('   • Acceder a todas las funciones administrativas');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  process.exit(0);
}

// Ejecutar el script
setAdminClaimsAndUpdateFirestore().catch((error) => {
  console.error('❌ Error fatal:', error);
  process.exit(1);
});
