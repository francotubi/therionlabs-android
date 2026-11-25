package cl.duoc.therionlabs

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.therionlabs.data.local.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperacionLocalTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun laMigracionConservaFichasYAsignaIdentificadoresALasGestiones() = runBlocking {
        val nombre = "migracion-${UUID.randomUUID()}"
        try {
            SQLiteDatabase.openOrCreateDatabase(
                    contexto.getDatabasePath(nombre).apply { parentFile?.mkdirs() },
                    null,
                )
                .use { anterior ->
                    anterior.execSQL(
                        "CREATE TABLE IF NOT EXISTS `prospectos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventoId` TEXT NOT NULL DEFAULT '', `eventoNombre` TEXT NOT NULL DEFAULT '', `captadorId` TEXT NOT NULL DEFAULT '', `captador` TEXT NOT NULL DEFAULT '', `rut` TEXT NOT NULL, `nombre` TEXT NOT NULL, `apellidos` TEXT NOT NULL, `fechaNacimiento` TEXT NOT NULL, `celular` TEXT NOT NULL, `email` TEXT NOT NULL, `establecimiento` TEXT NOT NULL, `comunaResidencia` TEXT NOT NULL, `comunaEstablecimiento` TEXT NOT NULL, `actividadActual` TEXT NOT NULL, `jornada` TEXT NOT NULL, `region` TEXT NOT NULL, `sede` TEXT NOT NULL, `nivelInteres` TEXT NOT NULL, `carrera1` TEXT NOT NULL, `carrera2` TEXT, `carrera3` TEXT, `modalidad` TEXT NOT NULL DEFAULT '', `canalContacto` TEXT NOT NULL DEFAULT '', `observaciones` TEXT NOT NULL DEFAULT '', `autorizaContacto` INTEGER NOT NULL DEFAULT 0, `fechaCaptura` INTEGER NOT NULL DEFAULT 0, `sincronizado` INTEGER NOT NULL DEFAULT 0, `fotoRuta` TEXT, `latitud` REAL, `longitud` REAL, `precisionMetros` REAL, `fechaUbicacion` INTEGER, `direccion` TEXT, `identificador` TEXT NOT NULL DEFAULT '')"
                    )
                    anterior.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_prospectos_identificador` ON `prospectos` (`identificador`)"
                    )
                    anterior.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_prospectos_eventoId` ON `prospectos` (`eventoId`)"
                    )
                    anterior.execSQL(
                        "CREATE TABLE IF NOT EXISTS `usuarios` (`id` TEXT NOT NULL, `usuario` TEXT NOT NULL, `nombre` TEXT NOT NULL, `rol` TEXT NOT NULL, `salt` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, PRIMARY KEY(`id`))"
                    )
                    anterior.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS `index_usuarios_usuario` ON `usuarios` (`usuario`)"
                    )
                    anterior.execSQL(
                        "CREATE TABLE IF NOT EXISTS `eventos` (`id` TEXT NOT NULL, `nombre` TEXT NOT NULL, `colegio` TEXT NOT NULL, `fecha` TEXT NOT NULL, `actividad` TEXT NOT NULL, `activo` INTEGER NOT NULL, `comuna` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))"
                    )
                    anterior.execSQL(
                        "CREATE TABLE IF NOT EXISTS `seguimientos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `prospectoId` INTEGER NOT NULL, `administrativoId` TEXT NOT NULL, `administrativoNombre` TEXT NOT NULL, `estado` TEXT NOT NULL, `canal` TEXT NOT NULL, `observacion` TEXT NOT NULL, `fecha` INTEGER NOT NULL, `estadoEnvio` TEXT NOT NULL, FOREIGN KEY(`prospectoId`) REFERENCES `prospectos`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`administrativoId`) REFERENCES `usuarios`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
                    )
                    anterior.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_seguimientos_prospectoId` ON `seguimientos` (`prospectoId`)"
                    )
                    anterior.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_seguimientos_administrativoId` ON `seguimientos` (`administrativoId`)"
                    )
                    anterior.execSQL(
                        "INSERT INTO usuarios VALUES ('ADM-01','administrativo','Administración','ADMINISTRATIVO','sal','resumen')"
                    )
                    anterior.execSQL(
                        "INSERT INTO prospectos (id,rut,nombre,apellidos,fechaNacimiento,celular,email,establecimiento,comunaResidencia,comunaEstablecimiento,actividadActual,jornada,region,sede,nivelInteres,carrera1,identificador,sincronizado) VALUES (1,'12345678-5','Persona','De prueba','01/01/2005','+56912345678','persona@example.cl','Colegio','Quilpué','Quilpué','4to Medio','Diurna','Región de Valparaíso','Valparaíso','Alto','Analista Programador','ficha-anterior',2)"
                    )
                    anterior.execSQL(
                        "INSERT INTO seguimientos VALUES (1,1,'ADM-01','Administración','EN_SEGUIMIENTO','Correo','Se preparó información',1000,'PENDIENTE')"
                    )
                    anterior.version = 5
                }
            val base =
                Room.databaseBuilder(contexto, AppDatabase::class.java, nombre)
                    .addMigrations(AppDatabase.MIGRATION_5_6)
                    .build()
            try {
                val ficha = requireNotNull(base.prospectoDao().obtenerPorId(1))
                assertEquals("Persona", ficha.nombre)
                assertEquals(2, ficha.sincronizado)
                assertEquals("", ficha.motivoRechazo)
                assertFalse(ficha.eliminado)
                val gestion = base.operacionDao().gestionesPendientes().single()
                UUID.fromString(gestion.identificador)
                assertEquals("Se preparó información", gestion.observacion)
            } finally {
                base.close()
            }
        } finally {
            contexto.deleteDatabase(nombre)
        }
    }

    @Test
    fun actualizarUnEventoNoBorraSusProspectosYUnRechazoNoSeReenvia() = runBlocking {
        val base = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        try {
            base
                .operacionDao()
                .actualizarEventos(
                    listOf(
                        EventoEntity(
                            id = "evento",
                            nombre = "Feria",
                            colegio = "Colegio",
                            fecha = "10/12/2026",
                            actividad = "Orientación",
                            comuna = "Quilpué",
                        )
                    )
                )
            val ficha =
                ProspectoEntity(
                    eventoId = "evento",
                    rut = "12345678-5",
                    nombre = "Persona",
                    apellidos = "De prueba",
                    fechaNacimiento = "01/01/2005",
                    celular = "+56912345678",
                    email = "persona@example.cl",
                    establecimiento = "Colegio",
                    comunaResidencia = "Quilpué",
                    comunaEstablecimiento = "Quilpué",
                    actividadActual = "4to Medio",
                    jornada = "Diurna",
                    region = "Región de Valparaíso",
                    sede = "Valparaíso",
                    nivelInteres = "Alto",
                    carrera1 = "Analista Programador",
                )
            val id = base.prospectoDao().insertarProspecto(ficha).toInt()
            val evento = requireNotNull(base.operacionDao().evento("evento"))
            base.operacionDao().actualizarEventos(listOf(evento.copy(nombre = "Feria actualizada")))
            assertNotNull(base.prospectoDao().obtenerPorId(id))
            assertEquals("Feria actualizada", base.operacionDao().evento("evento")?.nombre)
            base.prospectoDao().rechazar(id, "RUT duplicado")
            assertEquals(2, base.prospectoDao().obtenerPorId(id)?.sincronizado)
            assertEquals("RUT duplicado", base.prospectoDao().obtenerPorId(id)?.motivoRechazo)
            assertTrue(base.prospectoDao().obtenerPendientesSincronizacion().isEmpty())
        } finally {
            base.close()
        }
    }
}
