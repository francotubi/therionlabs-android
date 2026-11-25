package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.repository.AdministracionRepository
import cl.duoc.therionlabs.network.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AdministracionRepositoryTest : EscenarioLocal() {
    private val api = mockk<AdministracionApi>()

    @Before
    fun prepararApi() {
        mockkObject(RetrofitClient)
        every { RetrofitClient.administracionApi } returns api
    }

    @Test
    fun noSobrescribeCapturasPendientesYAplicaLasBajasRemotas() = runTest {
        val repo = AdministracionRepository(db)
        val datos = ficha()
        coEvery { prospectos.porIdentificador(datos.identificador) } returns datos
        repo.guardar(remoto(datos))
        coVerify(exactly = 0) { prospectos.actualizar(any()) }
        repo.guardar(remoto(datos).copy(eliminado = true))
        coVerify {
            prospectos.actualizar(
                match { it.id == datos.id && it.eliminado && it.sincronizado == 1 }
            )
        }
    }

    @Test
    fun descargaLasFichasYReconciliaSeguimientosPorUuid() = runTest {
        val datos = ficha().copy(sincronizado = 1, fotoRuta = "foto-local.jpg")
        val gestion =
            SeguimientoRemoto(
                "gestion-1",
                datos.identificador,
                "Administrativo",
                "CONTACTADO",
                "Correo",
                "Información enviada",
                "2026-11-25T10:00:00Z",
            )
        coEvery { prospectos.porIdentificador(datos.identificador) } returns datos
        coEvery { api.prospectos(evento.id, any()) } returns Response.success(listOf(remoto(datos)))
        coEvery { api.seguimientos(datos.identificador) } returns Response.success(listOf(gestion))
        AdministracionRepository(db).actualizarEvento(admin, evento.id)
        coVerify {
            prospectos.actualizar(match { it.fotoRuta == "foto-local.jpg" && it.id == datos.id })
        }
        coVerify {
            operacion.actualizarGestion(
                match {
                    it.identificador == "gestion-1" &&
                        it.prospectoId == datos.id &&
                        it.estadoEnvio == "SINCRONIZADO" &&
                        it.fecha > 0L
                }
            )
        }
        assertTrue(
            runCatching { AdministracionRepository(db).actualizarEvento(captador, evento.id) }
                .isFailure
        )
    }

    @Test
    fun edicionYBajaSeGuardanSoloConAceptacionDelServidor() = runTest {
        val repo = AdministracionRepository(db)
        val datos = ficha().copy(sincronizado = 1)
        coEvery { prospectos.obtenerPorId(1) } returns datos
        coEvery { prospectos.porIdentificador(datos.identificador) } returns datos
        coEvery { api.actualizar(datos.identificador, any()) } returns
            Response.success(remoto(datos))
        repo.editar(admin, datos)
        coVerify { api.actualizar(datos.identificador, match { it.id == datos.identificador }) }
        coEvery { api.eliminar(datos.identificador) } returns errorHttp(409)
        assertTrue(runCatching { repo.eliminar(admin, 1) }.isFailure)
        coVerify(exactly = 0) { prospectos.actualizar(match { it.eliminado }) }
        coEvery { api.eliminar(datos.identificador) } returns Response.success(Unit)
        repo.eliminar(admin, 1)
        coVerify {
            prospectos.actualizar(match { it.eliminado && it.identificador == datos.identificador })
        }
        assertTrue(runCatching { repo.editar(admin, datos.copy(sincronizado = 0)) }.isFailure)
        coEvery { prospectos.obtenerPorId(1) } returns datos.copy(sincronizado = 0)
        assertTrue(runCatching { repo.eliminar(admin, 1) }.isFailure)
    }
}
