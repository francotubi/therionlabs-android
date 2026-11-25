package cl.duoc.therionlabs.network

import cl.duoc.therionlabs.data.local.ProspectoEntity

fun ProspectoEntity.toSyncRequest(): ProspectoSyncRequest {
    return ProspectoSyncRequest(
        id = identificador,
        eventoId = eventoId,
        rut = rut,
        nombre = nombre,
        apellidos = apellidos,
        fechaNacimiento = fechaNacimiento,
        celular = celular,
        email = email,
        establecimiento = establecimiento,
        comunaResidencia = comunaResidencia,
        comunaEstablecimiento = comunaEstablecimiento,
        actividadActual = actividadActual,
        jornada = jornada,
        region = region,
        sede = sede,
        nivelInteres = nivelInteres,
        carrera1 = carrera1,
        carrera2 = carrera2,
        carrera3 = carrera3,
        modalidad = modalidad,
        canalContacto = canalContacto,
        observaciones = observaciones,
        autorizaContacto = autorizaContacto,
        fechaCaptura = fechaCaptura,
        fotoRuta = fotoRuta,
        latitud = latitud,
        longitud = longitud,
        precisionMetros = precisionMetros,
        fechaUbicacion = fechaUbicacion,
        direccion = direccion,
    )
}
