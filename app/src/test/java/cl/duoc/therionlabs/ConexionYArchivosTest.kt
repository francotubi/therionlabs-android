package cl.duoc.therionlabs

import android.content.ContentResolver
import android.net.Uri
import cl.duoc.therionlabs.domain.Roles
import cl.duoc.therionlabs.network.*
import io.mockk.*
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class ConexionYArchivosTest : EscenarioLocal() {
    private val direccionOriginal = ConfiguracionConexion.direccion

    @After
    fun restaurarDireccion() {
        every { preferencias.getString("direccion", any()) } returns direccionOriginal
        ConfiguracionConexion.inicializar(app)
    }

    @Test
    fun noPermiteMezclarDatosDeServidoresDistintos() = runTest {
        coEvery { prospectos.obtenerParaExportacion() } returns listOf(ficha())
        assertTrue(
            runCatching { ConfiguracionConexion.configurar(app, "https://otro.ejemplo.cl/") }
                .isFailure
        )
        assertEquals(direccionOriginal, ConfiguracionConexion.direccion)
        coEvery { prospectos.obtenerParaExportacion() } returns emptyList()
        coEvery { operacion.listaEventos() } returns emptyList()
        ConfiguracionConexion.configurar(app, "https://otro.ejemplo.cl")
        assertEquals("https://otro.ejemplo.cl/", ConfiguracionConexion.direccion)
        assertEquals("", ConfiguracionConexion.credencialSesion)
        verify { editor.putString("direccion", "https://otro.ejemplo.cl/") }
    }

    @Test
    fun retrofitAgregaLaSesionYRespetaUnaAutorizacionExplicita() = runTest {
        MockWebServer().use { servidor ->
            servidor.start()
            every { preferencias.getString("direccion", any()) } returns
                servidor.url("/").toString()
            ConfiguracionConexion.inicializar(app)
            ConfiguracionConexion.autorizar("sesion", Roles.ADMINISTRATIVO)
            servidor.enqueue(MockResponse().setBody("[]"))
            assertTrue(RetrofitClient.eventoApi.obtenerEventos().isSuccessful)
            assertEquals("Bearer sesion", servidor.takeRequest().getHeader("Authorization"))
            servidor.enqueue(MockResponse().setResponseCode(204))
            RetrofitClient.accesoApi.salir("Bearer explicita")
            assertEquals("Bearer explicita", servidor.takeRequest().getHeader("Authorization"))
            ConfiguracionConexion.cerrarSesion()
            servidor.enqueue(MockResponse().setBody("[]"))
            RetrofitClient.eventoApi.obtenerEventos()
            assertNull(servidor.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun descargarExcelCopiaLosBytesYConservaLosErroresRelevantes() = runTest {
        val api = mockk<ExcelApi>()
        val uri = mockk<Uri>()
        val resolver = mockk<ContentResolver>()
        val archivo = ByteArrayOutputStream()
        mockkObject(RetrofitClient)
        every { RetrofitClient.excelApi } returns api
        every { app.contentResolver } returns resolver
        every { resolver.openOutputStream(uri) } returns archivo
        val contenido = byteArrayOf(80, 75, 3, 4, 10, 20)
        coEvery { api.descargarExcel(any()) } returns
            Response.success(
                ResponseBody.create(MediaType.parse("application/octet-stream"), contenido)
            )
        ExcelDownloader.descargarYGuardar(app, evento.id, uri)
        assertArrayEquals(contenido, archivo.toByteArray())
        for (codigo in listOf(401, 403, 404, 500)) {
            coEvery { api.descargarExcel(any()) } returns errorHttp(codigo)
            assertTrue(
                runCatching { ExcelDownloader.descargarYGuardar(app, evento.id, uri) }.isFailure
            )
        }
        coEvery { api.descargarExcel(any()) } returns Response.success(null)
        assertTrue(runCatching { ExcelDownloader.descargarYGuardar(app, evento.id, uri) }.isFailure)
    }
}
