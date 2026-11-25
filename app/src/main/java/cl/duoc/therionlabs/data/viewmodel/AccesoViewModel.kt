package cl.duoc.therionlabs.data.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.therionlabs.data.repository.AccesoRepository
import cl.duoc.therionlabs.domain.SesionUsuario
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccesoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AccesoRepository(app)
    private val _sesion = MutableStateFlow<SesionUsuario?>(null)
    val sesion = _sesion.asStateFlow()
    private val _ocupado = MutableStateFlow(false)
    val ocupado = _ocupado.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun ingresar(nombre: String, clave: String) {
        if (_ocupado.value) return
        if (nombre.isBlank() || clave.isBlank()) {
            _error.value = "Ingresa usuario y contraseña."
            return
        }
        _ocupado.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val usuario = repo.ingresar(nombre, clave)
                _sesion.value = usuario
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (_: Exception) {
                _error.value = "No fue posible abrir la sesión local. Vuelve a intentar."
            } finally {
                _ocupado.value = false
            }
        }
    }

    fun cerrarSesion() {
        val token = cl.duoc.therionlabs.network.ConfiguracionConexion.credencialSesion
        cl.duoc.therionlabs.network.ConfiguracionConexion.cerrarSesion()
        viewModelScope.launch { repo.salir(token) }
        _sesion.value = null
        _error.value = null
    }
}
