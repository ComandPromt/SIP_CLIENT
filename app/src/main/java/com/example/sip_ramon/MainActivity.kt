package com.example.sip_ramon

import android.Manifest
import android.content.pm.PackageManager
import android.net.sip.SipAudioCall
import android.net.sip.SipException
import android.net.sip.SipManager
import android.net.sip.SipProfile
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sip_ramon.R

class MainActivity : AppCompatActivity() {

    private val TAG = "SipVoIP"
    private val PERMISSION_REQUEST_CODE = 100

    private var sipManager: SipManager? = null
    private var sipProfile: SipProfile? = null
    private var currentCall: SipAudioCall? = null

    // --- Referencias a Vistas (Perfil SIP Local) ---
    private val etProfileUser: EditText by lazy { findViewById(R.id.etProfileUser) }
    private val etProfileDomain: EditText by lazy { findViewById(R.id.etProfileDomain) }
    private val etProfilePassword: EditText by lazy { findViewById(R.id.etProfilePassword) }

    // --- Referencias a Vistas (Llamada y Estado) ---
    private val etSipUri: EditText by lazy { findViewById(R.id.etSipUri) }
    private val tvStatus: TextView by lazy { findViewById(R.id.tvStatus) }
    private val btnCall: Button by lazy { findViewById(R.id.btnCall) }
    private val btnHangup: Button by lazy { findViewById(R.id.btnHangup) }

    // --- Configuración Inicial ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Comprobación y solicitud de permisos
        if (checkAndRequestPermissions()) {
            initializeSipManager()
        }

        // 2. Configuración del Perfil SIP (¡Cargando desde Inputs!)
        configureSipProfile() // <-- Llamada a la nueva función

        // 3. Manejo de Eventos de Botones
        btnCall.setOnClickListener { startCall() }
        btnHangup.setOnClickListener { hangUpCall() }
    }

    // ------------------------------------------
    // --- Gestión de SIP (Funciones Corregidas) ---
    // ------------------------------------------

    /**
     * Configura el SipProfile tomando usuario, dominio y contraseña de los EditText.
     */
    private fun configureSipProfile() {
        val username = etProfileUser.text.toString()
        val domain = etProfileDomain.text.toString()
        val password = etProfilePassword.text.toString()

        if (username.isEmpty() || domain.isEmpty()) {
            tvStatus.text = "¡ALERTA!: Rellena usuario y dominio para configurar el perfil SIP."
            return
        }

        try {
            val builder = SipProfile.Builder(username, domain)
            if (password.isNotEmpty()) {
                builder.setPassword(password)
            }
            sipProfile = builder.build()
            tvStatus.text = "Estado: Perfil SIP configurado."
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear SipProfile: ${e.message}")
            tvStatus.text = "Error en la configuración SIP. Formato de URI incorrecto."
        }
    }

    private fun initializeSipManager() {
        if (sipManager == null) {
            sipManager = SipManager.newInstance(this)
        }
        tvStatus.text = "Estado: Manager Inicializado"
    }

    private fun startCall() {
        // ... (validaciones)
        val calleeUri = etSipUri.text.toString()
        if (calleeUri.isEmpty()) {
            Toast.makeText(this, "Introduce una URI SIP válida.", Toast.LENGTH_SHORT).show()
            return
        }

        // Verifica y abre el perfil SIP si no está ya abierto
        if (sipManager == null || sipProfile == null) {
            tvStatus.text = "Error: Perfil SIP no configurado o Manager ausente."
            return
        }

        // CORRECCIÓN: Usar getUriString() para obtener la URI como String y evitar el error 'getUri'.
        if (!sipManager!!.isOpened(sipProfile!!.getUriString())) {
            try {
                // Notifica al SipManager de la existencia de este perfil
                sipManager!!.open(sipProfile)
                // Nota: Una implementación real debería registrar el perfil aquí
            } catch (e: Exception) {
                Log.e(TAG, "Error al abrir el perfil: ${e.message}")
                tvStatus.text = "Error: Perfil no abierto. ¿Faltan permisos o credenciales?"
                return
            }
        }

        try {
            // Inicia la llamada de audio
            // CORRECCIÓN: Usar getUriString() para la URI del perfil local.
            currentCall = sipManager!!.makeAudioCall(
                sipProfile!!.getUriString(),
                calleeUri,
                SipSessionListener(),
                30 // Tiempo de espera
            )
            tvStatus.text = "Estado: Llamando a $calleeUri"
            btnCall.isEnabled = false
            btnHangup.isEnabled = true
        } catch (e: SipException) {
            Log.e(TAG, "Error al iniciar la llamada: ${e.message}")
            tvStatus.text = "Error de llamada: ${e.message}"
        }
    }

    // --- Funciones hangUpCall, SipSessionListener y Permisos (Sin cambios) ---
    // ...
    private fun hangUpCall() {
        currentCall?.let { call ->
            try {
                call.endCall() // Finaliza la llamada
            } catch (e: SipException) {
                Log.e(TAG, "Error al colgar: ${e.message}")
            } finally {
                call.close()
                currentCall = null
                runOnUiThread {
                    tvStatus.text = "Estado: Llamada finalizada"
                    btnCall.isEnabled = true
                    btnHangup.isEnabled = false
                }
            }
        }
    }

    inner class SipSessionListener : SipAudioCall.Listener() {

        override fun onCallEstablished(call: SipAudioCall) {
            call.setSpeakerMode(true)
            call.startAudio()
            runOnUiThread { tvStatus.text = "Estado: En llamada" }
        }

        override fun onCallEnded(call: SipAudioCall) {
            call.close()
            runOnUiThread {
                tvStatus.text = "Estado: Llamada finalizada"
                currentCall = null
                btnCall.isEnabled = true
                btnHangup.isEnabled = false
            }
        }

        override fun onError(call: SipAudioCall?, errorCode: Int, errorMessage: String?) {
            runOnUiThread {
                tvStatus.text = "Error SIP: $errorMessage"
                btnCall.isEnabled = true
                btnHangup.isEnabled = false
            }
        }
    }

    private fun checkAndRequestPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.USE_SIP,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeSipManager()
            } else {
                Toast.makeText(this, "Permisos denegados. VoIP no funcionará.", Toast.LENGTH_LONG).show()
            }
        }
    }
}