package cl.duoc.therionlabs

import android.app.Application
import android.content.SharedPreferences
import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.*
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Before
import retrofit2.Response

abstract class EscenarioLocal {
    protected val app = mockk<Application>()
    protected val db = mockk<AppDatabase>()
    protected val operacion = mockk<OperacionDAO>(relaxed = true)
    protected val prospectos = mockk<ProspectoDAO>(relaxed = true)
    protected val tokens = mockk<DispositivoTokenStore>(relaxed = true)
    protected val preferencias = mockk<SharedPreferences>(relaxed = true)
    protected val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    protected val eventos = MutableStateFlow<List<EventoEntity>>(emptyList())
    protected val fichas = MutableStateFlow<List<ProspectoEntity>>(emptyList())
    protected val gestiones = MutableStateFlow<List<SeguimientoEntity>>(emptyList())
    protected val admin = SesionUsuario("admin", "Administración", Roles.ADMINISTRATIVO)
    protected val captador = SesionUsuario("cuenta-cap", "Captador", Roles.CAPTADOR)
    protected val evento =
        EventoEntity(
            id = "a0000000-0000-4000-8000-000000000001",
            nombre = "Feria",
            colegio = "Colegio",
            fecha = "27/11/2026",
            actividad = "Orientación de carreras",
            activo = true,
            comuna = "Quilpué",
        )

    @Before
    fun prepararEscenarioLocal() {
        every { app.applicationContext } returns app
        every { app.getSharedPreferences(any(), any()) } returns preferencias
        every { preferencias.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putLong(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        every { db.operacionDao() } returns operacion
        every { db.prospectoDao() } returns prospectos
        every { operacion.eventos() } returns eventos
        every { operacion.seguimientos() } returns gestiones
        every { prospectos.obtenerProspectos() } returns fichas
        coEvery { operacion.usuarioPorId(admin.id) } returns
            UsuarioEntity(admin.id, "administrativo", admin.nombre, admin.rol, "sal", "hash")
        coEvery { operacion.usuarioPorId(captador.id) } returns
            UsuarioEntity(captador.id, "captador", captador.nombre, captador.rol, "sal", "hash")
        coEvery { operacion.evento(evento.id) } returns evento
        every { tokens.obtenerCaptadorId(captador.id) } returns "captador-remoto"
        every { tokens.obtenerCodigoCaptador(captador.id) } returns "CAP-03"
        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(any()) } returns db
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { db.withTransaction(any<suspend () -> Any?>()) } coAnswers
            {
                secondArg<suspend () -> Any?>().invoke()
            }
        ConfiguracionConexion.autorizar("sesion-prueba", Roles.ADMINISTRATIVO)
    }

    @After
    fun limpiarEscenarioLocal() {
        ConfiguracionConexion.cerrarSesion()
        unmockkAll()
    }

    protected fun ficha(id: Int = 1) =
        ProspectoEntity(
            id = id,
            eventoId = evento.id,
            eventoNombre = evento.nombre,
            captadorId = "CAP-03",
            captador = "CAP-03",
            rut = "12.345.678-5",
            nombre = "Persona",
            apellidos = "Prueba",
            fechaNacimiento = "01/01/2004",
            celular = "+56912345678",
            email = "persona@ejemplo.cl",
            establecimiento = "Colegio",
            comunaResidencia = "Quilpué",
            comunaEstablecimiento = "Quilpué",
            actividadActual = "Estudiante",
            jornada = "Diurna",
            region = CatalogoRegional.region,
            sede = "Viña del Mar",
            nivelInteres = "Alto",
            carrera1 = "Ingeniería en Informática",
            modalidad = "Presencial",
            canalContacto = "Correo electrónico",
            autorizaContacto = true,
            identificador = "b0000000-0000-4000-8000-000000000001",
        )

    protected fun remoto(ficha: ProspectoEntity = ficha()): ProspectoRemoto =
        com.google.gson
            .Gson()
            .fromJson(
                com.google.gson.Gson().toJson(ficha),
                ProspectoRemoto::class.java,
            )
            .copy(id = ficha.identificador, codigoCaptador = ficha.captadorId)

    protected fun <T> errorHttp(codigo: Int, mensaje: String = "Rechazado"): Response<T> =
        Response.error(
            codigo,
            ResponseBody.create(MediaType.parse("application/json"), "{\"mensaje\":\"$mensaje\"}"),
        )
}
