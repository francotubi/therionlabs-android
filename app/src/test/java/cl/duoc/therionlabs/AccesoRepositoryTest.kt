package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.local.UsuarioEntity
import cl.duoc.therionlabs.data.repository.AccesoRepository
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AccesoRepositoryTest : EscenarioLocal() {
    private val api = mockk<AccesoApi>()
    private var programaciones = 0

    private fun repo() =
        AccesoRepository(app, db, preferencias, tokens, { api }, { programaciones++ })

    private fun respuesta(rol: String = Roles.CAPTADOR) =
        RespuestaAcceso(
            CuentaRemota(
                "cuenta-remota",
                "captador",
                "Persona",
                rol,
                "captador-remoto",
                "CAP-03",
                true,
            ),
            "token-sesion",
            System.currentTimeMillis() + 3600000L,
            "token-captura",
        )

    @Test
    fun autenticaYVinculaElCaptadorSinCopiarTokens() = runTest {
        coEvery { operacion.usuario("captador") } returns null
        coEvery { api.ingresar(any()) } returns Response.success(respuesta())
        val resultado = repo().ingresar(" CAPTADOR ", "ClavePrueba!")
        assertEquals("cuenta-remota", resultado.id)
        assertEquals(Roles.CAPTADOR, resultado.rol)
        coVerify { api.ingresar(SolicitudAcceso("captador", "ClavePrueba!")) }
        coVerify {
            operacion.guardarUsuario(
                match { it.passwordHash != "ClavePrueba!" && it.salt.isNotBlank() }
            )
        }
        verify {
            tokens.guardarVinculacion("cuenta-remota", "token-captura", "captador-remoto", "CAP-03")
        }
        assertEquals("token-sesion", ConfiguracionConexion.credencialSesion)
        assertEquals(1, programaciones)
    }

    @Test
    fun conservaElIdentificadorLocalAnteriorYAutorizaAdministracion() = runTest {
        coEvery { operacion.usuario(any()) } returns
            UsuarioEntity("ADM-01", "administrativo", "Admin", Roles.ADMINISTRATIVO, "sal", "hash")
        coEvery { api.ingresar(any()) } returns Response.success(respuesta(Roles.ADMINISTRATIVO))
        assertEquals("ADM-01", repo().ingresar("administrativo", "ClavePrueba!").id)
        assertEquals("token-sesion", ConfiguracionConexion.exigirSesion())
        verify(exactly = 0) { tokens.guardarVinculacion(any(), any(), any(), any()) }
    }

    @Test
    fun rechazaCuentasInactivasRespuestasVaciasYErroresHttp() = runTest {
        for (datos in
            listOf(
                respuesta().copy(token = ""),
                respuesta().copy(vence = 0),
                respuesta().copy(cuenta = respuesta().cuenta.copy(activo = false)),
                respuesta().copy(cuenta = respuesta().cuenta.copy(rol = "OTRO")),
            )) {
            coEvery { api.ingresar(any()) } returns Response.success(datos)
            assertTrue(runCatching { repo().ingresar("captador", "Clave") }.isFailure)
        }
        coEvery { api.ingresar(any()) } returns Response.success(null)
        assertTrue(runCatching { repo().ingresar("captador", "Clave") }.isFailure)
        coEvery { api.ingresar(any()) } returns errorHttp(401, "Credenciales incorrectas")
        assertEquals(
            "Credenciales incorrectas",
            runCatching { repo().ingresar("captador", "Clave") }.exceptionOrNull()?.message,
        )
        coVerify(exactly = 0) { operacion.guardarUsuario(any()) }
    }

    @Test
    fun unaCaidaDeRedPermiteLaCuentaValidadaPeroNoOtraClaveNiUnCacheVencido() = runTest {
        val sal = ProtectorClave.generarSal()
        val cuenta =
            UsuarioEntity(
                "cuenta-local",
                "captador",
                "Persona",
                Roles.CAPTADOR,
                sal,
                ProtectorClave.derivar("ClavePrueba!", sal),
            )
        coEvery { api.ingresar(any()) } throws IOException("Sin red")
        coEvery { operacion.usuario("captador") } returns cuenta
        every { preferencias.getLong("hasta_cuenta-local", 0L) } returns
            System.currentTimeMillis() + 60000L
        assertEquals("cuenta-local", repo().ingresar("captador", "ClavePrueba!").id)
        assertEquals("", ConfiguracionConexion.credencialSesion)
        assertTrue(runCatching { repo().ingresar("captador", "Incorrecta") }.isFailure)
        every { preferencias.getLong("hasta_cuenta-local", 0L) } returns 0L
        assertTrue(runCatching { repo().ingresar("captador", "ClavePrueba!") }.isFailure)
        coEvery { operacion.usuario("captador") } returns null
        assertTrue(runCatching { repo().ingresar("captador", "ClavePrueba!") }.isFailure)
    }

    @Test
    fun noCambiaLaIdentidadSiHayCapturasPendientes() = runTest {
        coEvery { operacion.usuario(any()) } returns null
        coEvery { api.ingresar(any()) } returns Response.success(respuesta())
        every { tokens.obtenerCodigoCaptador("cuenta-remota") } returns "CAP-02"
        coEvery { prospectos.contarPendientes("CAP-02") } returns 1
        assertTrue(
            runCatching { repo().ingresar("captador", "ClavePrueba!") }
                .exceptionOrNull()!!
                .message!!
                .contains("pendientes")
        )
        verify(exactly = 0) { tokens.guardarVinculacion(any(), any(), any(), any()) }
    }

    @Test
    fun elCierreNotificaAlServidorSoloConToken() = runTest {
        mockkObject(RetrofitClient)
        every { RetrofitClient.accesoApi } returns api
        coEvery { api.salir(any()) } returns Response.success(Unit)
        repo().salir("")
        coVerify(exactly = 0) { api.salir(any()) }
        repo().salir("sesion")
        coVerify { api.salir("Bearer sesion") }
        coEvery { api.salir(any()) } throws IOException("Sin red")
        repo().salir("sesion")
    }
}
