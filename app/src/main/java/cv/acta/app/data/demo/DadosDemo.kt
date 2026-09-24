package cv.acta.app.data.demo

import cv.acta.app.data.RepositorioGravacao
import cv.acta.app.data.db.ActaDao
import cv.acta.app.data.db.OradorEntity
import cv.acta.app.data.db.ParteEntity
import cv.acta.app.data.db.ParticipacaoEntity
import cv.acta.app.data.db.ParticipanteEntity
import cv.acta.app.data.db.ReuniaoEntity
import cv.acta.app.data.db.SessaoEntity
import cv.acta.app.data.db.paraDominio
import cv.acta.app.data.db.paraEntidade
import cv.acta.app.domain.acta.AccaoGerada
import cv.acta.app.domain.acta.ActaGerada
import cv.acta.app.domain.acta.Cabecalho
import cv.acta.app.domain.acta.DeliberacaoGerada
import cv.acta.app.domain.acta.Verificador
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.modelo.EstadoParte
import cv.acta.app.domain.modelo.EstadoSessao
import cv.acta.app.domain.modelo.PontoOrdem
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.util.Formatos
import java.util.Locale

/**
 * Modo demonstração: uma reunião FICTÍCIA já processada (transcrição, oradores associados e acta em RASCUNHO),
 * para avaliar a app sem chave de API. Todos os nomes, e-mails (@example.org) e falas são inventados.
 * A acta de exemplo passa pelo mesmo Verificador que as actas reais.
 */
class DadosDemo(private val dao: ActaDao, private val gravacao: RepositorioGravacao) {

    private enum class Quem { ANA, CARLA, RUI }

    private data class Fala(val quem: Quem, val texto: String)

    private val parte1 = listOf(
        Fala(Quem.ANA, "Boa tarde a todos. Vamos dar início à reunião da direção. O Paulo avisou que não pode estar presente por motivos profissionais, por isso a falta dele fica justificada."),
        Fala(Quem.ANA, "Temos três pontos na ordem de trabalhos: o orçamento do festival de outono, a divulgação do festival e a renovação do protocolo com a câmara municipal."),
        Fala(Quem.CARLA, "Antes de começarmos, confirmo que a acta da reunião anterior já foi enviada a todos por e-mail na semana passada e não recebi nenhuma correção."),
        Fala(Quem.ANA, "Obrigada, Carla. Então passamos ao primeiro ponto. Rui, podes apresentar a proposta de orçamento para o festival?"),
        Fala(Quem.RUI, "Claro. A proposta que preparei prevê um orçamento total de oito mil e quinhentos euros. A maior parte, cerca de quatro mil euros, vai para o palco, o som e a iluminação."),
        Fala(Quem.RUI, "Depois temos mil e quinhentos euros para os cachets dos grupos musicais, mil euros para a segurança e a limpeza, e o restante para seguros, licenças e material de divulgação."),
        Fala(Quem.CARLA, "E do lado das receitas, quanto é que já está garantido? No ano passado tivemos dificuldades porque um dos patrocinadores desistiu à última hora."),
        Fala(Quem.RUI, "Neste momento temos garantidos cinco mil euros do apoio da câmara e mil e duzentos euros de dois patrocinadores locais. Faltam cerca de dois mil e trezentos euros."),
        Fala(Quem.ANA, "Esse valor em falta preocupa-me. Proponho que se façam contactos com mais três ou quatro empresas da zona antes de fecharmos a programação."),
        Fala(Quem.RUI, "Concordo. Posso preparar uma carta de apresentação do festival para enviar às empresas, com os números do público do ano passado."),
        Fala(Quem.CARLA, "Eu tenho os números: estiveram cerca de mil e duzentas pessoas no sábado e oitocentas no domingo. Posso mandar-te o relatório ainda hoje."),
        Fala(Quem.ANA, "Perfeito. Então, Rui, tu preparas a carta para os patrocinadores e envias até sexta-feira da próxima semana. Está bem assim?"),
        Fala(Quem.RUI, "Sim, está combinado. Até sexta-feira da próxima semana a carta segue para as empresas."),
        Fala(Quem.ANA, "Antes de votarmos o orçamento, alguém tem mais alguma questão ou proposta de alteração?"),
        Fala(Quem.CARLA, "Só uma sugestão: reservar uma pequena verba para imprevistos, talvez trezentos euros, retirada do material de divulgação, que no ano passado sobrou."),
        Fala(Quem.RUI, "Faz sentido. Posso ajustar a proposta assim: o total mantém-se nos oito mil e quinhentos euros, com trezentos euros para imprevistos."),
        Fala(Quem.ANA, "Então coloco à votação o orçamento de oito mil e quinhentos euros, com a reserva de trezentos euros para imprevistos. Quem está a favor? Aprovado por unanimidade dos presentes."),
    )

    private val parte2 = listOf(
        Fala(Quem.ANA, "Passamos ao segundo ponto, a divulgação do festival. Carla, fizeste o levantamento das opções que discutimos na última reunião?"),
        Fala(Quem.CARLA, "Fiz. Temos três opções principais: cartazes e folhetos distribuídos no comércio local, publicações pagas nas redes sociais e um anúncio na rádio comunitária."),
        Fala(Quem.CARLA, "Os cartazes e folhetos custam à volta de duzentos e cinquenta euros. As redes sociais podemos gerir com o orçamento que quisermos, e a rádio pediu cento e oitenta euros por duas semanas."),
        Fala(Quem.RUI, "Com o ajuste que fizemos no orçamento, ficamos com cerca de seiscentos euros para divulgação. Dá para as três opções, se formos cuidadosos nas redes sociais."),
        Fala(Quem.ANA, "Eu acho que a rádio comunitária é importante, porque chega às pessoas mais velhas que não usam redes sociais. Não devíamos deixar essa opção de fora."),
        Fala(Quem.CARLA, "Concordo. E os cartazes também ajudam, sobretudo nos cafés e nas mercearias do bairro. As pessoas ainda param para ler."),
        Fala(Quem.RUI, "Então a proposta seria: cartazes e folhetos, anúncio na rádio durante duas semanas e o que sobrar, cerca de cento e setenta euros, para as redes sociais."),
        Fala(Quem.ANA, "Parece-me equilibrado. Quem fica responsável por desenhar o cartaz? No ano passado foi um voluntário, mas ele mudou-se para fora da ilha."),
        Fala(Quem.CARLA, "Não sei quem poderá fazer isso este ano. Talvez possamos perguntar na escola de artes se algum aluno tem interesse."),
        Fala(Quem.ANA, "Boa ideia, mas ainda não temos ninguém definido. Fica como tarefa em aberto: arranjar alguém para desenhar o cartaz até ao dia dez de outubro."),
        Fala(Quem.RUI, "Convém não passar dessa data, porque a gráfica precisa de uma semana para imprimir os cartazes e os folhetos."),
        Fala(Quem.ANA, "Muito bem. Carla, podes tratar do contacto com a rádio comunitária e reservar o anúncio para as duas semanas antes do festival?"),
        Fala(Quem.CARLA, "Posso, sim. Trato disso até ao fim do mês e confirmo as datas com eles por escrito."),
        Fala(Quem.ANA, "Então coloco à votação o plano de divulgação: cartazes e folhetos, rádio comunitária e redes sociais com o valor restante. Quem está a favor?"),
        Fala(Quem.RUI, "A favor, com certeza."),
        Fala(Quem.CARLA, "Também a favor."),
        Fala(Quem.ANA, "Com os três votos a favor, o plano de divulgação fica aprovado por unanimidade dos presentes."),
    )

    private val parte3 = listOf(
        Fala(Quem.ANA, "Terceiro ponto: a renovação do protocolo com a câmara municipal. O protocolo atual termina em dezembro e temos de entregar a proposta de renovação."),
        Fala(Quem.CARLA, "Estive a reler o protocolo. A câmara pede um relatório de atividades do último ano e o plano de atividades para o próximo, além das contas aprovadas."),
        Fala(Quem.RUI, "As contas estão prontas. Posso juntar o relatório financeiro ao processo, mas o relatório de atividades ainda está por fazer."),
        Fala(Quem.ANA, "Eu posso escrever o relatório de atividades. Tenho as fotografias e as listas de presenças de todos os eventos do ano."),
        Fala(Quem.CARLA, "Isso ajudava muito. E o plano de atividades para o próximo ano, fazemos em conjunto numa próxima reunião?"),
        Fala(Quem.ANA, "Sim, acho melhor. Proponho que a direção aprove já a intenção de renovar o protocolo nos mesmos termos, e depois tratamos dos documentos."),
        Fala(Quem.RUI, "Estou de acordo. Os termos atuais são bons para nós, sobretudo a cedência do espaço do antigo mercado para os ensaios."),
        Fala(Quem.ANA, "Então coloco à votação a renovação do protocolo com a câmara nos mesmos termos. Quem está a favor?"),
        Fala(Quem.CARLA, "A favor."),
        Fala(Quem.RUI, "A favor."),
        Fala(Quem.ANA, "A renovação do protocolo fica aprovada por unanimidade. Eu comprometo-me a entregar o relatório de atividades até ao fim do mês."),
        Fala(Quem.RUI, "E eu envio à Carla o relatório financeiro até sexta-feira da próxima semana, para ela juntar tudo no mesmo processo."),
        Fala(Quem.CARLA, "Combinado. Assim que tiver os dois documentos, organizo o processo e confirmo com a câmara a data de entrega."),
        Fala(Quem.ANA, "Antes de terminarmos, temos de marcar a próxima reunião para preparar o plano de atividades. Que tal quinta-feira, dia um de outubro, às dezoito horas?"),
        Fala(Quem.RUI, "Por mim está bem. Às dezoito horas consigo chegar a tempo."),
        Fala(Quem.CARLA, "Para mim também serve. Fica marcada para dia um de outubro, aqui na sede."),
        Fala(Quem.ANA, "Então está marcado. Obrigada a todos pela presença e pela participação. Damos por encerrada a reunião."),
    )

    /** Rótulo que a diarização daria, por ordem de primeira intervenção em cada parte. */
    private fun rotulos(falas: List<Fala>): Map<Quem, String> =
        falas.map { it.quem }.distinct().mapIndexed { i, q -> q to ('A' + i).toString() }.toMap()

    /** Distribui as falas por ~295 s de cada parte, proporcionalmente ao número de palavras. */
    private fun segmentosDaParte(reuniaoId: Long, numero: Int, inicioParteMs: Long, falas: List<Fala>): List<Segmento> {
        val letras = rotulos(falas)
        val intervaloMs = 900L
        val palavras = falas.map { Formatos.contarPalavras(it.texto) }
        val disponivel = 295_000L - intervaloMs * falas.size
        val total = palavras.sum().toDouble()
        var t = inicioParteMs + 500
        return falas.mapIndexed { i, f ->
            val dur = (disponivel * palavras[i] / total).toLong()
            val s = Segmento(
                id = String.format(Locale.ROOT, "P%d-%03d", numero, i + 1),
                reuniaoId = reuniaoId,
                inicioMs = t,
                fimMs = t + dur,
                orador = "P$numero-${letras.getValue(f.quem)}",
                texto = f.texto,
            )
            t += dur + intervaloMs
            s
        }
    }

    /** Carrega a reunião de exemplo e devolve o seu id. */
    suspend fun carregar(): Long {
        val origem = Formatos.paraMs("2026-09-17", "18:02")
        val duracaoTotal = 3 * 300_000L
        val reuniaoId = dao.inserirReuniao(
            ReuniaoEntity(
                titulo = "[EXEMPLO FICTÍCIO] Direção da Associação Cultural Ribeira Viva",
                inicioPrevistoMs = Formatos.paraMs("2026-09-17", "18:00"),
                local = "Sede da associação (fictícia)",
                lingua = "Português",
                origemGravacaoMs = origem,
                fimGravacaoMs = origem + duracaoTotal,
                demo = true,
            )
        )

        val pessoas = listOf(
            Triple(Quem.ANA, ParticipanteEntity(nome = "Ana Tavares", funcao = "Presidente", email = "ana.tavares@example.org", telefone = null, organizacao = "Ribeira Viva (fictícia)"), Presenca.PRESENTE),
            Triple(Quem.RUI, ParticipanteEntity(nome = "Rui Monteiro", funcao = "Tesoureiro", email = "rui.monteiro@example.org", telefone = null, organizacao = "Ribeira Viva (fictícia)"), Presenca.PRESENTE),
            Triple(Quem.CARLA, ParticipanteEntity(nome = "Carla Semedo", funcao = "Secretária", email = "carla.semedo@example.org", telefone = null, organizacao = "Ribeira Viva (fictícia)"), Presenca.PRESENTE),
            Triple(null, ParticipanteEntity(nome = "Paulo Fortes", funcao = "Vogal", email = "paulo.fortes@example.org", telefone = null, organizacao = "Ribeira Viva (fictícia)"), Presenca.JUSTIFICADO),
        )
        val idDe = mutableMapOf<Quem, Long>()
        for ((quem, p, presenca) in pessoas) {
            val id = dao.inserirParticipante(p)
            quem?.let { idDe[it] = id }
            val presente = presenca == Presenca.PRESENTE
            dao.guardarParticipacao(ParticipacaoEntity(reuniaoId, id, presenca.name, presente, if (presente) origem - 60_000 else null))
        }

        val sessaoId = dao.inserirSessao(SessaoEntity(reuniaoId = reuniaoId, inicioMs = origem, fimMs = origem + duracaoTotal, estado = EstadoSessao.TERMINADA.name))
        val segmentos = mutableListOf<Segmento>()
        val oradores = mutableListOf<OradorEntity>()
        listOf(parte1, parte2, parte3).forEachIndexed { i, falas ->
            val numero = i + 1
            val inicio = i * 300_000L
            dao.inserirParte(
                ParteEntity(
                    sessaoId = sessaoId, reuniaoId = reuniaoId, numero = numero, ficheiro = "(exemplo sem áudio)",
                    inicioMs = inicio, duracaoMs = 300_000L, estado = EstadoParte.TRANSCRITA.name, erro = null,
                )
            )
            segmentos += segmentosDaParte(reuniaoId, numero, inicio, falas)
            rotulos(falas).forEach { (quem, letra) -> oradores += OradorEntity(reuniaoId, "P$numero-$letra", idDe[quem]) }
        }
        dao.inserirSegmentos(segmentos.map { it.paraEntidade() })
        dao.guardarOradores(oradores)

        // Acta de exemplo: o conteúdo que o LLM devolveria, passado pelo mesmo Verificador.
        val gerada = ActaGerada(
            ordemTrabalhos = listOf(
                PontoOrdem("Orçamento do festival de outono", "O tesoureiro apresentou uma proposta de 8 500 € (palco, som e iluminação; cachets; segurança e limpeza; seguros, licenças e divulgação). Estão garantidos 6 200 € e faltam cerca de 2 300 €. Foi acrescentada uma reserva de 300 € para imprevistos."),
                PontoOrdem("Divulgação do festival", "Foram analisadas três opções: cartazes e folhetos, rádio comunitária e redes sociais. A direção optou por combinar as três dentro do orçamento disponível de cerca de 600 €."),
                PontoOrdem("Renovação do protocolo com a câmara municipal", "O protocolo termina em dezembro. A câmara pede relatório de atividades, plano de atividades e contas aprovadas. O plano de atividades será preparado na próxima reunião."),
            ),
            deliberacoes = listOf(
                DeliberacaoGerada("Aprovado o orçamento do festival de outono no valor total de 8 500 €, com uma reserva de 300 € para imprevistos.", "Aprovado por unanimidade dos presentes", listOf("P1-016", "P1-017"), "coloco à votação o orçamento de oito mil e quinhentos euros"),
                DeliberacaoGerada("Aprovado o plano de divulgação: cartazes e folhetos, anúncio na rádio comunitária e redes sociais com o valor restante.", "Aprovado por unanimidade dos presentes (3 votos a favor)", listOf("P2-014", "P2-017"), "o plano de divulgação fica aprovado por unanimidade"),
                DeliberacaoGerada("Aprovada a intenção de renovar o protocolo com a câmara municipal nos mesmos termos.", "Aprovado por unanimidade", listOf("P3-008", "P3-011"), "A renovação do protocolo fica aprovada por unanimidade"),
            ),
            accoes = listOf(
                AccaoGerada("Rui Monteiro", "Preparar e enviar a carta de apresentação do festival a potenciais patrocinadores.", "2026-09-25", listOf("P1-012", "P1-013"), "Até sexta-feira da próxima semana a carta segue para as empresas"),
                AccaoGerada("Carla Semedo", "Contactar a rádio comunitária e reservar o anúncio para as duas semanas antes do festival.", "2026-09-30", listOf("P2-013"), "Trato disso até ao fim do mês e confirmo as datas com eles por escrito"),
                AccaoGerada(null, "Encontrar quem desenhe o cartaz do festival (por exemplo, um aluno da escola de artes).", "2026-10-10", listOf("P2-010"), "arranjar alguém para desenhar o cartaz até ao dia dez de outubro"),
                AccaoGerada("Ana Tavares", "Redigir o relatório de atividades para a renovação do protocolo.", "2026-09-30", listOf("P3-011"), "Eu comprometo-me a entregar o relatório de atividades até ao fim do mês"),
                AccaoGerada("Rui Monteiro", "Enviar à secretária o relatório financeiro para o processo de renovação.", "2026-09-25", listOf("P3-012"), "envio à Carla o relatório financeiro até sexta-feira da próxima semana"),
            ),
            proximaReuniao = "2026-10-01T18:00",
            sumulaExecutiva = "A direção reuniu com três dos quatro membros (falta justificada do vogal) e tratou de três pontos. " +
                "Aprovou por unanimidade o orçamento do festival de outono, de 8 500 €, com uma reserva de 300 € para imprevistos; " +
                "como faltam cerca de 2 300 € de receitas, o tesoureiro vai contactar novos patrocinadores. " +
                "Aprovou também o plano de divulgação, que combina cartazes e folhetos, a rádio comunitária e as redes sociais; " +
                "falta ainda encontrar quem desenhe o cartaz, até 10 de outubro. " +
                "Por fim, aprovou a intenção de renovar o protocolo com a câmara municipal nos mesmos termos: a presidente redige o relatório de atividades " +
                "e o tesoureiro envia o relatório financeiro à secretária, que organiza o processo. " +
                "A próxima reunião fica marcada para 1 de outubro, às 18h00, para preparar o plano de atividades.",
        )
        val reuniao = dao.reuniao(reuniaoId)!!.paraDominio()
        val participantes = dao.participantes(reuniaoId).map { it.paraDominio() }
        val conteudo = Verificador.verificar(gerada, segmentos, participantes.map { it.participante })
        dao.inserirActa(
            ActaVersao(
                reuniaoId = reuniaoId,
                versao = 1,
                estado = EstadoActa.RASCUNHO,
                documento = DocumentoActa(Cabecalho.construir(reuniao, participantes), conteudo),
                criadaEmMs = System.currentTimeMillis(),
            ).paraEntidade()
        )
        return reuniaoId
    }

    /** Apaga todas as reuniões marcadas como fictícias. Devolve quantas foram apagadas. */
    suspend fun apagar(): Int {
        val ids = dao.idsReunioesDemo()
        ids.forEach { gravacao.apagarReuniaoComFicheiros(it) }
        return ids.size
    }
}
