package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.repository.ProspectoRepository
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProspectoRepositoryTest : EscenarioLocal() {
    @Test
    fun guardaLaIdentidadAsignadaYNormalizaElCorreo() = runTest {
        val repo = ProspectoRepository(db, tokens)
        val datos = ficha().copy(email = " Persona@Ejemplo.cl ")
        coEvery { prospectos.insertarProspecto(any()) } returns 7L
        assertEquals(7L, repo.guardarProspecto(captador, datos))
        coVerify {
            prospectos.insertarProspecto(
                match {
                    it.identificador == datos.identificador &&
                        it.captadorId == "CAP-03" &&
                        it.eventoNombre == evento.nombre &&
                        it.email == "persona@ejemplo.cl"
                }
            )
        }
        fichas.value = listOf(datos)
        assertEquals(listOf(datos), repo.registros().first())
    }

    @Test
    fun rechazaRolEventoCatalogoYRutIncorrectosAntesDeInsertar() = runTest {
        val repo = ProspectoRepository(db, tokens)
        assertTrue(runCatching { repo.guardarProspecto(admin, ficha()) }.isFailure)
        coEvery { operacion.evento(evento.id) } returns evento.copy(activo = false)
        assertTrue(runCatching { repo.guardarProspecto(captador, ficha()) }.isFailure)
        coEvery { operacion.evento(evento.id) } returns evento
        for (datos in
            listOf(
                ficha().copy(region = "Otra región"),
                ficha().copy(sede = "Alameda"),
                ficha().copy(carrera1 = "No existe"),
                ficha().copy(carrera2 = ficha().carrera1),
                ficha().copy(rut = "12.345.678-4"),
                ficha().copy(email = "sin-arroba"),
            )) assertTrue(runCatching { repo.guardarProspecto(captador, datos) }.isFailure)
        coVerify(exactly = 0) { prospectos.insertarProspecto(any()) }
    }

    @Test
    fun impideDuplicarRutOCorreoEnElMismoEvento() = runTest {
        val repo = ProspectoRepository(db, tokens)
        coEvery { prospectos.existeRut(evento.id, "123456785") } returns true
        assertTrue(
            runCatching { repo.guardarProspecto(captador, ficha()) }
                .exceptionOrNull()!!
                .message!!
                .contains("RUT")
        )
        coEvery { prospectos.existeRut(any(), any()) } returns false
        coEvery { prospectos.existeCorreo(evento.id, "persona@ejemplo.cl") } returns true
        assertTrue(
            runCatching { repo.guardarProspecto(captador, ficha()) }
                .exceptionOrNull()!!
                .message!!
                .contains("correo")
        )
        coVerify(exactly = 0) { prospectos.insertarProspecto(any()) }
    }

    @Test
    fun exigeIdentidadVinculadaCuandoHayAlmacenDeCredenciales() = runTest {
        every { tokens.obtenerCodigoCaptador(captador.id) } returns null
        assertTrue(
            runCatching { ProspectoRepository(db, tokens).guardarProspecto(captador, ficha()) }
                .isFailure
        )
        coEvery { prospectos.insertarProspecto(any()) } returns 8L
        assertEquals(8L, ProspectoRepository(db).guardarProspecto(captador, ficha()))
        coVerify { prospectos.insertarProspecto(match { it.captadorId == captador.id }) }
    }

    @Test
    fun corregirUnRechazoConservaSuUuidYLoDejaPendiente() = runTest {
        val anterior = ficha().copy(sincronizado = 2, motivoRechazo = "Dato incorrecto")
        coEvery { prospectos.obtenerPorId(1) } returns anterior
        coEvery { prospectos.obtenerParaExportacion() } returns listOf(anterior)
        ProspectoRepository(db, tokens)
            .editarProspecto(
                captador,
                anterior.copy(identificador = "otro", captadorId = "CAP-99", nombre = "Corregido"),
            )
        coVerify {
            prospectos.actualizar(
                match {
                    it.identificador == anterior.identificador &&
                        it.captadorId == anterior.captadorId &&
                        it.nombre == "Corregido" &&
                        it.sincronizado == 0 &&
                        it.motivoRechazo.isEmpty()
                }
            )
        }
    }

    @Test
    fun noPermiteEditarFichasAjenasSincronizadasODuplicadas() = runTest {
        val repo = ProspectoRepository(db, tokens)
        coEvery { prospectos.obtenerPorId(1) } returns ficha().copy(captadorId = "CAP-02")
        assertTrue(runCatching { repo.editarProspecto(captador, ficha()) }.isFailure)
        coEvery { prospectos.obtenerPorId(1) } returns ficha().copy(sincronizado = 1)
        assertTrue(runCatching { repo.editarProspecto(captador, ficha()) }.isFailure)
        coEvery { prospectos.obtenerPorId(1) } returns ficha()
        coEvery { prospectos.obtenerParaExportacion() } returns listOf(ficha(2))
        assertTrue(runCatching { repo.editarProspecto(captador, ficha()) }.isFailure)
        coVerify(exactly = 0) { prospectos.actualizar(any()) }
    }

    @Test
    fun soloAdministracionExportaOBorraFichasRechazadas() = runTest {
        val repo = ProspectoRepository(db, tokens)
        coEvery { prospectos.obtenerParaExportacion() } returns listOf(ficha())
        assertEquals(1, repo.obtenerProspectosParaExcel(admin).size)
        assertTrue(runCatching { repo.obtenerProspectosParaExcel(captador) }.isFailure)
        coEvery { prospectos.obtenerPorId(1) } returns ficha()
        assertTrue(runCatching { repo.borrarProspecto(admin, 1) }.isFailure)
        coEvery { prospectos.obtenerPorId(1) } returns ficha().copy(sincronizado = 2)
        assertTrue(runCatching { repo.borrarProspecto(captador, 1) }.isFailure)
        repo.borrarProspecto(admin, 1)
        coVerify(exactly = 1) { prospectos.borrarProspecto(1) }
    }
}
