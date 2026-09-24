package cv.acta.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ParticipanteComParticipacao(
    val id: Long,
    val nome: String,
    val funcao: String,
    val email: String,
    val telefone: String?,
    val organizacao: String?,
    val reuniaoId: Long,
    val presenca: String,
    val consentimento: Boolean,
    val consentimentoEmMs: Long?,
)

data class ResultadoPesquisa(
    val reuniaoId: Long,
    val titulo: String,
    val origem: String,
    val excerto: String,
)

@Dao
abstract class ActaDao {

    // ---------- Reuniões ----------
    @Query("SELECT * FROM reuniao ORDER BY inicioPrevistoMs DESC")
    abstract fun observarReunioes(): Flow<List<ReuniaoEntity>>

    @Query("SELECT * FROM reuniao WHERE id = :id")
    abstract fun observarReuniao(id: Long): Flow<ReuniaoEntity?>

    @Query("SELECT * FROM reuniao WHERE id = :id")
    abstract suspend fun reuniao(id: Long): ReuniaoEntity?

    @Query("SELECT COUNT(*) FROM reuniao")
    abstract suspend fun contarReunioes(): Int

    @Insert
    abstract suspend fun inserirReuniao(r: ReuniaoEntity): Long

    @Update
    abstract suspend fun atualizarReuniao(r: ReuniaoEntity)

    @Query("DELETE FROM reuniao WHERE id = :id")
    abstract suspend fun apagarReuniao(id: Long)

    @Query("SELECT id FROM reuniao WHERE demo = 1")
    abstract suspend fun idsReunioesDemo(): List<Long>

    // ---------- Participantes ----------
    @Insert
    abstract suspend fun inserirParticipante(p: ParticipanteEntity): Long

    @Update
    abstract suspend fun atualizarParticipante(p: ParticipanteEntity)

    @Upsert
    abstract suspend fun guardarParticipacao(p: ParticipacaoEntity)

    @Query("DELETE FROM participacao WHERE reuniaoId = :reuniaoId AND participanteId = :participanteId")
    abstract suspend fun removerParticipacao(reuniaoId: Long, participanteId: Long)

    @Query("DELETE FROM participante WHERE id NOT IN (SELECT participanteId FROM participacao)")
    abstract suspend fun apagarParticipantesOrfaos()

    @Query(
        """SELECT p.id, p.nome, p.funcao, p.email, p.telefone, p.organizacao,
                  c.reuniaoId, c.presenca, c.consentimento, c.consentimentoEmMs
           FROM participante p JOIN participacao c ON c.participanteId = p.id
           WHERE c.reuniaoId = :reuniaoId ORDER BY p.nome"""
    )
    abstract fun observarParticipantes(reuniaoId: Long): Flow<List<ParticipanteComParticipacao>>

    @Query(
        """SELECT p.id, p.nome, p.funcao, p.email, p.telefone, p.organizacao,
                  c.reuniaoId, c.presenca, c.consentimento, c.consentimentoEmMs
           FROM participante p JOIN participacao c ON c.participanteId = p.id
           WHERE c.reuniaoId = :reuniaoId ORDER BY p.nome"""
    )
    abstract suspend fun participantes(reuniaoId: Long): List<ParticipanteComParticipacao>

    // ---------- Sessões, partes e marcas ----------
    @Insert
    abstract suspend fun inserirSessao(s: SessaoEntity): Long

    @Update
    abstract suspend fun atualizarSessao(s: SessaoEntity)

    @Query("SELECT * FROM sessao WHERE id = :id")
    abstract suspend fun sessao(id: Long): SessaoEntity?

    @Query("SELECT * FROM sessao WHERE estado IN ('A_GRAVAR', 'EM_PAUSA')")
    abstract suspend fun sessoesAbertas(): List<SessaoEntity>

    @Query("SELECT * FROM sessao WHERE reuniaoId = :reuniaoId ORDER BY inicioMs")
    abstract fun observarSessoes(reuniaoId: Long): Flow<List<SessaoEntity>>

    @Insert
    abstract suspend fun inserirParte(p: ParteEntity): Long

    @Update
    abstract suspend fun atualizarParte(p: ParteEntity)

    @Query("DELETE FROM parte WHERE id = :id")
    abstract suspend fun apagarParte(id: Long)

    @Query("SELECT * FROM parte WHERE id = :id")
    abstract suspend fun parte(id: Long): ParteEntity?

    @Query("SELECT * FROM parte WHERE reuniaoId = :reuniaoId ORDER BY numero")
    abstract suspend fun partes(reuniaoId: Long): List<ParteEntity>

    @Query("SELECT * FROM parte WHERE reuniaoId = :reuniaoId ORDER BY numero")
    abstract fun observarPartes(reuniaoId: Long): Flow<List<ParteEntity>>

    @Query("SELECT * FROM parte WHERE sessaoId = :sessaoId ORDER BY numero")
    abstract suspend fun partesDaSessao(sessaoId: Long): List<ParteEntity>

    @Query("SELECT COALESCE(MAX(numero), 0) FROM parte WHERE reuniaoId = :reuniaoId")
    abstract suspend fun ultimoNumeroParte(reuniaoId: Long): Int

    @Insert
    abstract suspend fun inserirMarca(m: MarcaEntity): Long

    @Update
    abstract suspend fun atualizarMarca(m: MarcaEntity)

    @Query("SELECT * FROM marca WHERE id = :id")
    abstract suspend fun marca(id: Long): MarcaEntity?

    @Query("SELECT * FROM marca WHERE reuniaoId = :reuniaoId ORDER BY inicioMs")
    abstract fun observarMarcas(reuniaoId: Long): Flow<List<MarcaEntity>>

    // ---------- Segmentos e oradores ----------
    @Query("SELECT * FROM segmento WHERE reuniaoId = :reuniaoId ORDER BY inicioMs, id")
    abstract fun observarSegmentos(reuniaoId: Long): Flow<List<SegmentoEntity>>

    @Query("SELECT * FROM segmento WHERE reuniaoId = :reuniaoId ORDER BY inicioMs, id")
    abstract suspend fun segmentos(reuniaoId: Long): List<SegmentoEntity>

    @Query("SELECT * FROM segmento WHERE reuniaoId = :reuniaoId AND id = :id")
    abstract suspend fun segmento(reuniaoId: Long, id: String): SegmentoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun inserirSegmentos(s: List<SegmentoEntity>)

    @Update
    abstract suspend fun atualizarSegmento(s: SegmentoEntity)

    @Query("DELETE FROM segmento WHERE reuniaoId = :reuniaoId AND id LIKE :prefixo || '%'")
    abstract suspend fun apagarSegmentosComPrefixo(reuniaoId: Long, prefixo: String)

    @Query("SELECT * FROM orador WHERE reuniaoId = :reuniaoId ORDER BY rotulo")
    abstract fun observarOradores(reuniaoId: Long): Flow<List<OradorEntity>>

    @Query("SELECT * FROM orador WHERE reuniaoId = :reuniaoId ORDER BY rotulo")
    abstract suspend fun oradores(reuniaoId: Long): List<OradorEntity>

    @Upsert
    abstract suspend fun guardarOradores(o: List<OradorEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun inserirOradoresNovos(o: List<OradorEntity>)

    @Insert
    abstract suspend fun inserirEdicao(e: EdicaoSegmentoEntity): Long

    @Query("SELECT * FROM edicao_segmento WHERE reuniaoId = :reuniaoId ORDER BY emMs DESC")
    abstract fun observarEdicoes(reuniaoId: Long): Flow<List<EdicaoSegmentoEntity>>

    @Transaction
    open suspend fun editarSegmento(novo: SegmentoEntity, edicao: EdicaoSegmentoEntity) {
        atualizarSegmento(novo)
        inserirEdicao(edicao)
    }

    @Transaction
    open suspend fun substituirSegmentosDaParte(reuniaoId: Long, prefixo: String, segmentos: List<SegmentoEntity>, oradores: List<OradorEntity>) {
        apagarSegmentosComPrefixo(reuniaoId, prefixo)
        inserirSegmentos(segmentos)
        // Rótulos novos entram como anónimos; associações já feitas pelo utilizador mantêm-se.
        inserirOradoresNovos(oradores)
    }

    // ---------- Actas ----------
    @Query("SELECT * FROM acta WHERE reuniaoId = :reuniaoId ORDER BY versao DESC")
    abstract fun observarActas(reuniaoId: Long): Flow<List<ActaEntity>>

    @Query("SELECT * FROM acta WHERE reuniaoId = :reuniaoId ORDER BY versao DESC LIMIT 1")
    abstract suspend fun ultimaActa(reuniaoId: Long): ActaEntity?

    @Query("SELECT * FROM acta WHERE id = :id")
    abstract suspend fun acta(id: Long): ActaEntity?

    @Query("SELECT * FROM acta WHERE id = :id")
    abstract fun observarActa(id: Long): Flow<ActaEntity?>

    @Insert
    abstract suspend fun inserirActa(a: ActaEntity): Long

    @Update
    abstract suspend fun atualizarActa(a: ActaEntity)

    @Insert
    abstract suspend fun inserirExpedicao(e: ExpedicaoEntity): Long

    @Query("SELECT * FROM expedicao WHERE actaId = :actaId ORDER BY emMs DESC")
    abstract fun observarExpedicoes(actaId: Long): Flow<List<ExpedicaoEntity>>

    @Transaction
    open suspend fun registarDistribuicao(acta: ActaEntity, expedicao: ExpedicaoEntity) {
        atualizarActa(acta)
        inserirExpedicao(expedicao)
    }

    // ---------- Pesquisa (RF-PER-003) ----------
    @Query(
        """SELECT id AS reuniaoId, titulo, 'Título' AS origem, titulo AS excerto
             FROM reuniao WHERE titulo LIKE '%' || :q || '%' OR local LIKE '%' || :q || '%'
           UNION ALL
           SELECT s.reuniaoId, r.titulo, 'Transcrição' AS origem, s.texto AS excerto
             FROM segmento s JOIN reuniao r ON r.id = s.reuniaoId WHERE s.texto LIKE '%' || :q || '%'
           UNION ALL
           SELECT a.reuniaoId, r.titulo, 'Acta v' || a.versao AS origem, '' AS excerto
             FROM acta a JOIN reuniao r ON r.id = a.reuniaoId WHERE a.documentoJson LIKE '%' || :q || '%'
           LIMIT 200"""
    )
    abstract suspend fun pesquisar(q: String): List<ResultadoPesquisa>
}
