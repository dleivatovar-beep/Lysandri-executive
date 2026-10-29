package com.lysandri.application.service;

import com.lysandri.domain.model.Programa;
import com.lysandri.domain.model.RolUsuario;
import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.ports.in.MatriculaCorporativaUseCase;
import com.lysandri.domain.ports.in.SolicitudInformacionUseCase;
import com.lysandri.domain.ports.out.LmsClientPort;
import com.lysandri.domain.ports.out.NotificationPort;
import com.lysandri.domain.ports.out.ProgramRepositoryPort;
import com.lysandri.domain.ports.out.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import com.lysandri.adapters.out.database.entity.LoteMatriculaCorporativaEntity;
import com.lysandri.adapters.out.database.repository.SpringDataLoteMatriculaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatriculaCorporativaService implements MatriculaCorporativaUseCase {

    private final ProgramRepositoryPort programRepository;
    private final UserRepositoryPort userRepository;
    private final LmsClientPort lmsClientPort;
    private final NotificationPort notificationPort;
    private final SolicitudInformacionUseCase solicitudInformacionUseCase;
    private final PasswordEncoder passwordEncoder;
    private final SpringDataLoteMatriculaRepository loteMatriculaRepository;

    @Override
    @Transactional
    public MatriculaNominaResponse matricularNomina(MatricularNominaCommand command) {
        log.info("Iniciando matrícula masiva para empresa '{}' en programa ID: {}", command.empresaNombre(), command.idPrograma());

        if (command.idPrograma() == null) {
            throw new IllegalArgumentException("El ID del programa es obligatorio");
        }

        Programa programa = programRepository.buscarPorId(command.idPrograma())
                .orElseThrow(() -> new IllegalArgumentException("Programa no encontrado con ID: " + command.idPrograma()));

        if (programa.getMoodleCourseId() == null) {
            throw new IllegalStateException("El programa '" + programa.getTitulo() + "' no tiene configurado un curso en Moodle (moodleCourseId es nulo)");
        }

        if (command.colaboradores() == null || command.colaboradores().isEmpty()) {
            throw new IllegalArgumentException("La nómina debe contener al menos un colaborador a matricular");
        }

        List<MatriculaColaboradorResultado> resultados = new ArrayList<>();
        int exitosos = 0;
        int fallidos = 0;

        for (ColaboradorItem item : command.colaboradores()) {
            if (item.email() == null || item.email().trim().isBlank()) {
                fallidos++;
                resultados.add(new MatriculaColaboradorResultado(
                        item.nombres(),
                        item.apellidos(),
                        "",
                        null,
                        null,
                        null,
                        "ERROR",
                        "El correo electrónico es obligatorio"
                ));
                continue;
            }

            String email = item.email().trim().toLowerCase();
            String nombres = item.nombres() != null && !item.nombres().isBlank() ? item.nombres().trim() : "Colaborador";
            String apellidos = item.apellidos() != null && !item.apellidos().isBlank() ? item.apellidos().trim() : "Corporativo";
            String passwordGenerada = generarPasswordSegura10Chars();
            String username = email.split("@")[0].toLowerCase().replaceAll("[^a-z0-9._-]", "");

            try {
                // 1. Asegurar o crear usuario en base de datos local
                Usuario usuarioLocal = userRepository.buscarPorEmail(email).orElseGet(() -> {
                    Usuario nuevo = Usuario.builder()
                            .nombres(nombres)
                            .apellidos(apellidos)
                            .email(email)
                            .telefono(item.telefono())
                            .passw(passwordEncoder.encode(passwordGenerada))
                            .rol(RolUsuario.CLIENTE)
                            .activo(true)
                            .fechaCreacion(OffsetDateTime.now())
                            .fechaActualizacion(OffsetDateTime.now())
                            .build();
                    return userRepository.guardar(nuevo);
                });

                // 2. Aprovisionar o buscar en Moodle LMS
                Long moodleUserId = usuarioLocal.getMoodleUserId();
                if (moodleUserId == null) {
                    moodleUserId = lmsClientPort.buscarUsuarioPorEmail(email).orElse(null);
                }

                if (moodleUserId == null) {
                    moodleUserId = lmsClientPort.crearUsuario(
                            username,
                            passwordGenerada,
                            nombres,
                            apellidos,
                            email
                    );
                    usuarioLocal.setMoodleUserId(moodleUserId);
                    userRepository.guardar(usuarioLocal);
                    log.info("Usuario creado en Moodle para nómina corporativa ID: {}", moodleUserId);
                } else {
                    log.info("Usuario existente en Moodle para nómina corporativa ID: {}", moodleUserId);
                }

                // 3. Matricular en el curso Moodle
                lmsClientPort.matricularUsuarioEnCurso(moodleUserId, programa.getMoodleCourseId(), 5);
                log.info("Usuario Moodle ID {} matriculado con éxito en curso {}", moodleUserId, programa.getMoodleCourseId());

                // 4. Notificar por correo si está habilitado
                if (command.enviarCredencialesEmail()) {
                    try {
                        notificationPort.enviarBienvenidaCorporativa(
                                email,
                                nombres + " " + apellidos,
                                command.empresaNombre(),
                                programa.getTitulo(),
                                username,
                                passwordGenerada
                        );
                    } catch (Exception ex) {
                        log.warn("No se pudo enviar correo corporativo para usuario Moodle ID {}: {}", moodleUserId, com.lysandri.security.SanitizerUtils.sanitizeMessage(ex.getMessage()));
                    }
                }

                exitosos++;
                resultados.add(new MatriculaColaboradorResultado(
                        nombres,
                        apellidos,
                        email,
                        moodleUserId,
                        username,
                        passwordGenerada,
                        "MATRICULADO",
                        "Matriculado con éxito en Moodle (Curso ID: " + programa.getMoodleCourseId() + ")"
                ));
            } catch (Exception e) {
                fallidos++;
                log.error("Error al procesar colaborador en nómina: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
                resultados.add(new MatriculaColaboradorResultado(
                        nombres,
                        apellidos,
                        email,
                        null,
                        username,
                        null,
                        "ERROR",
                        e.getMessage()
                ));
            }
        }

        // Si se vinculó una solicitud de información In-Company, marcarla como CERRADA
        if (command.idSolicitud() != null) {
            try {
                solicitudInformacionUseCase.actualizarEstado(command.idSolicitud(), "CERRADA");
                log.info("Solicitud In-Company ID {} actualizada a CERRADA", command.idSolicitud());
            } catch (Exception e) {
                log.warn("No se pudo actualizar estado de solicitud ID {}: {}", command.idSolicitud(), e.getMessage());
            }
        }

        // Persistir auditoría del lote en base de datos
        try {
            LoteMatriculaCorporativaEntity lote = LoteMatriculaCorporativaEntity.builder()
                    .idPrograma(programa.getIdPrograma())
                    .idSolicitud(command.idSolicitud())
                    .empresaNombre(command.empresaNombre())
                    .totalColaboradores(command.colaboradores().size())
                    .totalExitosos(exitosos)
                    .totalFallidos(fallidos)
                    .fechaRegistro(OffsetDateTime.now())
                    .notasAdmin("Lote procesado: " + exitosos + " exitosos, " + fallidos + " fallidos.")
                    .build();
            loteMatriculaRepository.save(lote);
            log.info("Lote corporativo registrado en auditoría para empresa '{}'", command.empresaNombre());
        } catch (Exception ex) {
            log.error("Error al persistir auditoría del lote corporativo: {}", ex.getMessage());
        }

        return new MatriculaNominaResponse(
                programa.getIdPrograma(),
                programa.getTitulo(),
                programa.getMoodleCourseId(),
                command.empresaNombre(),
                command.colaboradores().size(),
                exitosos,
                fallidos,
                resultados
        );
    }

    private String generarPasswordSegura10Chars() {
        final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String LOWER = "abcdefghijkmnpqrstuvwxyz";
        final String DIGITS = "23456789";
        final String SYMBOLS = "!@#$*";
        final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

        SecureRandom random = new SecureRandom();
        List<Character> chars = new ArrayList<>();
        chars.add(UPPER.charAt(random.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(random.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(random.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(random.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(random.nextInt(DIGITS.length())));
        chars.add(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        while (chars.size() < 10) {
            chars.add(ALL.charAt(random.nextInt(ALL.length())));
        }

        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder();
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
