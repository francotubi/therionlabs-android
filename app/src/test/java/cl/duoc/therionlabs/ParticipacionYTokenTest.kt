package cl.duoc.therionlabs

import cl.duoc.therionlabs.network.*
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ParticipacionYTokenTest : EscenarioLocal() {
    @Test
    fun elAlmacenMantieneLaIdentidadYRetiraLaCredencialAnterior() {
        mockkObject(AlmacenCredenciales)
        every { AlmacenCredenciales.cifrar(any()) } answers { "cifrado:" + firstArg<String>() }
        every { AlmacenCredenciales.leer(any()) } answers
            {
                firstArg<String?>()?.removePrefix("cifrado:")
            }
        every { preferencias.getString(any(), null) } returns null
        val almacen = DispositivoTokenStore(app)
        almacen.guardarToken("cuenta", " token ")
        verify { editor.putString("device_token_cuenta", "cifrado:token") }
        every { preferencias.getString("captador_codigo_cuenta", null) } returns "CAP-01"
        almacen.guardarVinculacion("cuenta", "nuevo", "id-remoto", "CAP-02")
        verify { editor.remove("device_token_por_codigo_CAP-01") }
        verify { editor.putString("captador_id_cuenta", "id-remoto") }
        every { preferencias.getString("device_token_cuenta", null) } returns "cifrado:nuevo"
        every { preferencias.getString("device_token_por_codigo_CAP-02", null) } returns
            "cifrado:nuevo"
        every { preferencias.getString("captador_id_cuenta", null) } returns "id-remoto"
        assertEquals("nuevo", almacen.obtenerToken("cuenta"))
        assertEquals("nuevo", almacen.obtenerTokenPorCodigo("CAP-02"))
        assertEquals("id-remoto", almacen.obtenerCaptadorId("cuenta"))
        assertEquals("CAP-01", almacen.obtenerCodigoCaptador("cuenta"))
        almacen.eliminarToken("cuenta")
        verify { editor.remove("device_token_cuenta") }
        verify { editor.remove("captador_codigo_cuenta") }
        assertTrue(runCatching { almacen.guardarToken("", "token") }.isFailure)
        assertTrue(runCatching { almacen.guardarToken("cuenta", "") }.isFailure)
    }

    @Test
    fun verificaElTitularAntesDeParticiparEnElEvento() = runTest {
        val dispositivos = mockk<DispositivoApi>()
        val participaciones = mockk<ParticipacionApi>()
        mockkObject(RetrofitClient)
        every { RetrofitClient.dispositivoApi } returns dispositivos
        every { RetrofitClient.participacionApi } returns participaciones
        mockkConstructor(DispositivoTokenStore::class)
        every { anyConstructed<DispositivoTokenStore>().obtenerTokenPorCodigo("CAP-03") } returns
            "captura"
        val verificacion = VerificacionDispositivoResponse("dispositivo", "remoto", "CAP-03", true)
        coEvery { dispositivos.verificarDispositivo("captura") } returns
            Response.success(verificacion)
        coEvery { participaciones.unirseEvento("captura", "remoto", evento.id) } returns
            Response.success(ParticipacionResponse("union", true))
        val cliente = ParticipacionEventoClient(app)
        cliente.unirseEvento("CAP-03", evento.id)
        coVerify(exactly = 1) { participaciones.unirseEvento("captura", "remoto", evento.id) }
        coEvery { dispositivos.verificarDispositivo("captura") } returns
            Response.success(verificacion.copy(identificadorLocal = "CAP-99"))
        assertTrue(runCatching { cliente.unirseEvento("CAP-03", evento.id) }.isFailure)
        coVerify(exactly = 1) { participaciones.unirseEvento(any(), any(), any()) }
    }

    @Test
    fun losErroresTemporalesDeParticipacionPermitenReintentar() = runTest {
        val dispositivos = mockk<DispositivoApi>()
        val participaciones = mockk<ParticipacionApi>()
        mockkObject(RetrofitClient)
        every { RetrofitClient.dispositivoApi } returns dispositivos
        every { RetrofitClient.participacionApi } returns participaciones
        mockkConstructor(DispositivoTokenStore::class)
        every { anyConstructed<DispositivoTokenStore>().obtenerTokenPorCodigo(any()) } returns
            "captura"
        val cliente = ParticipacionEventoClient(app)
        coEvery { dispositivos.verificarDispositivo(any()) } returns errorHttp(503)
        assertTrue(
            runCatching { cliente.unirseEvento("CAP-03", evento.id) }.exceptionOrNull()
                is IOException
        )
        coEvery { dispositivos.verificarDispositivo(any()) } returns errorHttp(401)
        assertTrue(
            runCatching { cliente.unirseEvento("CAP-03", evento.id) }.exceptionOrNull()
                is IllegalArgumentException
        )
        coEvery { dispositivos.verificarDispositivo(any()) } returns
            Response.success(
                VerificacionDispositivoResponse("dispositivo", "remoto", "CAP-03", true)
            )
        coEvery { participaciones.unirseEvento(any(), any(), any()) } returns errorHttp(429)
        assertTrue(
            runCatching { cliente.unirseEvento("CAP-03", evento.id) }.exceptionOrNull()
                is IOException
        )
        coEvery { participaciones.unirseEvento(any(), any(), any()) } returns
            errorHttp(409, "El evento está cerrado")
        assertEquals(
            "El evento está cerrado",
            runCatching { cliente.unirseEvento("CAP-03", evento.id) }.exceptionOrNull()?.message,
        )
    }
}
