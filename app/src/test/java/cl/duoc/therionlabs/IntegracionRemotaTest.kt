package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.repository.aEntidad
import cl.duoc.therionlabs.network.*
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class IntegracionRemotaTest {
    @Test
    fun normalizaLaDireccionSinAceptarCredencialesNiRutas() {
        assertEquals(
            "https://ejemplo.cl/",
            ConfiguracionConexion.normalizarDireccion(" https://ejemplo.cl "),
        )
        assertEquals(
            "http://10.0.2.2:8081/",
            ConfiguracionConexion.normalizarDireccion("http://10.0.2.2:8081/"),
        )
        for (valor in
            listOf(
                "ejemplo.cl",
                "ftp://ejemplo.cl",
                "https://usuario:clave@ejemplo.cl",
                "https://ejemplo.cl/api",
                "https://ejemplo.cl?clave=abc",
            )) {
            assertTrue(runCatching { ConfiguracionConexion.normalizarDireccion(valor) }.isFailure)
        }
    }

    @Test
    fun conservaLosMensajesDelServidorSinMostrarElCuerpoCompleto() {
        assertEquals(
            "RUT repetido",
            RespuestasServidor.mensaje("{\"mensaje\":\"RUT repetido\"}", 409),
        )
        assertEquals(
            "Evento eliminado",
            RespuestasServidor.mensaje("{\"message\":\"Evento eliminado\"}", 400),
        )
        assertEquals("Sin permiso", RespuestasServidor.mensaje("{\"detail\":\"Sin permiso\"}", 403))
        assertTrue(RespuestasServidor.mensaje("<html>Error</html>", 500).contains("HTTP 500"))
        assertTrue(RespuestasServidor.mensaje(null, 401).contains("credencial"))
    }

    @Test
    fun consultaYEditaUsandoElIdentificadorDelServidor() = runBlocking {
        MockWebServer().use { servidor ->
            servidor.start()
            val api =
                Retrofit.Builder()
                    .baseUrl(servidor.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(AdministracionApi::class.java)
            servidor.enqueue(MockResponse().setBody("[]"))
            assertTrue(RespuestasServidor.exigir(api.prospectos("evento-1")).isEmpty())
            val consulta = servidor.takeRequest()
            assertEquals("GET", consulta.method)
            assertEquals(
                "/api/admin/eventos/evento-1/prospectos?incluirEliminados=true",
                consulta.path,
            )
            servidor.enqueue(MockResponse().setResponseCode(204))
            RespuestasServidor.confirmar(api.eliminar("ficha-1"))
            assertEquals("DELETE", servidor.takeRequest().method)
            servidor.enqueue(
                MockResponse().setResponseCode(403).setBody("{\"mensaje\":\"Sin autorización\"}")
            )
            assertEquals(
                "Sin autorización",
                runCatching { RespuestasServidor.exigir(api.prospectos("evento-1")) }
                    .exceptionOrNull()
                    ?.message,
            )
        }
    }

    @Test
    fun laParticipacionEnviaTokenYLeeElCampoCorrecto() = runBlocking {
        MockWebServer().use { servidor ->
            servidor.start()
            val api =
                Retrofit.Builder()
                    .baseUrl(servidor.url("/"))
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ParticipacionApi::class.java)
            servidor.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody("{\"participacionId\":\"union-1\",\"activo\":true}")
            )
            val respuesta = api.unirseEvento("credencial-prueba", "captador-1", "evento-1")
            assertEquals("union-1", respuesta.body()?.participacionId)
            val peticion = servidor.takeRequest()
            assertEquals("credencial-prueba", peticion.getHeader("X-Device-Token"))
            assertTrue(peticion.path!!.contains("captadorId=captador-1"))
        }
    }

    @Test
    fun mapeaLaFichaSinConfundirRechazoConSincronizacion() {
        val remoto =
            Gson()
                .fromJson(
                    """{"id":"uuid-remoto","eventoId":"evento","eventoNombre":"Feria","nombre":"Persona","codigoCaptador":"CAP-03","captador":"Captador de prueba","eliminado":false} """,
                    ProspectoRemoto::class.java,
                )
        val ficha = remoto.aEntidad()
        assertEquals("uuid-remoto", ficha.identificador)
        assertEquals("CAP-03", ficha.captadorId)
        assertEquals(1, ficha.sincronizado)
        assertEquals(2, ficha.copy(sincronizado = 2).sincronizado)
        assertNull(ficha.fotoRuta)
        assertEquals("uuid-remoto", ficha.toSyncRequest().id)
    }

    @Test
    fun traduceLosCodigosMeteorologicos() {
        assertEquals("Despejado", ServicioClima.descripcion(0))
        assertEquals("Lluvia", ServicioClima.descripcion(61))
        assertEquals("Tormenta", ServicioClima.descripcion(95))
        assertEquals("Condición variable", ServicioClima.descripcion(-1))
    }
}
