package cv.acta.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Entidades Room. Os enums guardam-se como texto (nome do enum) e convertem-se nos mapeadores.

@Entity(tableName = "reuniao")
data class ReuniaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titulo: String,
    val inicioPrevistoMs: Long,
    val local: String,
    val lingua: String,
    val origemGravacaoMs: Long?,
    val fimGravacaoMs: Long?,
    val demo: Boolean,
)

@Entity(tableName = "participante")
data class ParticipanteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val funcao: String,
    val email: String,
    val telefone: String?,
    val organizacao: String?,
)

@Entity(
    tableName = "participacao",
    primaryKeys = ["reuniaoId", "participanteId"],
    foreignKeys = [
        ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ParticipanteEntity::class, parentColumns = ["id"], childColumns = ["participanteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("participanteId")],
)
data class ParticipacaoEntity(
    val reuniaoId: Long,
    val participanteId: Long,
    val presenca: String,
    val consentimento: Boolean,
    val consentimentoEmMs: Long?,
)

@Entity(
    tableName = "sessao",
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reuniaoId")],
)
data class SessaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reuniaoId: Long,
    val inicioMs: Long,
    val fimMs: Long?,
    val estado: String,
)

@Entity(
    tableName = "parte",
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reuniaoId"), Index("sessaoId")],
)
data class ParteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessaoId: Long,
    val reuniaoId: Long,
    val numero: Int,
    val ficheiro: String,
    val inicioMs: Long,
    val duracaoMs: Long?,
    val estado: String,
    val erro: String?,
)

@Entity(
    tableName = "marca",
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reuniaoId")],
)
data class MarcaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reuniaoId: Long,
    val tipo: String,
    val inicioMs: Long,
    val fimMs: Long?,
)

@Entity(
    tableName = "segmento",
    primaryKeys = ["reuniaoId", "id"],
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
)
data class SegmentoEntity(
    val reuniaoId: Long,
    val id: String,
    val inicioMs: Long,
    val fimMs: Long,
    val orador: String,
    val texto: String,
)

@Entity(
    tableName = "orador",
    primaryKeys = ["reuniaoId", "rotulo"],
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
)
data class OradorEntity(
    val reuniaoId: Long,
    val rotulo: String,
    val participanteId: Long?,
)

@Entity(
    tableName = "edicao_segmento",
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("reuniaoId")],
)
data class EdicaoSegmentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reuniaoId: Long,
    val segmentoId: String,
    val autor: String,
    val emMs: Long,
    val textoAnterior: String,
    val textoNovo: String,
)

@Entity(
    tableName = "acta",
    foreignKeys = [ForeignKey(entity = ReuniaoEntity::class, parentColumns = ["id"], childColumns = ["reuniaoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["reuniaoId", "versao"], unique = true)],
)
data class ActaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reuniaoId: Long,
    val versao: Int,
    val estado: String,
    val documentoJson: String,
    val hash: String?,
    val criadaEmMs: Long,
    val aprovadaEmMs: Long?,
)

@Entity(
    tableName = "expedicao",
    foreignKeys = [ForeignKey(entity = ActaEntity::class, parentColumns = ["id"], childColumns = ["actaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("actaId")],
)
data class ExpedicaoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actaId: Long,
    val versao: Int,
    val emMs: Long,
    /** Endereços separados por mudança de linha. */
    val destinatarios: String,
)
