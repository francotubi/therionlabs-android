package cl.duoc.therionlabs

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import cl.duoc.therionlabs.data.model.FormularioProspectos
import cl.duoc.therionlabs.data.repository.ProspectoRepository
import cl.duoc.therionlabs.data.viewmodel.ProspectoViewModel
import cl.duoc.therionlabs.domain.CatalogoRegional
import cl.duoc.therionlabs.network.DispositivoTokenStore
import cl.duoc.therionlabs.worker.SyncScheduler
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProspectoViewModelTest : EscenarioLocal() {
    private val repo = mockk<ProspectoRepository>()

    @Before
    fun prepararModelo() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repo.registros() } returns fichas
        mockkConstructor(DispositivoTokenStore::class)
        every { anyConstructed<DispositivoTokenStore>().obtenerCodigoCaptador(captador.id) } returns
            "CAP-03"
        mockkObject(SyncScheduler)
        every { SyncScheduler.solicitarSincronizacion(any()) } just Runs
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    @After
    fun restaurarDispatcher() {
        Dispatchers.resetMain()
    }

    private fun modelo() = ProspectoViewModel(repo, SavedStateHandle(), app)

    private fun formulario() =
        FormularioProspectos(
            rut = "12.345.678-5",
            nombre = "Persona",
            apellidos = "Prueba",
            fechaNacimiento = "01/01/2004",
            celular = "912345678",
            email = "persona@ejemplo.cl",
            establecimiento = "Colegio",
            comunaResidencia = "Quilpué",
            comunaEstablecimiento = "Quilpué",
            actividadActual = "Estudiante",
            jornada = "Diurna",
            sede = "Viña del Mar",
            nivelInteres = "Alto",
            carreras = listOf("Ingeniería en Informática"),
            modalidad = "Presencial",
            canalContacto = "Correo electrónico",
        )

    private fun listo(): ProspectoViewModel =
        modelo().apply {
            establecerUsuario(captador)
            seleccionarEvento(evento)
            actualizarFormulario(formulario())
        }

    @Test
    fun filtraElHistorialPorPerfilYCodigoAsignado() = runTest {
        val vm = modelo()
        fichas.value = listOf(ficha(), ficha(2).copy(captadorId = "CAP-02"))
        assertTrue(vm.estadoHistorial.first().registros.isEmpty())
        vm.establecerUsuario(captador)
        vm.actualizarIdentidadCaptador()
        assertEquals(listOf(1), vm.estadoHistorial.first().registros.map { it.id })
        vm.establecerUsuario(admin)
        assertEquals(2, vm.estadoHistorial.first().registros.size)
        vm.establecerUsuario(null)
        assertTrue(vm.estadoHistorial.first().registros.isEmpty())
    }

    @Test
    fun conservaElBorradorDelMismoUsuarioYLoDescartaAlCambiarDeCuenta() {
        val estado = SavedStateHandle()
        val vm = ProspectoViewModel(repo, estado, app)
        vm.establecerUsuario(captador)
        vm.seleccionarEvento(evento)
        assertFalse(vm.tieneBorrador())
        vm.actualizarFormulario(formulario())
        assertTrue(vm.tieneBorrador())
        val restaurado = ProspectoViewModel(repo, estado, app)
        restaurado.establecerUsuario(captador)
        assertEquals("Persona", restaurado.formulario.nombre)
        restaurado.establecerUsuario(admin)
        assertFalse(restaurado.tieneBorrador())
        assertEquals("", restaurado.formulario.nombre)
    }

    @Test
    fun limitaCarrerasYLimpiaSeleccionAlCambiarDeRegion() {
        val vm = listo()
        assertTrue(vm.puedeElegirSede)
        assertEquals(3, vm.maximoCarreras)
        val carreras = CatalogoRegional.carreras(vm.formulario.sede).take(4)
        vm.limpiarCarreras()
        carreras.forEach(vm::alternarCarrera)
        assertEquals(3, vm.formulario.carreras.size)
        assertFalse(vm.puedeAgregarCarreras)
        vm.alternarCarrera(carreras.first())
        assertEquals(2, vm.formulario.carreras.size)
        vm.alternarCarrera("  ")
        vm.actualizarFormulario(vm.formulario.copy(region = "Otra región"))
        assertEquals(CatalogoRegional.region, vm.formulario.region)
        assertEquals("", vm.formulario.sede)
        assertTrue(vm.formulario.carreras.isEmpty())
    }

    @Test
    fun laValidacionNoGuardaDatosIncompletos() {
        val vm = modelo()
        vm.establecerUsuario(captador)
        vm.seleccionarEvento(evento)
        assertFalse(vm.validarDatosPersonales())
        assertNotNull(vm.erroresFormulario.rut)
        vm.guardarProspecto()
        assertNotNull(vm.errorGuardado)
        coVerify(exactly = 0) { repo.guardarProspecto(any(), any()) }
    }

    @Test
    fun guardaNormalizaYEvitaLaDobleInsercion() = runTest {
        coEvery { repo.guardarProspecto(captador, any()) } returns 12L
        val vm = listo()
        assertTrue(vm.validarDatosPersonales())
        vm.guardarProspecto()
        assertEquals(12L, vm.registroGuardadoId)
        assertFalse(vm.guardando)
        coVerify {
            repo.guardarProspecto(
                captador,
                match {
                    it.rut == "12345678-5" &&
                        it.celular == "+56912345678" &&
                        it.eventoId == evento.id
                },
            )
        }
        vm.guardarProspecto()
        coVerify(exactly = 1) { repo.guardarProspecto(any(), any()) }
        verify { SyncScheduler.solicitarSincronizacion(app) }
        vm.consumirConfirmacionGuardado()
        assertNull(vm.registroGuardadoId)
    }

    @Test
    fun unErrorDeGuardadoConservaLasRespuestasParaReintentar() = runTest {
        val vm = listo()
        coEvery { repo.guardarProspecto(any(), any()) } throws
            IllegalArgumentException("RUT duplicado")
        vm.guardarProspecto()
        assertEquals("RUT duplicado", vm.errorGuardado)
        assertEquals("Persona", vm.formulario.nombre)
        coEvery { repo.guardarProspecto(any(), any()) } throws IOException("Disco")
        vm.guardarProspecto()
        assertTrue(vm.errorGuardado!!.contains("conservaron"))
        assertNull(vm.registroGuardadoId)
        assertFalse(vm.guardando)
    }

    @Test
    fun editarYBorrarInformanElResultadoSinPerderElControlDePermisos() = runTest {
        val vm = listo()
        var terminado = false
        coEvery { repo.editarProspecto(any(), any()) } just Runs
        vm.editarProspecto(ficha()) { terminado = true }
        assertTrue(terminado)
        coEvery { repo.editarProspecto(any(), any()) } throws
            IllegalArgumentException("Ficha ajena")
        vm.editarProspecto(ficha()) {}
        assertEquals("Ficha ajena", vm.errorBorrado)
        vm.limpiarErrorBorrado()
        assertNull(vm.errorBorrado)
        vm.borrarProspecto(1)
        coVerify(exactly = 0) { repo.borrarProspecto(any(), any()) }
        vm.establecerUsuario(admin)
        coEvery { repo.borrarProspecto(admin, 1) } throws
            IllegalArgumentException("Tiene gestiones")
        vm.borrarProspecto(1)
        assertEquals("Tiene gestiones", vm.errorBorrado)
        coEvery { repo.borrarProspecto(admin, 1) } just Runs
        vm.borrarProspecto(1)
        assertNull(vm.errorBorrado)
        assertFalse(vm.procesando)
    }
}
