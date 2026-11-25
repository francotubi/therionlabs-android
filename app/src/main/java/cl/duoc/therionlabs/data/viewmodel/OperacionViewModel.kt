package cl.duoc.therionlabs.data.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import cl.duoc.therionlabs.data.local.AppDatabase
import cl.duoc.therionlabs.data.local.EventoEntity
import cl.duoc.therionlabs.data.repository.AdministracionRepository
import cl.duoc.therionlabs.data.repository.OperacionRepository
import cl.duoc.therionlabs.data.repository.SincronizacionRepository
import cl.duoc.therionlabs.domain.*
import cl.duoc.therionlabs.network.ParticipacionEventoClient
import cl.duoc.therionlabs.worker.SyncScheduler
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class OperacionViewModel(aplicacion: Application, private val guardado: SavedStateHandle) :
    AndroidViewModel(aplicacion) {
    private val repositorio = OperacionRepository(AppDatabase.getDatabase(aplicacion))
    private val usuario = MutableStateFlow<SesionUsuario?>(null)
    private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje = _mensaje.asStateFlow()
    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    private val _erroresEvento = MutableStateFlow<Map<String, String>>(emptyMap())
    val erroresEvento = _erroresEvento.asStateFlow()
    val eventoEditando = guardado.getStateFlow("eventoEditando", "")
    val borradorEvento = guardado.getStateFlow("evento", BorradorEvento())
    val eventos =
        repositorio.eventos
            .map { eventos ->
                eventos.sortedWith(
                    compareBy(
                        { evento ->
                            runCatching { LocalDate.parse(evento.fecha, formatoFecha) }
                                .getOrDefault(LocalDate.MIN)
                        },
                        { it.nombre },
                    )
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                _mensaje.value = "No se pudieron cargar los eventos."
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val gestiones =
        combine(repositorio.seguimientos, usuario) { gestiones, actual ->
                if (PermisosPerfil.puedeAdministrar(actual)) gestiones else emptyList()
            }
            .catch { error ->
                if (error is CancellationException) throw error
                _mensaje.value = "No se pudo cargar el seguimiento."
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun establecerUsuario(actual: SesionUsuario?) {
        if (usuario.value?.id != actual?.id) {
            if (guardado.get<String>("responsable") != actual?.id) {
                limpiarEvento()
            }

            usuario.value = actual
            guardado["responsable"] = actual?.id
            _mensaje.value = null
            if (actual != null) {
                viewModelScope.launch {
                    try {
                        repositorio.descargarEventos()
                    } catch (cancelacion: CancellationException) {
                        throw cancelacion
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun registrarParticipacion(evento: EventoEntity) {
        val actual = usuario.value ?: return

        if (!PermisosPerfil.puedeCapturar(actual)) return

        viewModelScope.launch {
            try {
                val cliente = ParticipacionEventoClient(getApplication())

                cliente.unirseEvento(
                    usuarioId = actual.id,
                    eventoId = evento.id,
                )
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: Exception) {}
        }
    }

    fun comenzarEdicion(evento: EventoEntity) {
        guardado["eventoEditando"] = evento.id
        guardado["evento"] =
            BorradorEvento(
                evento.nombre,
                evento.colegio,
                evento.fecha,
                evento.comuna,
                evento.actividad,
            )
    }

    fun refrescar(eventoId: String? = null) {
        val actual = usuario.value ?: return
        ejecutar("Datos actualizados.") {
            repositorio.descargarEventos()
            if (
                eventoId != null && eventoId.isNotBlank() && PermisosPerfil.puedeAdministrar(actual)
            ) {
                SincronizacionRepository(getApplication()).enviarGestiones()
                AdministracionRepository(AppDatabase.getDatabase(getApplication()))
                    .actualizarEvento(actual, eventoId)
            }
        }
    }

    fun actualizarEvento(cambio: (BorradorEvento) -> BorradorEvento) {
        if (_ocupado.value || !PermisosPerfil.puedeAdministrar(usuario.value)) return
        guardado["evento"] = cambio(borradorEvento.value)
        if (_erroresEvento.value.isNotEmpty())
            _erroresEvento.value = ReglasEvento.validar(borradorEvento.value)
    }

    fun limpiarEvento() {
        guardado["eventoEditando"] = ""
        guardado["evento"] = BorradorEvento()
        _erroresEvento.value = emptyMap()
    }

    fun limpiarMensaje() {
        _mensaje.value = null
    }

    fun crearEvento(terminar: () -> Unit) {
        val actual = usuario.value ?: return
        if (_ocupado.value || !PermisosPerfil.puedeAdministrar(actual)) return
        val datos = borradorEvento.value
        _erroresEvento.value = ReglasEvento.validar(datos)
        if (_erroresEvento.value.isNotEmpty()) return
        ejecutar("Evento creado. Ya está disponible para captación.") {
            if (eventoEditando.value.isBlank()) repositorio.crearEvento(actual, datos)
            else repositorio.editarEvento(actual, eventoEditando.value, datos)
            limpiarEvento()
            terminar()
        }
    }

    fun eliminarEvento(id: String) {
        val actual = usuario.value ?: return
        if (!PermisosPerfil.puedeAdministrar(actual)) return
        ejecutar("Evento eliminado.") { repositorio.eliminarEvento(actual, id) }
    }

    fun guardarGestion(id: Int, estado: String, canal: String, nota: String, terminar: () -> Unit) {
        val actual = usuario.value ?: return
        if (!PermisosPerfil.puedeAdministrar(actual)) return
        ejecutar("Gestión guardada en el dispositivo. Se enviará al servidor.") {
            repositorio.guardarGestion(actual, id, estado, canal, nota)
            SyncScheduler.solicitarSincronizacion(getApplication())
            terminar()
        }
    }

    private fun ejecutar(confirmacion: String, accion: suspend () -> Unit) {
        if (_ocupado.value) return
        _ocupado.value = true
        viewModelScope.launch {
            try {
                accion()
                _mensaje.value = confirmacion
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (error: IllegalArgumentException) {
                _mensaje.value = error.message ?: "Revisa los datos ingresados."
            } catch (_: Exception) {
                _mensaje.value = "No se pudo completar la operación. Inténtalo nuevamente."
            } finally {
                _ocupado.value = false
            }
        }
    }
}
