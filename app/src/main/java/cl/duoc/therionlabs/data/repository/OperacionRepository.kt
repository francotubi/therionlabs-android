package cl.duoc.therionlabs.data.repository

import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import cl.duoc.therionlabs.network.CrearEventoRequest
import cl.duoc.therionlabs.network.RetrofitClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter


class OperacionRepository(private val db: AppDatabase) {
    private val dao = db.operacionDao()
    val eventos =
        dao.eventos().map { items -> items.filterNot { ReglasEvento.esDemostracion(it.id) } }
    val seguimientos = dao.seguimientos()

    suspend fun inicializar() =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                if (dao.totalUsuarios() == 0) {
                    val cuentas =
                        listOf(
                                listOf(
                                    "CAP-01",
                                    "captador",
                                    "Captador de terreno",
                                    Roles.CAPTADOR,
                                    "Captador2026!",
                                ),
                                listOf(
                                    "CAP-02",
                                    "captador2",
                                    "Segundo captador",
                                    Roles.CAPTADOR,
                                    "Captador2026!",
                                ),
                                listOf(
                                    "ADM-01",
                                    "administrativo",
                                    "Administración de prospectos",
                                    Roles.ADMINISTRATIVO,
                                    "Admin2026!",
                                ),
                            )
                            .map { fila ->
                                val sal = ProtectorClave.generarSal()
                                UsuarioEntity(
                                    fila[0],
                                    fila[1],
                                    fila[2],
                                    fila[3],
                                    sal,
                                    ProtectorClave.derivar(fila[4], sal),
                                )
                            }
                    dao.insertarUsuarios(cuentas)
                }
            }
        }

    suspend fun eliminarEvento(usuario: SesionUsuario, id: String) = db.withTransaction {
        require(dao.usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO) {
            "Solo administración puede eliminar eventos."
        }
        requireNotNull(dao.evento(id)) { "El evento ya no existe." }
        require(dao.eliminarEventoVacio(id) == 1) {
            "No se puede eliminar un evento con prospectos registrados."
        }
    }


    suspend fun crearEvento(
        usuario: SesionUsuario,
        borrador: BorradorEvento
    ): String {
        require(dao.usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO) {
            "Solo administración puede crear eventos."
        }

        require(ReglasEvento.validar(borrador).isEmpty()) {
            "Revisa los campos del evento."
        }

        val eventosExistentes = dao.listaEventos()
            .filterNot { ReglasEvento.esDemostracion(it.id) }

        require(eventosExistentes.none {
            CatalogoRegional.normalizar(it.nombre) ==
                    CatalogoRegional.normalizar(borrador.nombre) &&
                    CatalogoRegional.normalizar(it.colegio) ==
                    CatalogoRegional.normalizar(borrador.colegio) &&
                    it.fecha == borrador.fecha
        }) {
            "Ya existe ese evento para la misma institución y fecha."
        }

        // Convertir dd/MM/yyyy a yyyy-MM-dd
        val fechaServidor = LocalDate.parse(
            borrador.fecha,
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
        ).toString()

        val solicitud = CrearEventoRequest(
            nombre = borrador.nombre.trim(),
            colegio = borrador.colegio.trim(),
            fecha = fechaServidor,
            actividad = borrador.actividad.trim(),
            comuna = borrador.comuna.trim()
        )

        // Primero publicar el evento en Spring Boot
        val respuesta = RetrofitClient.eventoApi.crearEvento(solicitud)

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                "No se pudo publicar el evento. Código HTTP: ${respuesta.code()}"
            )
        }

        val eventoServidor = requireNotNull(respuesta.body()) {
            "El servidor no devolvió los datos del evento."
        }

        // Comprobar que el backend devolvió un UUID válido
        java.util.UUID.fromString(eventoServidor.id)

        // Guardar en Room con el mismo UUID de MySQL
        db.withTransaction {
            dao.insertarEventos(
                listOf(
                    EventoEntity(
                        id = eventoServidor.id,
                        nombre = eventoServidor.nombre,
                        colegio = eventoServidor.colegio,
                        fecha = borrador.fecha,
                        actividad = eventoServidor.actividad,
                        activo = eventoServidor.activo,
                        comuna = eventoServidor.comuna.orEmpty()
                    )
                )
            )
        }

        return eventoServidor.id
    }

    suspend fun descargarEventos(): Int {
        // Consultar eventos publicados en Spring Boot
        val respuesta = RetrofitClient.eventoApi.obtenerEventos()

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                "No se pudieron descargar los eventos. HTTP ${respuesta.code()}"
            )
        }

        val eventosServidor = requireNotNull(respuesta.body()) {
            "El servidor no devolvió la lista de eventos."
        }

        // Convertir eventos del backend a entidades Room
        val eventosLocales = eventosServidor.map { evento ->
            EventoEntity(
                id = java.util.UUID.fromString(evento.id).toString(),
                nombre = evento.nombre,
                colegio = evento.colegio,
                fecha = java.time.LocalDate.parse(evento.fecha)
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("dd/MM/uuuu")
                    ),
                actividad = evento.actividad,
                activo = evento.activo,
                comuna = evento.comuna.orEmpty()
            )
        }

        // Guardar los eventos sin duplicar UUID existentes
        db.withTransaction {
            dao.insertarEventos(eventosLocales)
        }

        return eventosLocales.size
    }


    suspend fun ingresar(nombre: String, clave: String): SesionUsuario? =
        withContext(Dispatchers.IO) {
            val usuario = dao.usuario(nombre.trim().lowercase(Locale.ROOT))
            if (
                usuario != null &&
                    ProtectorClave.coincide(clave, usuario.salt, usuario.passwordHash)
            )
                SesionUsuario(usuario.id, usuario.nombre, usuario.rol)
            else null
        }

    suspend fun guardarGestion(
        usuario: SesionUsuario,
        prospectoId: Int,
        estado: String,
        canal: String,
        observacion: String,
    ) = db.withTransaction {
        val cuentaGuardada = requireNotNull(dao.usuarioPorId(usuario.id))
        require(cuentaGuardada.rol == Roles.ADMINISTRATIVO) {
            "Solo el perfil administrativo puede registrar gestiones."
        }
        val prospecto =
            requireNotNull(db.prospectoDao().obtenerPorId(prospectoId)) {
                "El prospecto ya no está disponible."
            }
        require(prospecto.autorizaContacto || canal == "Otro") {
            "Sin autorización de contacto, registra solo una gestión interna con canal Otro."
        }
        val error = ReglasSeguimiento.validar(estado, canal, observacion)
        require(error == null) { error.orEmpty() }
        dao.insertarSeguimiento(
            SeguimientoEntity(
                prospectoId = prospectoId,
                administrativoId = usuario.id,
                administrativoNombre = cuentaGuardada.nombre,
                estado = estado,
                canal = canal,
                observacion = observacion.trim(),
                fecha = System.currentTimeMillis(),
            )
        )
    }
}
