package com.lysandri.api.service.impl;

import com.lysandri.api.dto.request.ChatRequest;
import com.lysandri.api.dto.response.ChatResponse;
import com.lysandri.api.exception.BadRequestException;
import com.lysandri.api.model.entity.ChatMensaje;
import com.lysandri.api.model.entity.Usuario;
import com.lysandri.api.repository.ChatMensajeRepository;
import com.lysandri.api.repository.InscripcionRepository;
import com.lysandri.api.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMensajeRepository chatMensajeRepository;
    private final InscripcionRepository inscripcionRepository;

    @Override
    @Transactional
    public ChatResponse procesarMensaje(ChatRequest request, Usuario usuario) {
        if (usuario == null) {
            throw new BadRequestException("Sesión no válida o usuario no autenticado");
        }

        String pregunta = request.getMensaje().trim();
        log.info("Procesando consulta de chat para usuario [{}]: {}", usuario.getEmail(), pregunta);

        // Validación de contexto de programa académico si se suministra
        String contextoPrograma = "";
        if (request.getProgramaId() != null) {
            boolean estaInscrito = inscripcionRepository.existsByUsuarioIdUserAndProgramaIdPrograma(
                    usuario.getIdUser(),
                    request.getProgramaId().intValue()
            );
            if (estaInscrito) {
                contextoPrograma = " en el contexto del programa #" + request.getProgramaId();
            }
        }

        // Generación de respuesta y selección de fuentes documentales
        List<String> fuentes = new ArrayList<>();
        String respuestaGenerada = generarRespuestaContextual(pregunta, contextoPrograma, fuentes);

        // Persistir la interacción en la entidad CHAT_MENSAJES
        ChatMensaje mensajeEntidad = ChatMensaje.builder()
                .usuario(usuario)
                .pregunta(pregunta)
                .respuestaIa(respuestaGenerada)
                .build();

        chatMensajeRepository.save(mensajeEntidad);
        log.debug("Mensaje de chat guardado con éxito para usuario id {}", usuario.getIdUser());

        return ChatResponse.builder()
                .respuesta(respuestaGenerada)
                .fuentes(fuentes)
                .fechaEnvio(LocalDateTime.now())
                .build();
    }

    private String generarRespuestaContextual(String pregunta, String contextoPrograma, List<String> fuentes) {
        String queryLower = pregunta.toLowerCase(Locale.ROOT);

        if (queryLower.contains("kafka") || queryLower.contains("eventos") || queryLower.contains("eda")) {
            fuentes.add("Manual de Arquitectura Cloud Lysandri - Cap. 4: Event-Driven Systems");
            fuentes.add("Playbook de Patrones de Integración C-Suite");
            return "La adopción de una arquitectura orientada a eventos con Apache Kafka permite desacoplar los servicios de misión crítica, reduciendo la latencia de procesamiento y mejorando la resiliencia ante picos de demanda. El ROI promedio estimado en el primer año contempla una reducción del 35% en costos de sincronización distribuida y alta disponibilidad para pipelines analíticos" + contextoPrograma + ".";
        }

        if (queryLower.contains("finops") || queryLower.contains("costo") || queryLower.contains("presupuesto") || queryLower.contains("eks") || queryLower.contains("aws")) {
            fuentes.add("Marco de Trabajo FinOps Lysandri Executive");
            fuentes.add("Playbook de Optimización EKS & Spot Instances");
            return "Para optimizar el modelo de costos FinOps en clústeres Kubernetes (EKS), se recomienda implementar Karpenter para autoscaling inteligente de nodos, el uso estratégico de instancias Spot para cargas resilientes y cuotas de recursos mediante ResourceQuotas y LimitRanges. Esto asegura una visibilidad precisa del gasto por unidad de negocio" + contextoPrograma + ".";
        }

        if (queryLower.contains("seguridad") || queryLower.contains("zero-trust") || queryLower.contains("opa") || queryLower.contains("gateway") || queryLower.contains("llm")) {
            fuentes.add("Política de Ciberseguridad y Zero-Trust Lysandri");
            fuentes.add("Guía de Guardrails y Gobernanza de Modelos LLM");
            return "Los guardrails de seguridad OPA (Open Policy Agent) aplicados al gateway LLM deben incluir: validación y sanitización estricta de prompts contra inyecciones indirectas, filtrado de datos personales (PII Masking), cuotas de tokens por rol y validación de tokens JWT en cada petición. Toda interacción debe quedar auditada sin exponer secretos de infraestructura" + contextoPrograma + ".";
        }

        if (queryLower.contains("curso") || queryLower.contains("programa") || queryLower.contains("leccion") || queryLower.contains("progreso") || queryLower.contains("examen")) {
            fuentes.add("Reglamento Académico Lysandri Executive");
            fuentes.add("Guía del Estudiante y Certificaciones C-Suite");
            return "Puedes consultar tus programas activos y avances desde tu panel de cursos ejecutivos. Cada módulo cuenta con lecciones estructuradas, evaluaciones formativas y recursos técnicos certificados para tu desarrollo directivo" + contextoPrograma + ".";
        }

        // Respuesta ejecutiva por defecto con grounding estándar
        fuentes.add("Base de Conocimiento Central Lysandri Executive");
        fuentes.add("Documentación Técnica de Plataforma");
        return "He procesado tu consulta directiva. Desde la perspectiva de arquitectura y estrategia tecnológica en Lysandri Executive, recomendamos evaluar el impacto en escalabilidad, gobernanza de datos y alineación con los objetivos de negocio de la organización" + contextoPrograma + ". ¿Deseas profundizar en alguna directriz específica?";
    }
}
