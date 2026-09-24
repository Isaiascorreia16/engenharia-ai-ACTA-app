package cv.acta.app

import android.app.KeyguardManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import cv.acta.app.ui.Navegacao
import cv.acta.app.ui.tema.TemaActa

/**
 * Atividade única. Ao abrir a app pede autenticação (BiometricPrompt, com a credencial do
 * dispositivo como alternativa), porque o ecrã inicial mostra o arquivo das reuniões (RF-SEG-003).
 * O bloqueio é feito na criação da atividade e não ao regressar de outra app, para não
 * interromper o fluxo de envio de e-mail e de calendário.
 */
class MainActivity : FragmentActivity() {

    private var desbloqueada by mutableStateOf(false)
    private var mensagem by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        desbloqueada = savedInstanceState?.getBoolean(CHAVE_DESBLOQUEADA) ?: false
        setContent {
            TemaActa {
                Surface(Modifier.fillMaxSize()) {
                    if (desbloqueada) {
                        Navegacao()
                    } else {
                        Column(
                            Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("ACTA", style = MaterialTheme.typography.displayMedium)
                            Text("Arquivo protegido. Autentique-se para continuar.", Modifier.padding(vertical = 16.dp))
                            mensagem?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 16.dp)) }
                            Button(onClick = { autenticar() }) { Text("Desbloquear") }
                        }
                    }
                }
            }
        }
        if (!desbloqueada) autenticar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CHAVE_DESBLOQUEADA, desbloqueada)
    }

    private fun autenticar() {
        val autenticadores = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        val keyguard = getSystemService(KeyguardManager::class.java)
        val podeAutenticar = BiometricManager.from(this).canAuthenticate(autenticadores) == BiometricManager.BIOMETRIC_SUCCESS
        if (!podeAutenticar) {
            if (keyguard?.isDeviceSecure != true) {
                // Sem bloqueio de ecrã configurado não há como autenticar: abre, mas avisa.
                mensagem = null
                desbloqueada = true
                return
            }
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    mensagem = null
                    desbloqueada = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    mensagem = "Autenticação cancelada: $errString"
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear o ACTA")
                .setSubtitle("As reuniões gravadas contêm dados pessoais")
                .setAllowedAuthenticators(autenticadores)
                .build()
        )
    }

    companion object {
        private const val CHAVE_DESBLOQUEADA = "desbloqueada"
    }
}
