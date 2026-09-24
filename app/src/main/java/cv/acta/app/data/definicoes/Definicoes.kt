package cv.acta.app.data.definicoes

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "definicoes")

/**
 * Definições da app em DataStore. A chave da API nunca é guardada em claro:
 * é cifrada com uma chave AES-256/GCM gerada no Android Keystore (que não sai do hardware seguro)
 * e só o texto cifrado (IV + dados, em Base64) vai para o DataStore.
 */
class Definicoes(private val contexto: Context) {

    companion object {
        const val MODELO_TEXTO_OMISSAO = "gpt-6-sol"
        const val AUTOR_OMISSAO = "Utilizador do dispositivo"

        private val CHAVE_CIFRADA = stringPreferencesKey("chave_api_cifrada")
        private val MODELO_TEXTO = stringPreferencesKey("modelo_texto")
        private val AUTOR = stringPreferencesKey("autor_edicoes")
        private val DEMO_OFERECIDA = booleanPreferencesKey("demo_oferecida")
        private val TTS_VELOCIDADE = floatPreferencesKey("tts_velocidade")
        private val TTS_VOLUME = floatPreferencesKey("tts_volume")
    }

    private val dados get() = contexto.dataStore.data

    val temChave: Flow<Boolean> = dados.map { it[CHAVE_CIFRADA] != null }
    val modeloTexto: Flow<String> = dados.map { it[MODELO_TEXTO] ?: MODELO_TEXTO_OMISSAO }
    val autorEdicoes: Flow<String> = dados.map { it[AUTOR] ?: AUTOR_OMISSAO }
    val demoOferecida: Flow<Boolean> = dados.map { it[DEMO_OFERECIDA] ?: false }
    val ttsVelocidade: Flow<Float> = dados.map { it[TTS_VELOCIDADE] ?: 1.0f }
    val ttsVolume: Flow<Float> = dados.map { it[TTS_VOLUME] ?: 1.0f }

    suspend fun guardarChave(chave: String) {
        val cifrada = CofreChave.cifrar(chave.trim())
        contexto.dataStore.edit { it[CHAVE_CIFRADA] = cifrada }
    }

    /** Decifra a chave só no momento do pedido. Devolve null se não houver chave ou se não for possível decifrá-la. */
    suspend fun chave(): String? {
        val cifrada = dados.first()[CHAVE_CIFRADA] ?: return null
        return try {
            CofreChave.decifrar(cifrada)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun apagarChave() {
        contexto.dataStore.edit { it.remove(CHAVE_CIFRADA) }
    }

    suspend fun guardarModelo(modelo: String) {
        contexto.dataStore.edit { it[MODELO_TEXTO] = modelo.trim().ifBlank { MODELO_TEXTO_OMISSAO } }
    }

    suspend fun guardarAutor(autor: String) {
        contexto.dataStore.edit { it[AUTOR] = autor.trim().ifBlank { AUTOR_OMISSAO } }
    }

    suspend fun marcarDemoOferecida() {
        contexto.dataStore.edit { it[DEMO_OFERECIDA] = true }
    }

    suspend fun guardarVoz(velocidade: Float, volume: Float) {
        contexto.dataStore.edit {
            it[TTS_VELOCIDADE] = velocidade
            it[TTS_VOLUME] = volume
        }
    }

    /** Usado em "Apagar todos os dados": limpa as definições e destrói a chave do Keystore. */
    suspend fun apagarTudo() {
        contexto.dataStore.edit { it.clear() }
        CofreChave.destruir()
    }
}

object CofreChave {
    private const val PROVEDOR = "AndroidKeyStore"
    private const val ALIAS = "acta_chave_api"
    private const val TRANSFORMACAO = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun chaveSecreta(): SecretKey {
        val ks = KeyStore.getInstance(PROVEDOR).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gerador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEDOR)
        gerador.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gerador.generateKey()
    }

    fun cifrar(texto: String): String {
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.ENCRYPT_MODE, chaveSecreta())
        val iv = cifra.iv
        val dados = cifra.doFinal(texto.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(dados, Base64.NO_WRAP)
    }

    fun decifrar(guardado: String): String {
        val (ivB64, dadosB64) = guardado.split(":", limit = 2)
        val cifra = Cipher.getInstance(TRANSFORMACAO)
        cifra.init(Cipher.DECRYPT_MODE, chaveSecreta(), GCMParameterSpec(TAG_BITS, Base64.decode(ivB64, Base64.NO_WRAP)))
        return String(cifra.doFinal(Base64.decode(dadosB64, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    fun destruir() {
        val ks = KeyStore.getInstance(PROVEDOR).apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
    }
}
