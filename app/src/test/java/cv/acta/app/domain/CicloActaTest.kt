package cv.acta.app.domain

import cv.acta.app.domain.ciclo.ActaImutavelException
import cv.acta.app.domain.ciclo.CicloActa
import cv.acta.app.domain.ciclo.HashActa
import cv.acta.app.domain.ciclo.TransicaoInvalidaException
import cv.acta.app.domain.ciclo.Versionamento
import cv.acta.app.domain.modelo.ActaVersao
import cv.acta.app.domain.modelo.CabecalhoActa
import cv.acta.app.domain.modelo.ConteudoActa
import cv.acta.app.domain.modelo.DocumentoActa
import cv.acta.app.domain.modelo.EstadoActa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CicloActaTest {

    private fun documento(sumula: String = "Súmula de teste.") = DocumentoActa(
        cabecalho = CabecalhoActa(1, "Reunião", "2026-09-24", "10:00", "11:00", "Sala 1", listOf("Ana"), listOf("Rui")),
        conteudo = ConteudoActa(emptyList(), emptyList(), emptyList(), null, sumula),
    )

    private fun acta(estado: EstadoActa) = ActaVersao(
        id = 7, reuniaoId = 1, versao = 1, estado = estado, documento = documento(), criadaEmMs = 1000,
    )

    @Test
    fun transicoesValidas() {
        val rascunho = acta(EstadoActa.RASCUNHO)
        val revisao = CicloActa.enviarParaRevisao(rascunho)
        assertEquals(EstadoActa.EM_REVISAO, revisao.estado)
        assertEquals(EstadoActa.RASCUNHO, CicloActa.voltarARascunho(revisao).estado)
        val aprovada = CicloActa.aprovar(revisao, revisao.documento, 5000)
        assertEquals(EstadoActa.APROVADA, aprovada.estado)
        assertEquals(EstadoActa.DISTRIBUIDA, CicloActa.distribuir(aprovada).estado)
    }

    @Test
    fun transicoesInvalidasLancamErro() {
        val invalidas = listOf(
            EstadoActa.RASCUNHO to EstadoActa.APROVADA,
            EstadoActa.RASCUNHO to EstadoActa.DISTRIBUIDA,
            EstadoActa.EM_REVISAO to EstadoActa.DISTRIBUIDA,
            EstadoActa.APROVADA to EstadoActa.RASCUNHO,
            EstadoActa.APROVADA to EstadoActa.EM_REVISAO,
            EstadoActa.DISTRIBUIDA to EstadoActa.RASCUNHO,
            EstadoActa.DISTRIBUIDA to EstadoActa.APROVADA,
        )
        for ((de, para) in invalidas) {
            assertFalse("$de → $para devia ser inválida", CicloActa.podeTransitar(de, para))
            try {
                CicloActa.validar(de, para)
                throw AssertionError("Esperava TransicaoInvalidaException para $de → $para")
            } catch (e: TransicaoInvalidaException) {
                assertEquals(para, e.para)
            }
        }
    }

    @Test(expected = TransicaoInvalidaException::class)
    fun distribuirUmRascunhoFalha() {
        CicloActa.distribuir(acta(EstadoActa.RASCUNHO))
    }

    @Test(expected = TransicaoInvalidaException::class)
    fun aprovarUmRascunhoSemRevisaoFalha() {
        CicloActa.aprovar(acta(EstadoActa.RASCUNHO), documento(), 1)
    }

    @Test
    fun aprovacaoCalculaSha256() {
        val aprovada = CicloActa.aprovar(acta(EstadoActa.EM_REVISAO), documento(), 5000)
        assertNotNull(aprovada.hash)
        assertEquals(64, aprovada.hash!!.length)
        assertEquals(HashActa.sha256(documento()), aprovada.hash)
        assertEquals(5000L, aprovada.aprovadaEmMs)
    }

    @Test
    fun hashMudaComOConteudo() {
        assertNotEquals(HashActa.sha256(documento("A")), HashActa.sha256(documento("B")))
        assertEquals(HashActa.sha256(documento("A")), HashActa.sha256(documento("A")))
    }

    @Test
    fun sha256DeTextoConhecido() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashActa.sha256("abc"),
        )
    }

    @Test
    fun alterarActaAprovadaCriaNovaVersao() {
        val aprovada = CicloActa.aprovar(acta(EstadoActa.EM_REVISAO), documento(), 5000)
        val resultado = Versionamento.alterar(aprovada, documento("Texto novo"), 9000)
        assertTrue(resultado is Versionamento.Resultado.NovaVersao)
        resultado as Versionamento.Resultado.NovaVersao
        // A anterior fica intacta.
        assertEquals(aprovada, resultado.anterior)
        assertEquals(EstadoActa.APROVADA, resultado.anterior.estado)
        assertEquals("Súmula de teste.", resultado.anterior.documento.conteudo.sumulaExecutiva)
        // A nova é um rascunho sem hash, com o número de versão seguinte.
        assertEquals(2, resultado.nova.versao)
        assertEquals(EstadoActa.RASCUNHO, resultado.nova.estado)
        assertNull(resultado.nova.hash)
        assertEquals("Texto novo", resultado.nova.documento.conteudo.sumulaExecutiva)
    }

    @Test
    fun alterarRascunhoAtualizaAMesmaVersao() {
        val resultado = Versionamento.alterar(acta(EstadoActa.RASCUNHO), documento("Outra"), 9000)
        assertTrue(resultado is Versionamento.Resultado.Atualizada)
        assertEquals(1, (resultado as Versionamento.Resultado.Atualizada).acta.versao)
    }

    @Test(expected = ActaImutavelException::class)
    fun actaDistribuidaNaoEEditavel() {
        Versionamento.garantirEditavel(acta(EstadoActa.DISTRIBUIDA))
    }
}
