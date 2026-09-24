package cv.acta.app.ui.ajuda

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cv.acta.app.ui.comum.Cartao
import cv.acta.app.ui.comum.EcraBase
import cv.acta.app.ui.comum.Titulo

private val SECCOES = listOf(
    "Como funciona" to listOf(
        "1. Crie a reunião e registe os participantes, a presença e o consentimento de cada um.",
        "2. Confirme o aviso e grave. A gravação continua com o ecrã bloqueado; o indicador fica na barra de notificações.",
        "3. No fim, transcreva (é preciso a chave da API da OpenAI). Cada parte de 5 minutos é um pedido.",
        "4. Reveja a transcrição, associe os rótulos (P1-A, P2-B…) aos participantes e corrija o texto.",
        "5. Gere a acta, reveja os itens a âmbar (não verificados), envie para revisão e aprove.",
        "6. Distribua por e-mail, partilhe o PDF e agende a próxima reunião no calendário.",
    ),
    "Videoconferências (Zoom, Teams, Meet…)" to listOf(
        "O ACTA grava apenas o microfone do telemóvel. Não capta o áudio interno de outras apps.",
        "Se a videoconferência decorrer no próprio telemóvel, o Android dá o microfone à chamada e silencia outras gravações; " +
            "o ACTA deteta a chamada e pausa, pelo que essa parte não fica gravada.",
        "Para gravar uma videoconferência, use outro dispositivo com o altifalante ligado e o telemóvel do ACTA perto dele. " +
            "A qualidade depende do altifalante e do ruído da sala, e a identificação de oradores é menos fiável.",
    ),
    "Chamadas e interrupções" to listOf(
        "Chamada recebida: a gravação pausa sozinha, fica marcada na linha temporal e retoma no fim da chamada.",
        "Se o sistema terminar a app (por exemplo, ao revogar a permissão do microfone), o áudio já gravado é recuperado ao reabrir.",
        "Em cada mudança de parte (5 em 5 minutos) perdem-se cerca de 0,1 a 0,3 segundos de áudio.",
    ),
    "Limitações do MVP" to listOf(
        "A transcrição é feita depois da reunião, não em tempo real.",
        "O áudio é enviado para a OpenAI: não há processamento local.",
        "Os oradores começam sempre anónimos (sem impressões vocais); os rótulos só valem dentro de cada parte.",
        "Não há indicação de confiança por segmento.",
        "Não há tradução; o crioulo cabo-verdiano não é suportado de forma fiável.",
        "A exportação é só em PDF (não PDF/A, DOCX nem Markdown).",
        "A base de dados não é cifrada (a chave da API é). O envio de e-mail é feito pela app de e-mail do telemóvel.",
        "A app não infere emoções nem traços de personalidade a partir da voz.",
    ),
    "Privacidade" to listOf(
        "Os dados da app estão excluídos das cópias de segurança automáticas do Android.",
        "A chave da API é cifrada com uma chave do Android Keystore.",
        "Em Definições pode apagar os dados de exemplo ou todos os dados.",
    ),
)

@Composable
fun EcraAjuda(aoVoltar: () -> Unit) {
    EcraBase(titulo = "Ajuda e limitações", aoVoltar = aoVoltar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SECCOES.forEach { (titulo, linhas) ->
                Cartao {
                    Titulo(titulo)
                    linhas.forEach { Text(it, Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
    }
}
