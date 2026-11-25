package cl.duoc.therionlabs

import androidx.lifecycle.viewModelScope
import cl.duoc.therionlabs.data.repository.AccesoRepository
import cl.duoc.therionlabs.data.viewmodel.AccesoViewModel
import cl.duoc.therionlabs.network.ConfiguracionConexion
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccesoViewModelTest : EscenarioLocal() {
    private val repo = mockk<AccesoRepository>()

    @Before
    fun prepararModelo() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun restaurarDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun exigeCamposYEvitaSolicitudesSimultaneas() = runTest {
        val vm = AccesoViewModel(app, repo)
        vm.ingresar("", "")
        assertNotNull(vm.error.value)
        coEvery { repo.ingresar("captador", "Clave") } returns captador
        vm.ingresar("captador", "Clave")
        assertTrue(vm.ocupado.value)
        vm.ingresar("captador", "Clave")
        advanceUntilIdle()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(captador, vm.sesion.value)
        assertFalse(vm.ocupado.value)
        assertNull(vm.error.value)
        coVerify(exactly = 1) { repo.ingresar(any(), any()) }
    }

    @Test
    fun muestraErrorYElCierreEliminaLaSesionActiva() = runTest {
        val vm = AccesoViewModel(app, repo)
        coEvery { repo.ingresar(any(), any()) } throws IOException("Sin red")
        vm.ingresar("captador", "Clave")
        advanceUntilIdle()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertNull(vm.sesion.value)
        assertNotNull(vm.error.value)
        assertFalse(vm.ocupado.value)
        coEvery { repo.salir(any()) } just Runs
        vm.cerrarSesion()
        advanceUntilIdle()
        vm.viewModelScope.coroutineContext[Job]!!.children.toList().joinAll()
        assertNull(vm.error.value)
        assertEquals("", ConfiguracionConexion.credencialSesion)
        coVerify { repo.salir("sesion-prueba") }
    }
}
