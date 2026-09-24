package cv.acta.app.ui.tema

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Claro = lightColorScheme(
    primary = Color(0xFF1B4F72),
    onPrimary = Color.White,
    secondary = Color(0xFF4A6572),
    error = Color(0xFFB3261E),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF9CCBF0),
    onPrimary = Color(0xFF00344F),
    secondary = Color(0xFFB0C9D6),
    error = Color(0xFFF2B8B5),
)

/** Âmbar para itens "Não verificado" (fundo) e texto escuro legível sobre ele. */
val AmbarFundo = Color(0xFFFFE08A)
val AmbarTexto = Color(0xFF3E2A00)
val VermelhoGravacao = Color(0xFFC62828)

/**
 * Tema Material 3. Os tamanhos de letra usam sp e a tipografia por omissão,
 * pelo que respeitam o tamanho de letra e o contraste definidos no sistema (RF-PLT-010).
 */
@Composable
fun TemaActa(conteudo: @Composable () -> Unit) {
    val escuro = isSystemInDarkTheme()
    val contexto = LocalContext.current
    val esquema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (escuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        escuro -> Escuro
        else -> Claro
    }
    MaterialTheme(colorScheme = esquema, content = conteudo)
}
