package cl.duoc.therionlabs.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.duoc.therionlabs.data.local.ProspectoEntity
import cl.duoc.therionlabs.data.model.ErroresFormularioProspectos
import cl.duoc.therionlabs.data.model.FormularioProspectos
import cl.duoc.therionlabs.domain.CatalogoRegional
import cl.duoc.therionlabs.util.ValidacionesProspecto
import java.util.Locale

@Composable
fun EditorFichaDialog(
    datos: ProspectoEntity,
    ocupado: Boolean,
    guardar: (ProspectoEntity, () -> Unit) -> Unit,
    cerrar: () -> Unit,
) {
    val inicial = remember(datos.id) { datos.aFormulario() }
    var formulario by rememberSaveable(datos.id) { mutableStateOf(inicial) }
    var paso by rememberSaveable(datos.id) { mutableIntStateOf(0) }
    var errores by remember { mutableStateOf(ErroresFormularioProspectos()) }
    var confirmar by remember { mutableStateOf(false) }
    fun salir() {
        if (!ocupado) {
            if (formulario != inicial) confirmar = true else cerrar()
        }
    }
    fun cambiar(nuevo: FormularioProspectos) {
        if (!ocupado)
            formulario =
                nuevo.copy(
                    carreras = nuevo.carreras.filter { it in CatalogoRegional.carreras(nuevo.sede) }
                )
    }
    Dialog(
        onDismissRequest = ::salir,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false),
    ) {
        BackHandler {
            if (!ocupado) {
                if (paso == 1) paso = 0 else salir()
            }
        }
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (paso == 0)
                FormScreenContent(
                    formulario,
                    errores,
                    ::cambiar,
                    {
                        errores = ValidacionesProspecto.validar(formulario)
                        if (!errores.hayErroresDatosPersonales) paso = 1
                    },
                    ::salir,
                )
            else
                SelectorSedeContent(
                    formulario,
                    errores,
                    ocupado,
                    null,
                    3,
                    formulario.carreras.size < 3,
                    true,
                    ::cambiar,
                    { carrera ->
                        cambiar(
                            formulario.copy(
                                carreras =
                                    if (carrera in formulario.carreras)
                                        formulario.carreras - carrera
                                    else (formulario.carreras + carrera).take(3)
                            )
                        )
                    },
                    { cambiar(formulario.copy(carreras = emptyList())) },
                    {
                        errores = ValidacionesProspecto.validar(formulario)
                        if (!errores.hayErrores) guardar(datos.conFormulario(formulario), cerrar)
                    },
                    { if (!ocupado) paso = 0 },
                    ::salir,
                )
        }
    }
    if (confirmar)
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("¿Descartar los cambios?") },
            text = { Text("La edición todavía no se ha guardado.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmar = false
                        cerrar()
                    }
                ) {
                    Text("Descartar")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmar = false }) { Text("Seguir editando") }
            },
        )
}

fun ProspectoEntity.aFormulario(): FormularioProspectos {
    val datos = this
    return FormularioProspectos(
        eventoId = eventoId,
        eventoNombre = eventoNombre,
        carreras = listOfNotNull(carrera1, carrera2, carrera3),
        rut = datos.rut,
        nombre = datos.nombre,
        apellidos = datos.apellidos,
        fechaNacimiento = datos.fechaNacimiento,
        celular = datos.celular,
        email = datos.email,
        establecimiento = datos.establecimiento,
        comunaResidencia = datos.comunaResidencia,
        comunaEstablecimiento = datos.comunaEstablecimiento,
        actividadActual = datos.actividadActual,
        jornada = datos.jornada,
        region = datos.region,
        sede = datos.sede,
        nivelInteres = datos.nivelInteres,
        modalidad = datos.modalidad,
        canalContacto = datos.canalContacto,
        observaciones = datos.observaciones,
        fotoRuta = datos.fotoRuta,
        latitud = datos.latitud,
        longitud = datos.longitud,
        precisionMetros = datos.precisionMetros,
        fechaUbicacion = datos.fechaUbicacion,
        direccion = datos.direccion,
        autorizaContacto = datos.autorizaContacto,
    )
}

fun ProspectoEntity.conFormulario(formulario: FormularioProspectos) =
    copy(
        rut =
            formulario.rut
                .filterNot { it == '.' || it == '-' || it.isWhitespace() }
                .uppercase(Locale.ROOT),
        celular = normalizarCelular(formulario.celular),
        email = formulario.email.trim().lowercase(Locale.ROOT),
        carrera1 = formulario.carreras.first(),
        carrera2 = formulario.carreras.getOrNull(1),
        carrera3 = formulario.carreras.getOrNull(2),
        nombre = formulario.nombre,
        apellidos = formulario.apellidos,
        fechaNacimiento = formulario.fechaNacimiento,
        establecimiento = formulario.establecimiento,
        comunaResidencia = formulario.comunaResidencia,
        comunaEstablecimiento = formulario.comunaEstablecimiento,
        actividadActual = formulario.actividadActual,
        jornada = formulario.jornada,
        region = formulario.region,
        sede = formulario.sede,
        nivelInteres = formulario.nivelInteres,
        modalidad = formulario.modalidad,
        canalContacto = formulario.canalContacto,
        observaciones = formulario.observaciones,
        fotoRuta = formulario.fotoRuta,
        latitud = formulario.latitud,
        longitud = formulario.longitud,
        precisionMetros = formulario.precisionMetros,
        fechaUbicacion = formulario.fechaUbicacion,
        direccion = formulario.direccion,
        autorizaContacto = formulario.autorizaContacto,
    )

private fun normalizarCelular(valor: String): String {
    val digitos = valor.filter { it.isDigit() }
    return if (digitos.length == 9) "+56$digitos" else "+$digitos"
}
