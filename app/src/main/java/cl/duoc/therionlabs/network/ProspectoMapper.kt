package cl.duoc.therionlabs.network

import cl.duoc.therionlabs.data.local.ProspectoEntity

fun ProspectoEntity.toSyncRequest(): ProspectoSyncRequest {
    return ProspectoSyncRequest(
        // UUID estable generado en Android
        id = identificador,
        eventoId = eventoId,

        // Datos personales
        rut = rut,
        nombre = nombre,
        apellidos = apellidos,
        fechaNacimiento = fechaNacimiento,
        celular = celular,
        email = email,

        // Datos académicos
        establecimiento = establecimiento,
        comunaResidencia = comunaResidencia,
        comunaEstablecimiento = comunaEstablecimiento,
        actividadActual = actividadActual,
        jornada = jornada,

        // Preferencias
        region = region,
        sede = sede,
        nivelInteres = nivelInteres,
        carrera1 = carrera1,
        carrera2 = carrera2,
        carrera3 = carrera3,
        modalidad = modalidad,
        canalContacto = canalContacto,

        // Información adicional
        observaciones = observaciones,
        autorizaContacto = autorizaContacto,
        fechaCaptura = fechaCaptura,

        // Datos opcionales
        fotoRuta = fotoRuta,
        latitud = latitud,
        longitud = longitud,
        precisionMetros = precisionMetros,
        fechaUbicacion = fechaUbicacion,
        direccion = direccion
    )
}
