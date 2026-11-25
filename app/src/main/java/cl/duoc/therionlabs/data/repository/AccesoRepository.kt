package cl.duoc.therionlabs.data.repository

import android.content.Context
import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.*
import cl.duoc.therionlabs.worker.SyncScheduler
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AccesoRepository(
    private val contexto: Context,
    private val db: AppDatabase = AppDatabase.getDatabase(contexto),
    private val preferencias: android.content.SharedPreferences =
        contexto.getSharedPreferences("acceso_validado", Context.MODE_PRIVATE),
    private val tokens: DispositivoTokenStore = DispositivoTokenStore(contexto),
    private val conexion: () -> AccesoApi = { RetrofitClient.accesoApi },
    private val programar: () -> Unit = { SyncScheduler.solicitarSincronizacion(contexto) },
) {
    private val dao = db.operacionDao()

    suspend fun ingresar(nombre: String, clave: String): SesionUsuario =
        withContext(Dispatchers.IO) {
            val nombreNormalizado = nombre.trim().lowercase(Locale.ROOT)
            try {
                val respuesta = conexion().ingresar(SolicitudAcceso(nombreNormalizado, clave))
                if (!respuesta.isSuccessful)
                    throw IllegalArgumentException(
                        RespuestasServidor.mensaje(
                            respuesta.errorBody()?.string(),
                            respuesta.code(),
                        )
                    )
                val datos =
                    requireNotNull(respuesta.body()) { "El servidor no devolvió la cuenta." }
                val cuenta = datos.cuenta
                require(
                    cuenta.activo &&
                        cuenta.rol in listOf(Roles.ADMINISTRATIVO, Roles.CAPTADOR) &&
                        datos.token.isNotBlank() &&
                        datos.vence > System.currentTimeMillis()
                ) {
                    "La cuenta recibida no es válida."
                }
                val anterior = dao.usuario(nombreNormalizado)
                val localId = anterior?.id ?: cuenta.id
                val sal = ProtectorClave.generarSal()
                db.withTransaction {
                    dao.guardarUsuario(
                        UsuarioEntity(
                            localId,
                            nombreNormalizado,
                            cuenta.nombre,
                            cuenta.rol,
                            sal,
                            ProtectorClave.derivar(clave, sal),
                        )
                    )
                }
                if (cuenta.rol == Roles.CAPTADOR) {
                    require(
                        !cuenta.captadorId.isNullOrBlank() && !cuenta.codigoCaptador.isNullOrBlank()
                    ) {
                        "La cuenta no tiene captador asignado."
                    }
                    val codigoAnterior = tokens.obtenerCodigoCaptador(localId)
                    require(
                        codigoAnterior == null ||
                            codigoAnterior == cuenta.codigoCaptador ||
                            db.prospectoDao().contarPendientes(codigoAnterior) == 0
                    ) {
                        "La cuenta anterior tiene capturas pendientes. Sincronízalas antes de cambiar de identidad."
                    }
                    tokens.guardarVinculacion(
                        localId,
                        requireNotNull(datos.tokenCaptura),
                        cuenta.captadorId,
                        cuenta.codigoCaptador,
                    )
                }
                preferencias
                    .edit()
                    .putLong(
                        "hasta_$localId",
                        minOf(datos.vence, System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000),
                    )
                    .apply()
                ConfiguracionConexion.autorizar(datos.token, cuenta.rol)
                programar()
                SesionUsuario(localId, cuenta.nombre, cuenta.rol)
            } catch (error: IOException) {
                val cuenta = dao.usuario(nombreNormalizado)
                require(
                    cuenta != null &&
                        preferencias.getLong("hasta_${cuenta.id}", 0) >
                            System.currentTimeMillis() &&
                        ProtectorClave.coincide(clave, cuenta.salt, cuenta.passwordHash)
                ) {
                    "Sin conexión: ingresa con una cuenta validada en este teléfono durante los últimos 7 días. El primer acceso necesita internet."
                }
                ConfiguracionConexion.cerrarSesion()
                SesionUsuario(cuenta.id, cuenta.nombre, cuenta.rol)
            }
        }

    suspend fun salir(token: String) {
        if (token.isNotBlank()) runCatching { RetrofitClient.accesoApi.salir("Bearer $token") }
    }
}
