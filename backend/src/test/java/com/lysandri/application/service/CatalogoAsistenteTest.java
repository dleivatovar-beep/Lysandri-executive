package com.lysandri.application.service;

import com.lysandri.domain.model.DocumentoChunk;
import com.lysandri.domain.model.Programa;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogoAsistenteTest {
    private final List<Programa> programas = List.of(
            Programa.builder().titulo("FinOps Empresarial").precio(new BigDecimal("89.00")).moneda("USD").duracionHoras(8).build(),
            Programa.builder().titulo("Ciberseguridad Corporativa").precio(new BigDecimal("109.00")).moneda("USD").duracionHoras(10).build());

    private DocumentoChunk fuente(Long documento, String estado, String contenido) {
        return DocumentoChunk.builder().idDocumento(documento).contenido(contenido)
                .metadata(Map.of("titulo", "Catálogo de Lysandri Executive", "estado", estado)).build();
    }

    private DocumentoChunk catalogo() {
        return fuente(1L, "VIGENTE", "- FinOps Empresarial: USD 89.00, 8 horas.\n- Ciberseguridad Corporativa: USD 109.00, 10 horas.");
    }

    @Test void listaCursosSinPrometerUnaCompra() {
        String respuesta = RespuestaBreve.catalogo("Qué cursos tienen?", List.of(catalogo()), programas);
        assertEquals("Estos son los cursos que tenemos:\n\n- FinOps Empresarial\n- Ciberseguridad Corporativa", respuesta);
        assertTrue(RespuestaBreve.esConsultaCatalogo("¿Qué cursos tienen?"));
        assertTrue(RespuestaBreve.esConsultaCatalogo("que cursos tienen"));
    }

    @Test void preciosYDuracionesConservanMonedaYUnidad() {
        assertTrue(RespuestaBreve.catalogo("Cuánto cuestan los cursos?", List.of(catalogo()), programas).contains("FinOps Empresarial: USD 89.00"));
        assertTrue(RespuestaBreve.catalogo("Cuánto duran los cursos?", List.of(catalogo()), programas).contains("Ciberseguridad Corporativa: 10 horas"));
    }

    @Test void documentoDivididoSeReuneSinOmitirCursos() {
        var a = fuente(1L, "VIGENTE", "- FinOps Empresarial: USD 89.00, 8 horas.");
        var b = fuente(1L, "VIGENTE", "- Ciberseguridad Corporativa: USD 109.00. Duracion registrada: 10 horas.");
        assertNotNull(RespuestaBreve.catalogo("Qué cursos hay?", List.of(a, b), programas));
        assertNull(RespuestaBreve.catalogo("Qué cursos hay?", List.of(a), programas));
    }

    @Test void documentoEnPropuestaYVersionesDistintasNoSeDanPorVigentes() {
        assertNull(RespuestaBreve.catalogo("Qué cursos hay?", List.of(fuente(1L, "PROPUESTA", catalogo().getContenido())), programas));
        assertNull(RespuestaBreve.catalogo("Qué cursos hay?", List.of(catalogo(), fuente(2L, "VIGENTE", catalogo().getContenido())), programas));
    }

    @Test void cambiosEnLaBaseImpidenUsarUnResumenAntiguo() {
        programas.get(0).setPrecio(new BigDecimal("90.00"));
        assertNull(RespuestaBreve.catalogo("Cuánto cuestan los cursos?", List.of(catalogo()), programas));
    }

    @Test void preguntasCompuestasYFiltrosNoSeReducenAlListadoGeneral() {
        assertFalse(RespuestaBreve.esConsultaCatalogo("Qué cursos tienen y cuándo inician?"));
        assertFalse(RespuestaBreve.esConsultaCatalogo("Qué cursos tienen por menos de 80 dólares?"));
        assertNull(RespuestaBreve.catalogo("Qué cursos tienen y cuándo inician?", List.of(catalogo()), programas));
    }
}
