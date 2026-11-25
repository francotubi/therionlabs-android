package cl.duoc.therionlabs.data.repository

import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.util.ValidacionesProspecto
import java.util.Locale

class ProspectoRepository(
    private val db: AppDatabase,
    private val tokenStore: cl.duoc.therionlabs.network.DispositivoTokenStore? = null,
) {
    private val dao = db.prospectoDao()

    private fun obtenerIdentidadCaptador(usuarioId: String): Pair<String, String>? {

        val store = tokenStore ?: return null

        val captadorId = store.obtenerCaptadorId(usuarioId) ?: return null

        val codigoCaptador = store.obtenerCodigoCaptador(usuarioId) ?: return null

        return captadorId to codigoCaptador
    }

    fun registros() = dao.obtenerProspectos()

    suspend fun obtenerProspectosParaExcel(usuario: SesionUsuario): List<ProspectoEntity> =
        db.withTransaction {
            val usuarioActual = db.operacionDao().usuarioPorId(usuario.id)

            require(usuarioActual?.rol == Roles.ADMINISTRATIVO) {
                "Solo los administrativos pueden exportar prospectos."
            }

            dao.obtenerParaExportacion()
        }

    suspend fun guardarProspecto(usuario: SesionUsuario, prospecto: ProspectoEntity): Long =
        db.withTransaction {
            val responsable = requireNotNull(db.operacionDao().usuarioPorId(usuario.id))
            require(responsable.rol == Roles.CAPTADOR) {
                "Solo el captador puede registrar prospectos."
            }
            val event =
                requireNotNull(db.operacionDao().evento(prospecto.eventoId)) {
                    "Selecciona un evento disponible."
                }
            require(event.activo && !ReglasEvento.esDemostracion(event.id)) {
                "El evento no está disponible."
            }
            require(
                prospecto.region == CatalogoRegional.region &&
                    prospecto.sede in CatalogoRegional.sedes
            )
            val carreras = listOfNotNull(prospecto.carrera1, prospecto.carrera2, prospecto.carrera3)
            require(
                carreras.size in 1..3 &&
                    carreras.distinct().size == carreras.size &&
                    carreras.all { it in CatalogoRegional.carreras(prospecto.sede) }
            ) {
                "Revisa las carreras de la sede elegida."
            }
            require(
                ValidacionesProspecto.esRutValido(prospecto.rut) &&
                    ValidacionesProspecto.esCorreoValido(prospecto.email)
            )
            val rut =
                prospecto.rut
                    .filterNot { it == '.' || it == '-' || it.isWhitespace() }
                    .uppercase(Locale.ROOT)
            val email = prospecto.email.trim().lowercase(Locale.ROOT)
            if (dao.existeRut(event.id, rut))
                throw ProspectoDuplicadoException("Este RUT ya está registrado en el evento.")
            if (dao.existeCorreo(event.id, email))
                throw ProspectoDuplicadoException("Este correo ya está registrado en el evento.")

            val identidad = obtenerIdentidadCaptador(usuario.id)

            val codigoCaptador =
                if (tokenStore != null) {
                    requireNotNull(identidad?.second) {
                        "Primero debes vincular tu dispositivo " +
                            "con el token entregado por Administración."
                    }
                } else {
                    responsable.id
                }

            dao.insertarProspecto(
                prospecto.copy(
                    eventoNombre = event.nombre,
                    captadorId = codigoCaptador,
                    captador = codigoCaptador,
                    email = email,
                )
            )
        }

    suspend fun editarProspecto(usuario: SesionUsuario, datos: ProspectoEntity) {
        val anterior = requireNotNull(dao.obtenerPorId(datos.id))
        if (usuario.rol == Roles.ADMINISTRATIVO) {
            AdministracionRepository(db).editar(usuario, datos)
        } else {
            require(
                obtenerIdentidadCaptador(usuario.id)?.second == anterior.captadorId &&
                    anterior.sincronizado != 1
            ) {
                "Solo puedes corregir tus fichas pendientes o rechazadas."
            }
            db.withTransaction {
                val repetidos =
                    dao.obtenerParaExportacion().filter {
                        it.id != datos.id && it.eventoId == datos.eventoId
                    }
                val rut = datos.rut.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
                require(
                    repetidos.none {
                        it.rut.filter { c -> c.isLetterOrDigit() }.uppercase(Locale.ROOT) == rut ||
                            it.email.equals(datos.email.trim(), true)
                    }
                ) {
                    "Ya existe ese RUT o correo en el evento."
                }
                dao.actualizar(
                    datos.copy(
                        identificador = anterior.identificador,
                        captadorId = anterior.captadorId,
                        captador = anterior.captador,
                        eventoId = anterior.eventoId,
                        sincronizado = 0,
                        motivoRechazo = "",
                    )
                )
            }
        }
    }

    suspend fun borrarProspecto(usuario: SesionUsuario, id: Int) {
        val datos = requireNotNull(dao.obtenerPorId(id))
        require(datos.sincronizado != 0) {
            "Espera a que termine el envío antes de eliminar esta ficha."
        }
        if (datos.sincronizado == 1) AdministracionRepository(db).eliminar(usuario, id)
        else
            db.withTransaction {
                require(db.operacionDao().usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO)
                dao.borrarProspecto(id)
            }
    }
}
