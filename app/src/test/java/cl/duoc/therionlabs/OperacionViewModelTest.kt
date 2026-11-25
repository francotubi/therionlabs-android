package cl.duoc.therionlabs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import cl.duoc.therionlabs.data.local.SeguimientoEntity
import cl.duoc.therionlabs.data.repository.*
import cl.duoc.therionlabs.data.viewmodel.OperacionViewModel
import cl.duoc.therionlabs.domain.BorradorEvento
import cl.duoc.therionlabs.network.ParticipacionEventoClient
import cl.duoc.therionlabs.worker.SyncScheduler
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperacionViewModelTest : EscenarioLocal() {
    private val modelos = ViewModelStore()

    @Before
    fun prepararModelo() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkConstructor(
            OperacionRepository::class,
            AdministracionRepository::class,
            SincronizacionRepository::class,
            ParticipacionEventoClient::class,
        )
        every { anyConstructed<OperacionRepository>().eventos } returns eventos
        every { anyConstructed<OperacionRepository>().seguimientos } returns gestiones
        coEvery { anyConstructed<OperacionRepository>().descargarEventos() } returns 1
        mockkObject(SyncScheduler)
        every { SyncScheduler.solicitarSincronizacion(any()) } just Runs
    }

    @After
    fun restaurarDispatcher() {
        modelos.clear()
        Dispatchers.resetMain()
    }

    private fun modelo() =
        OperacionViewModel(app, SavedStateHandle()).also { modelos.put("operacion", it) }

    private fun borrador() =
        BorradorEvento(evento.nombre, evento.colegio, evento.fecha, evento.comuna, evento.actividad)

    @Test
    fun creaYEditaConValidacionDeCamposYConfirmacion() = runTest {
        val vm = modelo()
        vm.establecerUsuario(admin)
        vm.crearEvento {}
        assertFalse(vm.erroresEvento.value.isEmpty())
        vm.actualizarEvento { borrador() }
        assertTrue(vm.erroresEvento.value.isEmpty())
        var terminado = false
        coEvery { anyConstructed<OperacionRepository>().crearEvento(admin, any()) } returns
            evento.id
        vm.crearEvento { terminado = true }
        assertTrue(terminado)
        assertTrue(vm.mensaje.value!!.contains("creado"))
        assertEquals(BorradorEvento(), vm.borradorEvento.value)
        vm.comenzarEdicion(evento)
        assertEquals(evento.id, vm.eventoEditando.value)
        coEvery { anyConstructed<OperacionRepository>().editarEvento(admin, evento.id, any()) } just
            Runs
        vm.crearEvento {}
        coVerify {
            anyConstructed<OperacionRepository>().editarEvento(admin, evento.id, borrador())
        }
        vm.limpiarMensaje()
        assertNull(vm.mensaje.value)
    }

    @Test
    fun elCaptadorNoPuedeModificarEventosNiGuardarGestiones() = runTest {
        val vm = modelo()
        vm.crearEvento {}
        vm.establecerUsuario(captador)
        vm.actualizarEvento { borrador() }
        vm.crearEvento {}
        vm.cerrarEvento(evento.id)
        vm.eliminarEvento(evento.id)
        vm.guardarGestion(1, "CONTACTADO", "Correo", "Información enviada") {}
        assertEquals(BorradorEvento(), vm.borradorEvento.value)
        coVerify(exactly = 0) { anyConstructed<OperacionRepository>().crearEvento(any(), any()) }
        coVerify(exactly = 0) { anyConstructed<OperacionRepository>().cerrarEvento(any(), any()) }
    }

    @Test
    fun cierreBajaYGestionMuestranElResultadoYRestauranElEstado() = runTest {
        val vm = modelo()
        vm.establecerUsuario(admin)
        coEvery { anyConstructed<OperacionRepository>().cerrarEvento(admin, evento.id) } just Runs
        vm.cerrarEvento(evento.id)
        assertTrue(vm.mensaje.value!!.contains("cerrado"))
        coEvery { anyConstructed<OperacionRepository>().eliminarEvento(admin, evento.id) } throws
            IllegalArgumentException("Tiene prospectos")
        vm.eliminarEvento(evento.id)
        assertEquals("Tiene prospectos", vm.mensaje.value)
        coEvery { anyConstructed<OperacionRepository>().eliminarEvento(admin, evento.id) } throws
            IOException("Sin red")
        vm.eliminarEvento(evento.id)
        assertTrue(vm.mensaje.value!!.contains("Inténtalo"))
        coEvery { anyConstructed<OperacionRepository>().eliminarEvento(admin, evento.id) } just Runs
        vm.eliminarEvento(evento.id)
        assertEquals("Evento eliminado.", vm.mensaje.value)
        coEvery {
            anyConstructed<OperacionRepository>().guardarGestion(any(), any(), any(), any(), any())
        } returns 1L
        var terminado = false
        vm.guardarGestion(1, "CONTACTADO", "Correo", "Información enviada") { terminado = true }
        assertTrue(terminado)
        verify { SyncScheduler.solicitarSincronizacion(app) }
        assertFalse(vm.ocupado.value)
    }

    @Test
    fun refrescaFichasAdministrativasYRegistraLaParticipacionDelCaptador() = runTest {
        val vm = modelo()
        vm.establecerUsuario(admin)
        coEvery { anyConstructed<SincronizacionRepository>().enviarGestiones() } returns true
        coEvery {
            anyConstructed<AdministracionRepository>().actualizarEvento(admin, evento.id)
        } just Runs
        vm.refrescar(evento.id)
        assertEquals("Datos actualizados.", vm.mensaje.value)
        coVerify { anyConstructed<AdministracionRepository>().actualizarEvento(admin, evento.id) }
        vm.establecerUsuario(captador)
        coEvery {
            anyConstructed<ParticipacionEventoClient>().unirseEvento(captador.id, evento.id)
        } just Runs
        vm.registrarParticipacion(evento)
        coVerify {
            anyConstructed<ParticipacionEventoClient>().unirseEvento(captador.id, evento.id)
        }
        vm.refrescar()
        vm.establecerUsuario(null)
        vm.refrescar()
    }

    @Test
    fun ordenaEventosPorFechaYReservaElSeguimientoAAdministracion() = runTest {
        val vm = modelo()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eventos.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.gestiones.collect() }
        eventos.value = listOf(evento, evento.copy(id = "previo", fecha = "01/01/2026"))
        gestiones.value =
            listOf(
                SeguimientoEntity(
                    prospectoId = 1,
                    administrativoId = admin.id,
                    administrativoNombre = admin.nombre,
                    estado = "CONTACTADO",
                    canal = "Correo",
                    observacion = "Nota",
                    fecha = 1L,
                )
            )
        assertEquals("previo", vm.eventos.value.first().id)
        assertTrue(vm.gestiones.value.isEmpty())
        vm.establecerUsuario(admin)
        assertEquals(1, vm.gestiones.value.size)
        vm.establecerUsuario(captador)
        assertTrue(vm.gestiones.value.isEmpty())
    }
}
