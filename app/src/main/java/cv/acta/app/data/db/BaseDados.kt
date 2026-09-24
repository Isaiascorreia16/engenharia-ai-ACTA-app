package cv.acta.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReuniaoEntity::class,
        ParticipanteEntity::class,
        ParticipacaoEntity::class,
        SessaoEntity::class,
        ParteEntity::class,
        MarcaEntity::class,
        SegmentoEntity::class,
        OradorEntity::class,
        EdicaoSegmentoEntity::class,
        ActaEntity::class,
        ExpedicaoEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BaseDados : RoomDatabase() {
    abstract fun dao(): ActaDao

    companion object {
        const val NOME = "acta.db"

        fun criar(context: Context): BaseDados =
            Room.databaseBuilder(context.applicationContext, BaseDados::class.java, NOME).build()
    }
}
