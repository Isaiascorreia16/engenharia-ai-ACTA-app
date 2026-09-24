package cv.acta.app.data.db

import cv.acta.app.domain.ciclo.HashActa
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.EdicaoSegmento
import cv.acta.app.domain.modelo.EstadoActa
import cv.acta.app.domain.modelo.EstadoParte
import cv.acta.app.domain.modelo.EstadoSessao
import cv.acta.app.domain.modelo.Expedicao
import cv.acta.app.domain.modelo.MarcaTemporal
import cv.acta.app.domain.modelo.Orador
import cv.acta.app.domain.modelo.ParteGravada
import cv.acta.app.domain.modelo.Participacao
import cv.acta.app.domain.modelo.Participante
import cv.acta.app.domain.modelo.ParticipanteNaReuniao
import cv.acta.app.domain.modelo.Presenca
import cv.acta.app.domain.modelo.Reuniao
import cv.acta.app.domain.modelo.Segmento
import cv.acta.app.domain.modelo.Sessao
import cv.acta.app.domain.modelo.TipoMarca

// Conversão entre entidades Room (data/) e modelos do domínio (domain/).

fun ReuniaoEntity.paraDominio() = Reuniao(id, titulo, inicioPrevistoMs, local, lingua, origemGravacaoMs, fimGravacaoMs, demo)
fun Reuniao.paraEntidade() = ReuniaoEntity(id, titulo, inicioPrevistoMs, local, lingua, origemGravacaoMs, fimGravacaoMs, demo)

fun Participante.paraEntidade() = ParticipanteEntity(id, nome, funcao, email, telefone, organizacao)
fun Participacao.paraEntidade() = ParticipacaoEntity(reuniaoId, participanteId, presenca.name, consentimento, consentimentoEmMs)

fun ParticipanteComParticipacao.paraDominio() = ParticipanteNaReuniao(
    participante = Participante(id, nome, funcao, email, telefone, organizacao),
    participacao = Participacao(reuniaoId, id, Presenca.valueOf(presenca), consentimento, consentimentoEmMs),
)

fun SessaoEntity.paraDominio() = Sessao(id, reuniaoId, inicioMs, fimMs, EstadoSessao.valueOf(estado))
fun Sessao.paraEntidade() = SessaoEntity(id, reuniaoId, inicioMs, fimMs, estado.name)

fun ParteEntity.paraDominio() = ParteGravada(id, sessaoId, reuniaoId, numero, ficheiro, inicioMs, duracaoMs, EstadoParte.valueOf(estado), erro)
fun ParteGravada.paraEntidade() = ParteEntity(id, sessaoId, reuniaoId, numero, ficheiro, inicioMs, duracaoMs, estado.name, erro)

fun MarcaEntity.paraDominio() = MarcaTemporal(id, reuniaoId, TipoMarca.valueOf(tipo), inicioMs, fimMs)
fun MarcaTemporal.paraEntidade() = MarcaEntity(id, reuniaoId, tipo.name, inicioMs, fimMs)

fun SegmentoEntity.paraDominio() = Segmento(id, reuniaoId, inicioMs, fimMs, orador, texto)
fun Segmento.paraEntidade() = SegmentoEntity(reuniaoId, id, inicioMs, fimMs, orador, texto)

fun OradorEntity.paraDominio() = Orador(reuniaoId, rotulo, participanteId)
fun Orador.paraEntidade() = OradorEntity(reuniaoId, rotulo, participanteId)

fun EdicaoSegmentoEntity.paraDominio() = EdicaoSegmento(id, reuniaoId, segmentoId, autor, emMs, textoAnterior, textoNovo)
fun EdicaoSegmento.paraEntidade() = EdicaoSegmentoEntity(id, reuniaoId, segmentoId, autor, emMs, textoAnterior, textoNovo)

fun ExpedicaoEntity.paraDominio() = Expedicao(id, actaId, versao, emMs, destinatarios.split('\n').filter { it.isNotBlank() })
fun Expedicao.paraEntidade() = ExpedicaoEntity(id, actaId, versao, emMs, destinatarios.joinToString("\n"))

fun ActaEntity.paraDominio() = ActaVersao(
    id = id,
    reuniaoId = reuniaoId,
    versao = versao,
    estado = EstadoActa.valueOf(estado),
    documento = HashActa.json.decodeFromString(DocumentoActa.serializer(), documentoJson),
    hash = hash,
    criadaEmMs = criadaEmMs,
    aprovadaEmMs = aprovadaEmMs,
)

/** Para uma acta aprovada, guarda-se exatamente o JSON canónico sobre o qual o hash foi calculado. */
fun ActaVersao.paraEntidade() = ActaEntity(
    id = id,
    reuniaoId = reuniaoId,
    versao = versao,
    estado = estado.name,
    documentoJson = HashActa.canonico(documento),
    hash = hash,
    criadaEmMs = criadaEmMs,
    aprovadaEmMs = aprovadaEmMs,
)
