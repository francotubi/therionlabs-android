package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.repository.OperacionRepository
import cl.duoc.therionlabs.domain.BorradorEvento
import cl.duoc.therionlabs.network.*
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class OperacionRepositoryTest : EscenarioLocal() {
    private val api = mockk<EventoApi>()
    private val remoto
        get() =
            EventoResponse(
                evento.id,
                evento.nombre,
                evento.colegio,
                "2026-11-27",
                evento.actividad,
                evento.comuna,
                true,
            )

    private val borrador
        get() =
            BorradorEvento(
                evento.nombre,
                evento.colegio,
                evento.fecha,
                evento.comuna,
                evento.actividad,
            )

    @Before
    fun prepararApi() {
        mockkObject(RetrofitClient)
        every { RetrofitClient.eventoApi } returns api
    }

    @Test
    fun creaElEventoEnServidorAntesDeGuardarloLocalmente() = runTest {
        val repo = OperacionRepository(db)
        coEvery { api.crearEvento(any()) } returns Response.success(remoto)
        assertEquals(evento.id, repo.crearEvento(admin, borrador))
        coVerify { api.crearEvento(match { it.fecha == "2026-11-27" && it.comuna == "Quilpué" }) }
        coVerify {
            operacion.actualizarEventos(
                match { it.single().id == evento.id && it.single().fecha == evento.fecha }
            )
        }
        assertTrue(repo.eventos.first().isEmpty())
        assertTrue(repo.seguimientos.first().isEmpty())
    }

    @Test
    fun rechazaCrearSinPermisoConCamposInvalidosODuplicados() = runTest {
        val repo = OperacionRepository(db)
        assertTrue(runCatching { repo.crearEvento(captador, borrador) }.isFailure)
        assertTrue(runCatching { repo.crearEvento(admin, BorradorEvento()) }.isFailure)
        coEvery { operacion.listaEventos() } returns listOf(evento)
        assertTrue(
            runCatching { repo.crearEvento(admin, borrador.copy(nombre = " FERIA ")) }.isFailure
        )
        coVerify(exactly = 0) { api.crearEvento(any()) }
    }

    @Test
    fun unErrorORespuestaVaciaNoInsertaElEvento() = runTest {
        val repo = OperacionRepository(db)
        coEvery { api.crearEvento(any()) } returns errorHttp(503)
        assertTrue(runCatching { repo.crearEvento(admin, borrador) }.isFailure)
        coEvery { api.crearEvento(any()) } returns Response.success(null)
        assertTrue(runCatching { repo.crearEvento(admin, borrador) }.isFailure)
        coVerify(exactly = 0) { operacion.actualizarEventos(any()) }
    }

    @Test
    fun descargaEdicionYCierreConservanElEstadoDelServidor() = runTest {
        val repo = OperacionRepository(db)
        coEvery { api.obtenerEventos(any()) } returns
            Response.success(listOf(remoto.copy(activo = false)))
        assertEquals(1, repo.descargarEventos())
        coVerify { operacion.actualizarEventos(match { !it.single().activo }) }
        coEvery { operacion.evento(evento.id) } returns evento.copy(activo = false)
        coEvery { api.actualizarEvento(any(), any()) } returns Response.success(remoto)
        repo.editarEvento(admin, evento.id, borrador)
        coVerify { api.actualizarEvento(evento.id, match { !it.activo }) }
        coEvery { api.cerrarEvento(evento.id) } returns
            Response.success(remoto.copy(activo = false))
        repo.cerrarEvento(admin, evento.id)
        coVerify { api.cerrarEvento(evento.id) }
        assertTrue(runCatching { repo.editarEvento(admin, evento.id, BorradorEvento()) }.isFailure)
    }

    @Test
    fun borrarSoloMarcaElCacheDespuesDeLaConfirmacionRemota() = runTest {
        val repo = OperacionRepository(db)
        coEvery { api.eliminarEvento(evento.id) } returns errorHttp(409)
        assertTrue(runCatching { repo.eliminarEvento(admin, evento.id) }.isFailure)
        coVerify(exactly = 0) { operacion.actualizarEventos(any()) }
        coEvery { api.eliminarEvento(evento.id) } returns Response.success(Unit)
        repo.eliminarEvento(admin, evento.id)
        coVerify {
            operacion.actualizarEventos(match { it.single().eliminado && !it.single().activo })
        }
        assertTrue(runCatching { repo.eliminarEvento(captador, evento.id) }.isFailure)
    }

    @Test
    fun unaDescargaFallidaNoReemplazaElCache() = runTest {
        val repo = OperacionRepository(db)
        coEvery { api.obtenerEventos(any()) } returns errorHttp(500)
        assertTrue(runCatching { repo.descargarEventos() }.isFailure)
        coEvery { api.obtenerEventos(any()) } returns Response.success(null)
        assertTrue(runCatching { repo.descargarEventos() }.isFailure)
        coVerify(exactly = 0) { operacion.actualizarEventos(any()) }
    }

    @Test
    fun unaGestionRespetaConsentimientoYSincronizacion() = runTest {
        val repo = OperacionRepository(db)
        coEvery { prospectos.obtenerPorId(1) } returns ficha()
        assertTrue(
            runCatching {
                    repo.guardarGestion(admin, 1, "CONTACTADO", "Correo", "Información enviada")
                }
                .isFailure
        )
        coEvery { prospectos.obtenerPorId(1) } returns
            ficha().copy(sincronizado = 1, autorizaContacto = false)
        assertTrue(
            runCatching {
                    repo.guardarGestion(admin, 1, "CONTACTADO", "Correo", "Información enviada")
                }
                .isFailure
        )
        assertTrue(
            runCatching { repo.guardarGestion(captador, 1, "CONTACTADO", "Otro", "Nota interna") }
                .isFailure
        )
        assertTrue(
            runCatching { repo.guardarGestion(admin, 1, "INVALIDO", "Otro", "Nota interna") }
                .isFailure
        )
        repo.guardarGestion(admin, 1, "CONTACTADO", "Otro", "  Nota interna  ")
        coVerify(exactly = 1) {
            operacion.insertarSeguimiento(
                match {
                    it.administrativoId == admin.id &&
                        it.observacion == "Nota interna" &&
                        it.estadoEnvio == "PENDIENTE"
                }
            )
        }
    }
}
