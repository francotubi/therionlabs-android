package cl.duoc.therionlabs

import cl.duoc.therionlabs.data.model.FormularioProspectos
import cl.duoc.therionlabs.domain.CatalogoRegional
import cl.duoc.therionlabs.network.ServicioClima
import cl.duoc.therionlabs.util.ValidacionesProspecto
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ValidacionesFormularioTest {
    @Test
    fun informaTodosLosCamposObligatoriosYLosCorrigeConDatosValidos() {
        val vacio = ValidacionesProspecto.validar(FormularioProspectos())
        assertNotNull(vacio.rut)
        assertNotNull(vacio.fechaNacimiento)
        assertNotNull(vacio.celular)
        assertNotNull(vacio.email)
        assertTrue(vacio.hayErroresDatosPersonales)
        assertTrue(vacio.hayErroresSeleccion)
        val completo =
            FormularioProspectos(
                rut = "12.345.678-5",
                nombre = "Persona",
                apellidos = "Prueba",
                fechaNacimiento = "01/01/2004",
                celular = "+56 9 1234 5678",
                email = "persona@ejemplo.cl",
                establecimiento = "Colegio",
                comunaResidencia = "Quilpué",
                comunaEstablecimiento = "Quilpué",
                actividadActual = "Estudiante",
                jornada = "Diurna",
                modalidad = "Presencial",
                canalContacto = "Correo",
                sede = "Viña del Mar",
                nivelInteres = "Alto",
                carreras = listOf("Ingeniería en Informática"),
            )
        assertFalse(ValidacionesProspecto.validar(completo).hayErrores)
        val incorrecto =
            ValidacionesProspecto.validar(
                completo.copy(
                    rut = "abc",
                    fechaNacimiento = "mal",
                    celular = "123",
                    email = "sin-arroba",
                    region = "Otra",
                )
            )
        assertNotNull(incorrecto.rut)
        assertNotNull(incorrecto.fechaNacimiento)
        assertNotNull(incorrecto.celular)
        assertNotNull(incorrecto.email)
        assertNotNull(incorrecto.region)
    }

    @Test
    fun rechazaCarrerasRepetidasYMasDeTresPreferencias() {
        val carreras = CatalogoRegional.carreras("Viña del Mar")
        val base = FormularioProspectos(sede = "Viña del Mar")
        assertNotNull(
            ValidacionesProspecto.validar(base.copy(carreras = carreras.take(4))).carreras
        )
        assertNotNull(
            ValidacionesProspecto.validar(
                    base.copy(carreras = listOf(carreras.first(), carreras.first()))
                )
                .carreras
        )
    }

    @Test
    fun aplicaLosLimitesDeCorreoFechasYCelular() {
        assertFalse(ValidacionesProspecto.esCorreoValido("a".repeat(65) + "@ejemplo.cl"))
        assertFalse(ValidacionesProspecto.esCorreoValido("a".repeat(255) + "@ejemplo.cl"))
        assertFalse(ValidacionesProspecto.esCorreoValido(".persona@ejemplo.cl"))
        assertFalse(ValidacionesProspecto.esCorreoValido("persona.@ejemplo.cl"))
        assertFalse(ValidacionesProspecto.esFechaNacimientoValida("1/1/2000"))
        assertFalse(ValidacionesProspecto.esFechaNacimientoValida("01/01/0000"))
        assertTrue(
            ValidacionesProspecto.esFechaNacimientoValida("25/11/2026", LocalDate.of(2026, 11, 25))
        )
        assertTrue(ValidacionesProspecto.esCelularValido("(9) 1234-5678"))
        assertTrue(ValidacionesProspecto.esCelularValido("56912345678"))
        assertFalse(ValidacionesProspecto.esCelularValido("812345678"))
        assertFalse(ValidacionesProspecto.esRutValido("abc"))
    }

    @Test
    fun lasCondicionesDelClimaTienenUnaDescripcionLegible() {
        for (codigo in listOf(1, 2, 3)) assertEquals("Nublado", ServicioClima.descripcion(codigo))
        for (codigo in listOf(45, 48)) assertEquals("Niebla", ServicioClima.descripcion(codigo))
        for (codigo in listOf(51, 67, 80, 82)) assertEquals(
            "Lluvia",
            ServicioClima.descripcion(codigo),
        )
        for (codigo in listOf(71, 77, 85, 86)) assertEquals(
            "Nieve",
            ServicioClima.descripcion(codigo),
        )
        for (codigo in listOf(95, 96, 99)) assertEquals(
            "Tormenta",
            ServicioClima.descripcion(codigo),
        )
    }
}
