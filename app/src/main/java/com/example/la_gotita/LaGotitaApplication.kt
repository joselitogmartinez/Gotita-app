package com.example.la_gotita

import android.app.Application
import com.google.firebase.FirebaseApp

class LaGotitaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializar Firebase
        FirebaseApp.initializeApp(this)
    }
}
