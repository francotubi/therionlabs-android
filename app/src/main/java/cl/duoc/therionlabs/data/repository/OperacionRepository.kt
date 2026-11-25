package cl.duoc.therionlabs.data.repository

import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.ConfiguracionConexion
import cl.duoc.therionlabs.network.CrearEventoRequest
import cl.duoc.therionlabs.network.RespuestasServidor
import cl.duoc.therionlabs.network.RetrofitClient
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.map

class OperacionRepository(private val db: AppDatabase) {
    private val dao = db.operacionDao()
    val eventos =
        dao.eventos().map { items -> items.filterNot { ReglasEvento.esDemostracion(it.id) } }
    val seguimientos = dao.seguimientos()

    suspend fun eliminarEvento(usuario: SesionUsuario, id: String) {
        exigirAdministracion(usuario)
        RespuestasServidor.confirmar(RetrofitClient.eventoApi.eliminarEvento(id))
        val evento = requireNotNull(dao.evento(id))
        dao.actualizarEventos(listOf(evento.copy(activo = false, eliminado = true)))
    }

    private suspend fun exigirAdministracion(usuario: SesionUsuario) {
        require(dao.usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO) {
            "Solo administración puede realizar esta operación."
        }
        ConfiguracionConexion.exigirSesion()
    }

    suspend fun cerrarEvento(usuario: SesionUsuario, id: String) {
        exigirAdministracion(usuario)
        RespuestasServidor.exigir(RetrofitClient.eventoApi.cerrarEvento(id))
        descargarEventos()
    }

    suspend fun editarEvento(usuario: SesionUsuario, id: String, borrador: BorradorEvento) {
        exigirAdministracion(usuario)
        require(ReglasEvento.validar(borrador).isEmpty()) { "Revisa los datos del evento." }
        val fecha =
            LocalDate.parse(borrador.fecha, DateTimeFormatter.ofPattern("dd/MM/uuuu")).toString()
        RespuestasServidor.exigir(
            RetrofitClient.eventoApi.actualizarEvento(
                id,
                CrearEventoRequest(
                    nombre = borrador.nombre.trim(),
                    colegio = borrador.colegio.trim(),
                    fecha = fecha,
                    actividad = borrador.actividad.trim(),
                    comuna = borrador.comuna,
                    activo = dao.evento(id)?.activo ?: true,
                ),
            )
        )
        descargarEventos()
    }

    suspend fun crearEvento(
        usuario: SesionUsuario,
        borrador: BorradorEvento,
    ): String {
        ConfiguracionConexion.exigirSesion()
        require(dao.usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO) {
            "Solo administración puede crear eventos."
        }

        require(ReglasEvento.validar(borrador).isEmpty()) {
            "Revisa los campos del evento."
        }

        val eventosExistentes = dao.listaEventos().filterNot { ReglasEvento.esDemostracion(it.id) }

        require(
            eventosExistentes.none {
                CatalogoRegional.normalizar(it.nombre) ==
                    CatalogoRegional.normalizar(borrador.nombre) &&
                    CatalogoRegional.normalizar(it.colegio) ==
                        CatalogoRegional.normalizar(borrador.colegio) &&
                    it.fecha == borrador.fecha
            }
        ) {
            "Ya existe ese evento para la misma institución y fecha."
        }
        val fechaServidor =
            LocalDate.parse(
                    borrador.fecha,
                    DateTimeFormatter.ofPattern("dd/MM/uuuu"),
                )
                .toString()

        val solicitud =
            CrearEventoRequest(
                nombre = borrador.nombre.trim(),
                colegio = borrador.colegio.trim(),
                fecha = fechaServidor,
                actividad = borrador.actividad.trim(),
                comuna = borrador.comuna.trim(),
            )
        val respuesta = RetrofitClient.eventoApi.crearEvento(solicitud)

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                "No se pudo publicar el evento. Código HTTP: ${respuesta.code()}"
            )
        }

        val eventoServidor =
            requireNotNull(respuesta.body()) {
                "El servidor no devolvió los datos del evento."
            }
        java.util.UUID.fromString(eventoServidor.id)
        db.withTransaction {
            dao.actualizarEventos(
                listOf(
                    EventoEntity(
                        id = eventoServidor.id,
                        nombre = eventoServidor.nombre,
                        colegio = eventoServidor.colegio,
                        fecha = borrador.fecha,
                        actividad = eventoServidor.actividad,
                        activo = eventoServidor.activo,
                        eliminado = eventoServidor.eliminado,
                        comuna = eventoServidor.comuna.orEmpty(),
                    )
                )
            )
        }

        return eventoServidor.id
    }

    suspend fun descargarEventos(): Int {
        val respuesta = RetrofitClient.eventoApi.obtenerEventos()

        if (!respuesta.isSuccessful) {
            throw IllegalArgumentException(
                "No se pudieron descargar los eventos. HTTP ${respuesta.code()}"
            )
        }

        val eventosServidor =
            requireNotNull(respuesta.body()) {
                "El servidor no devolvió la lista de eventos."
            }
        val eventosLocales = eventosServidor.map { evento ->
            EventoEntity(
                id = java.util.UUID.fromString(evento.id).toString(),
                nombre = evento.nombre,
                colegio = evento.colegio,
                fecha =
                    java.time.LocalDate.parse(evento.fecha)
                        .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")),
                actividad = evento.actividad,
                activo = evento.activo,
                eliminado = evento.eliminado,
                comuna = evento.comuna.orEmpty(),
            )
        }
        db.withTransaction {
            dao.actualizarEventos(eventosLocales)
        }

        return eventosLocales.size
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
        require(prospecto.sincronizado == 1) {
            "Sincroniza el prospecto antes de registrar gestiones."
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
