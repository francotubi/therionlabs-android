package cl.duoc.therionlabs.data.repository

import androidx.room.withTransaction
import cl.duoc.therionlabs.data.local.*
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.*
import java.time.Instant
import java.util.UUID

class AdministracionRepository(private val db: AppDatabase) {
    private suspend fun exigir(usuario: SesionUsuario) {
        require(db.operacionDao().usuarioPorId(usuario.id)?.rol == Roles.ADMINISTRATIVO) {
            "Solo administración puede realizar esta operación."
        }
        ConfiguracionConexion.exigirSesion()
    }

    suspend fun actualizarEvento(usuario: SesionUsuario, eventoId: String) {
        exigir(usuario)
        val datos = RespuestasServidor.exigir(RetrofitClient.administracionApi.prospectos(eventoId))
        for (remoto in datos) {
            guardar(remoto)
            if (!remoto.eliminado) descargarGestiones(usuario, remoto.id)
        }
    }

    suspend fun guardar(remoto: ProspectoRemoto) = db.withTransaction {
        UUID.fromString(remoto.id)
        val local = db.prospectoDao().porIdentificador(remoto.id)
        if (local != null && local.sincronizado == 0 && !remoto.eliminado) return@withTransaction
        db.prospectoDao().actualizar(remoto.aEntidad(local))
    }

    suspend fun descargarGestiones(usuario: SesionUsuario, identificador: String) {
        val datos =
            RespuestasServidor.exigir(RetrofitClient.administracionApi.seguimientos(identificador))
        db.withTransaction {
            val prospecto =
                db.prospectoDao().porIdentificador(identificador) ?: return@withTransaction
            for (remoto in datos) {
                val anterior = db.operacionDao().gestionPorIdentificador(remoto.id)
                db.operacionDao()
                    .actualizarGestion(
                        SeguimientoEntity(
                            id = anterior?.id ?: 0,
                            identificador = remoto.id,
                            prospectoId = prospecto.id,
                            administrativoId = usuario.id,
                            administrativoNombre = remoto.responsable,
                            estado = remoto.estado,
                            canal = remoto.canal,
                            observacion = remoto.observacion,
                            fecha = Instant.parse(remoto.fecha).toEpochMilli(),
                            estadoEnvio = "SINCRONIZADO",
                        )
                    )
            }
        }
    }

    suspend fun editar(usuario: SesionUsuario, datos: ProspectoEntity) {
        exigir(usuario)
        require(datos.sincronizado == 1) { "La ficha todavía no fue aceptada por el servidor." }
        guardar(
            RespuestasServidor.exigir(
                RetrofitClient.administracionApi.actualizar(
                    datos.identificador,
                    datos.toSyncRequest(),
                )
            )
        )
    }

    suspend fun eliminar(usuario: SesionUsuario, id: Int) {
        exigir(usuario)
        val datos = requireNotNull(db.prospectoDao().obtenerPorId(id))
        require(datos.sincronizado == 1) { "Sincroniza la ficha antes de eliminarla del servidor." }
        RespuestasServidor.confirmar(RetrofitClient.administracionApi.eliminar(datos.identificador))
        db.prospectoDao().actualizar(datos.copy(eliminado = true))
    }
}

fun ProspectoRemoto.aEntidad(local: ProspectoEntity? = null) =
    ProspectoEntity(
        id = local?.id ?: 0,
        identificador = id,
        eventoId = eventoId,
        eventoNombre = eventoNombre,
        captadorId = codigoCaptador,
        captador = captador,
        eliminado = eliminado,
        sincronizado = 1,
        rut = rut.orEmpty(),
        nombre = nombre.orEmpty(),
        apellidos = apellidos.orEmpty(),
        fechaNacimiento = fechaNacimiento.orEmpty(),
        celular = celular.orEmpty(),
        email = email.orEmpty(),
        establecimiento = establecimiento.orEmpty(),
        comunaResidencia = comunaResidencia.orEmpty(),
        comunaEstablecimiento = comunaEstablecimiento.orEmpty(),
        actividadActual = actividadActual.orEmpty(),
        jornada = jornada.orEmpty(),
        region = region.orEmpty(),
        sede = sede.orEmpty(),
        nivelInteres = nivelInteres.orEmpty(),
        carrera1 = carrera1.orEmpty(),
        carrera2 = carrera2,
        carrera3 = carrera3,
        modalidad = modalidad.orEmpty(),
        canalContacto = canalContacto.orEmpty(),
        observaciones = observaciones.orEmpty(),
        autorizaContacto = autorizaContacto,
        fechaCaptura = fechaCaptura ?: 0L,
        fotoRuta = local?.fotoRuta,
        latitud = latitud,
        longitud = longitud,
        precisionMetros = precisionMetros,
        fechaUbicacion = fechaUbicacion,
        direccion = direccion,
    )
