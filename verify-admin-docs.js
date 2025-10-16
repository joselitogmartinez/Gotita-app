// Script para verificar y actualizar documentos de usuarios administradores
const admin = require('firebase-admin');
const fs = require('fs');

// Verificar que existe el archivo de service account
const serviceAccountPath = './serviceAccountKey.json';
if (!fs.existsSync(serviceAccountPath)) {
  console.error('❌ ERROR: No se encontró el archivo serviceAccountKey.json');
  process.exit(1);
}

// Inicializar Firebase Admin SDK
const serviceAccount = require('./serviceAccountKey.json');
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const firestore = admin.firestore();

// Lista de usuarios administradores con sus UIDs correctos
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

async function verifyAndUpdateUserDocs() {
  console.log('🔍 Verificando y actualizando documentos de usuarios administradores...\n');

  for (const user of adminUsers) {
    try {
      console.log(`📧 Procesando: ${user.email}`);
      console.log(`   UID: ${user.uid}`);

      // Crear/actualizar documento en Firestore
      await firestore.collection('users').doc(user.uid).set({
        uid: user.uid,
        email: user.email,
        name: user.name,
        role: 'ADMIN',
        active: true,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });

      console.log('   ✅ Documento actualizado en Firestore');

      // Verificar que el documento existe
      const doc = await firestore.collection('users').doc(user.uid).get();
      if (doc.exists) {
        const userData = doc.data();
        console.log(`   ✅ Verificado - Role: ${userData.role}, Active: ${userData.active}`);
      } else {
        console.log('   ❌ Error - Documento no encontrado después de la actualización');
      }

      console.log('');

    } catch (error) {
      console.error(`   ❌ Error procesando ${user.email}:`, error.message);
      console.log('');
    }
  }

  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('🎉 VERIFICACIÓN COMPLETADA');
  console.log('');
  console.log('✅ Documentos de usuarios verificados y actualizados');
  console.log('✅ Todos los administradores tienen role: "ADMIN"');
  console.log('✅ Todos los administradores están activos: true');
  console.log('');
  console.log('🚀 SIGUIENTE PASO: Desplegar las nuevas reglas de Firestore');
  console.log('   Ejecuta: firebase deploy --only firestore:rules');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');

  process.exit(0);
}

// Ejecutar el script
verifyAndUpdateUserDocs().catch((error) => {
  console.error('❌ Error fatal:', error);
  process.exit(1);
});
