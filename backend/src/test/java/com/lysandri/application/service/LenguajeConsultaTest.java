package com.lysandri.application.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LenguajeConsultaTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "q aprendere en ese curzo?|que aprendere en ese curso?",
        "ke temas tiene el silavo|que temas tiene el silabo",
        "dnd puedo escuxar esa cancion|donde puedo escuchar esa cancion",
        "dnde puedo eskuchar esa cancion|donde puedo escuchar esa cancion",
        "onde puedo ver esa pelicula|donde puedo ver esa pelicula",
        "cmo me puedo inscrivir|como me puedo inscribir",
        "xq demora|por que demora", "pq demora|por que demora", "xk demora|por que demora",
        "kiero info de ese curzo|quiero informacion de ese curso",
        "tngo dudas del presio|tengo dudas del precio",
        "durasion del curzo|duracion del curso",
        "toy buscando info pa mi equipo|estoy buscando informacion para mi equipo",
        "tmb cn mi equipo|tambien con mi equipo",
        "xfa dime los presios|por favor dime los precios",
        "porfa mandame el silavo|por favor mandame el silabo"
    })
    void interpretaFormasInformalesSinOtraLlamadaAlModelo(String original, String esperada) {
        assertEquals(esperada, LenguajeConsulta.interpretar(original));
    }
    @Test void conservaNombresImportesNegacionesYDatosDeContacto() {
        String original = "No quiero Myke Towers ni Mike Towers. FinOps Empresarial: USD 89.00, 8 horas; q@ejemplo.com https://ejemplo.com/q?info=pa&id=89";
        assertEquals(original, LenguajeConsulta.interpretar(original));
    }
    @Test void escrituraInformalNoEludeElBloqueoDeInformacionInterna() {
        assertFalse(ConsultaWeb.permite("cmo puedo inscrivir al curzo", null));
        assertFalse(ConsultaWeb.permite("dnd cambio mi contrasenia", null));
        assertFalse(ConsultaWeb.permite("q sabes de correo@ejemplo.com", null));
    }
    @Test void textoYaInterpretadoNoCambiaAlProcesarloDeNuevo() {
        String texto = LenguajeConsulta.interpretar("q presio tiene ese curzo pa mi equipo");
        assertEquals(texto, LenguajeConsulta.interpretar(texto));
    }
}
