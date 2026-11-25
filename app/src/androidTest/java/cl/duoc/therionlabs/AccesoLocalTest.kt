package cl.duoc.therionlabs

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.data.repository.AccesoRepository
import cl.duoc.therionlabs.network.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class AccesoLocalTest {
    private val contexto: Context = ApplicationProvider.getApplicationContext()

    private class ApiPrueba : AccesoApi {
        var desconectado = false
        var rechazar = false
        var rol = "CAPTADOR"

        override suspend fun ingresar(datos: SolicitudAcceso): Response<RespuestaAcceso> {
            if (desconectado) throw IOException("Sin red")
            if (rechazar)
                return Response.error(
                    401,
                    ResponseBody.create(
                        MediaType.parse("application/json"),
                        "{\"mensaje\":\"Usuario o contraseña incorrectos\"}",
                    ),
                )
            return Response.success(
                RespuestaAcceso(
                    CuentaRemota(
                        "cuenta-remota",
                        datos.usuario,
                        "Cuenta de prueba",
                        rol,
                        if (rol == "CAPTADOR") "captador-remoto" else null,
                        if (rol == "CAPTADOR") "CAP-03" else null,
                        true,
                    ),
                    "sesion-activa",
                    System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000,
                    if (rol == "CAPTADOR") "credencial-captura" else null,
                )
            )
        }

        override suspend fun salir(autorizacion: String) = Response.success(Unit)

        override suspend fun cuentas() = Response.success(emptyList<CuentaRemota>())

        override suspend fun crear(datos: NuevaCuenta): Response<CuentaRemota> =
            throw UnsupportedOperationException()
    }

    @Test
    fun identificaCaptadorYNoUsaLaCacheAnteUnRechazoDelServidor() = runBlocking {
        val nombre = "acceso-prueba-${UUID.randomUUID()}"
        val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
        val tokens = DispositivoTokenStore(contexto, nombre)
        val db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        try {
            val api = ApiPrueba()
            val repo = AccesoRepository(contexto, db, prefs, tokens, { api }, {})
            val cuenta = repo.ingresar("captador", "ClaveDePrueba!")
            assertEquals("CAP-03", tokens.obtenerCodigoCaptador(cuenta.id))
            assertEquals("credencial-captura", tokens.obtenerTokenPorCodigo("CAP-03"))
            assertTrue(prefs.getString("device_token_${cuenta.id}", null)!!.startsWith("v1:"))
            api.rechazar = true
            try {
                repo.ingresar("captador", "ClaveDePrueba!")
                fail("No debe aceptar una cuenta rechazada por el servidor")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("incorrectos"))
            }
            api.desconectado = true
            assertEquals(cuenta, repo.ingresar("captador", "ClaveDePrueba!"))
            assertTrue(ConfiguracionConexion.credencialSesion.isBlank())
        } finally {
            db.close()
            prefs.edit().clear().commit()
            ConfiguracionConexion.cerrarSesion()
        }
    }

    @Test
    fun exigeUnPrimerAccesoConRedYLimitaElAccesoSinConexion() = runBlocking {
        val nombre = "acceso-prueba-${UUID.randomUUID()}"
        val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
        val db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        try {
            val api = ApiPrueba().apply { desconectado = true }
            val repo =
                AccesoRepository(
                    contexto,
                    db,
                    prefs,
                    DispositivoTokenStore(contexto, nombre),
                    { api },
                    {},
                )
            try {
                repo.ingresar("captador", "ClaveDePrueba!")
                fail()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("primer acceso"))
            }
            api.desconectado = false
            val cuenta = repo.ingresar("captador", "ClaveDePrueba!")
            prefs.edit().putLong("hasta_${cuenta.id}", System.currentTimeMillis() - 1).commit()
            api.desconectado = true
            try {
                repo.ingresar("captador", "ClaveDePrueba!")
                fail()
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.contains("7 días"))
            }
        } finally {
            db.close()
            prefs.edit().clear().commit()
            ConfiguracionConexion.cerrarSesion()
        }
    }

    @Test
    fun conservaElIdentificadorLocalAlValidarUnaCuentaAnterior() = runBlocking {
        val nombre = "acceso-prueba-${UUID.randomUUID()}"
        val prefs = contexto.getSharedPreferences(nombre, Context.MODE_PRIVATE)
        val db = Room.inMemoryDatabaseBuilder(contexto, AppDatabase::class.java).build()
        try {
            db.operacionDao()
                .guardarUsuario(
                    UsuarioEntity(
                        "ADM-01",
                        "administrativo",
                        "Administración",
                        "ADMINISTRATIVO",
                        "sal-anterior",
                        "resumen-anterior",
                    )
                )
            val api = ApiPrueba().apply { rol = "ADMINISTRATIVO" }
            val repo =
                AccesoRepository(
                    contexto,
                    db,
                    prefs,
                    DispositivoTokenStore(contexto, nombre),
                    { api },
                    {},
                )
            val cuenta = repo.ingresar("administrativo", "ClaveNueva!")
            assertEquals("ADM-01", cuenta.id)
            assertNotNull(db.operacionDao().usuarioPorId("ADM-01"))
            assertEquals("sesion-activa", ConfiguracionConexion.exigirSesion())
        } finally {
            db.close()
            prefs.edit().clear().commit()
            ConfiguracionConexion.cerrarSesion()
        }
    }

    @Test
    fun lasCredencialesSeCifranConVectoresDistintos() {
        val primero = AlmacenCredenciales.cifrar("credencial-de-prueba")
        val segundo = AlmacenCredenciales.cifrar("credencial-de-prueba")
        assertNotEquals(primero, segundo)
        assertEquals("credencial-de-prueba", AlmacenCredenciales.leer(primero))
        assertNull(AlmacenCredenciales.leer(primero.dropLast(5)))
    }
}
