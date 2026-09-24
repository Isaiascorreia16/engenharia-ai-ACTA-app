package cv.acta.app

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch

class ActaApp : Application() {
    lateinit var contentor: Contentor
        private set

    override fun onCreate() {
        super.onCreate()
        contentor = Contentor(this)
        // Um processo novo não tem gravação ativa: sessões abertas na BD vêm de uma terminação anómala.
        contentor.escopo.launch {
            val n = contentor.gravacao.recuperarSessoesInterrompidas()
            if (n > 0) Log.i("ActaApp", "Sessões interrompidas recuperadas: $n")
        }
    }
}
