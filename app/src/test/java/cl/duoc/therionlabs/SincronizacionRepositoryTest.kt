package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.local.SeguimientoEntity
import cl.duoc.therionlabs.data.repository.SincronizacionRepository
import cl.duoc.therionlabs.domain.Roles
import cl.duoc.therionlabs.network.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SincronizacionRepositoryTest : EscenarioLocal() {
    private val api = mockk<ProspectoApi>()
    private val administracion = mockk<AdministracionApi>()

    @Before
    fun prepararSincronizacion() {
        mockkObject(RetrofitClient)
        every { RetrofitClient.prospectoApi } returns api
        every { RetrofitClient.administracionApi } returns administracion
        mockkConstructor(DispositivoTokenStore::class, ParticipacionEventoClient::class)
        every { anyConstructed<DispositivoTokenStore>().obtenerTokenPorCodigo("CAP-03") } returns
            "captura"
        coEvery { anyConstructed<ParticipacionEventoClient>().unirseEvento(any(), any()) } just Runs
        coEvery { prospectos.obtenerPendientesSincronizacion() } returns listOf(ficha())
    }

    @Test
    fun confirmaSoloElUuidAceptadoYReintentaRespuestasIncompletas() = runTest {
        val repo = SincronizacionRepository(app)
        coEvery { api.sincronizarProspecto(any(), any()) } returns
            Response.success(RespuestaSync(ficha().identificador, "SINCRONIZADO", "OK"))
        assertTrue(repo.enviarProspectos())
        coVerify(exactly = 1) { prospectos.marcarComoSincronizado(1, ficha().identificador) }
        coEvery { api.sincronizarProspecto(any(), any()) } returns
            Response.success(RespuestaSync("otro", "SINCRONIZADO", "OK"))
        assertFalse(repo.enviarProspectos())
        coVerify(exactly = 1) { prospectos.marcarComoSincronizado(any(), any()) }
    }

    @Test
    fun diferenciaRechazosDefinitivosDeErroresTemporales() = runTest {
        val repo = SincronizacionRepository(app)
        for (codigo in listOf(400, 404, 409, 422)) {
            coEvery { api.sincronizarProspecto(any(), any()) } returns
                errorHttp(codigo, "Corregir dato")
            assertTrue(repo.enviarProspectos())
        }
        coVerify(exactly = 4) { prospectos.rechazar(1, "Corregir dato") }
        for (codigo in listOf(429, 500, 503)) {
            coEvery { api.sincronizarProspecto(any(), any()) } returns errorHttp(codigo)
            assertFalse(repo.enviarProspectos())
        }
        coEvery { api.sincronizarProspecto(any(), any()) } throws IOException("Sin red")
        assertFalse(repo.enviarProspectos())
        coEvery { api.sincronizarProspecto(any(), any()) } returns errorHttp(401)
        assertTrue(repo.enviarProspectos())
        coVerify(exactly = 0) { prospectos.marcarComoSincronizado(any(), any()) }
    }

    @Test
    fun rechazaUnaCapturaDeEventoCerradoSinDescartarla() = runTest {
        coEvery { anyConstructed<ParticipacionEventoClient>().unirseEvento(any(), any()) } throws
            IllegalArgumentException("El evento está cerrado")
        assertTrue(SincronizacionRepository(app).enviarProspectos())
        coVerify { prospectos.rechazar(1, "El evento está cerrado") }
        coVerify(exactly = 0) { api.sincronizarProspecto(any(), any()) }
    }

    @Test
    fun conservaPendientesSinCredencialDeCaptura() = runTest {
        every { anyConstructed<DispositivoTokenStore>().obtenerTokenPorCodigo(any()) } returns null
        every { anyConstructed<DispositivoTokenStore>().obtenerToken(any()) } returns null
        assertTrue(SincronizacionRepository(app).enviarProspectos())
        coVerify(exactly = 0) { api.sincronizarProspecto(any(), any()) }
        coVerify(exactly = 0) { prospectos.rechazar(any(), any()) }
    }

    @Test
    fun sincronizaGestionesSoloConSesionAdministrativaYConfirmacionCorrecta() = runTest {
        val repo = SincronizacionRepository(app)
        val gestion =
            SeguimientoEntity(
                prospectoId = 1,
                administrativoId = admin.id,
                administrativoNombre = admin.nombre,
                estado = "CONTACTADO",
                canal = "Correo",
                observacion = "Información enviada",
                fecha = 1L,
            )
        coEvery { operacion.gestionesPendientes() } returns listOf(gestion)
        coEvery { prospectos.obtenerPorId(1) } returns ficha().copy(sincronizado = 1)
        val respuesta =
            SeguimientoRemoto(
                gestion.identificador,
                ficha().identificador,
                admin.nombre,
                gestion.estado,
                gestion.canal,
                gestion.observacion,
                "2026-11-25T10:00:00Z",
            )
        coEvery { administracion.guardarGestion(any(), any()) } returns Response.success(respuesta)
        assertTrue(repo.enviarGestiones())
        coVerify(exactly = 1) { operacion.confirmarGestion(gestion.identificador) }
        coEvery { administracion.guardarGestion(any(), any()) } returns
            Response.success(respuesta.copy(id = "otro"))
        assertFalse(repo.enviarGestiones())
        coEvery { administracion.guardarGestion(any(), any()) } throws IOException("Sin red")
        assertFalse(repo.enviarGestiones())
        ConfiguracionConexion.autorizar("captador", Roles.CAPTADOR)
        assertTrue(repo.enviarGestiones())
        ConfiguracionConexion.cerrarSesion()
        assertTrue(repo.enviarGestiones())
        coVerify(exactly = 1) { operacion.confirmarGestion(any()) }
    }
}
