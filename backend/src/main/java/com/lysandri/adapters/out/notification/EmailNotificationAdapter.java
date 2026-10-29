package com.lysandri.adapters.out.notification;

import com.lysandri.adapters.out.database.entity.NotificacionLogEntity;
import com.lysandri.adapters.out.database.repository.SpringDataNotificacionLogRepository;
import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.ports.out.NotificationPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;
    private final SpringDataNotificacionLogRepository notificacionLogRepository;

    @Value("${moodle.url:http://localhost:8080}")
    private String moodleUrl;

    @Value("${spring.mail.username:no-reply@lysandri.com}")
    private String fromEmail = "no-reply@lysandri.com";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void enviarAccesosYFactura(
            String email,
            String nombre,
            String cursoTitulo,
            String moodlePassword,
            ComprobantePago comprobante) {

        Long idOrden = comprobante != null ? comprobante.getIdOrden() : null;
        String idRef = idOrden != null ? String.valueOf(idOrden) : Integer.toHexString(email != null ? email.trim().toLowerCase().hashCode() : 0);
        String claveIdempotencia = "ACCESOS_ORDEN_" + idRef;

        log.info("Verificando idempotencia de notificación para clave: {}", claveIdempotencia);

        Optional<NotificacionLogEntity> logExistente = notificacionLogRepository.findByClaveIdempotencia(claveIdempotencia);
        if (logExistente.isPresent() && "ENVIADO".equalsIgnoreCase(logExistente.get().getEstado())) {
            log.info("Notificación de accesos y factura ya enviada previamente (Clave: {}). Omitiendo reenvío innecesario.", claveIdempotencia);
            return;
        }

        NotificacionLogEntity notifEntity = logExistente.orElseGet(() -> NotificacionLogEntity.builder()
                .idOrden(idOrden)
                .destinatario(email.trim().toLowerCase())
                .tipoNotificacion("ACCESOS_Y_FACTURA")
                .claveIdempotencia(claveIdempotencia)
                .estado("PROCESANDO")
                .intentos(0)
                .fechaCreacion(OffsetDateTime.now())
                .build());

        notifEntity.setEstado("PROCESANDO");
        notifEntity.setIntentos(notifEntity.getIntentos() + 1);
        try {
            notifEntity = notificacionLogRepository.save(notifEntity);
        } catch (Exception ex) {
            log.warn("Colisión concurrente al registrar intento de notificación para {}: {}", claveIdempotencia, ex.getMessage());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Lysandri Executive Education");
            helper.setTo(email);
            helper.setSubject("¡Bienvenido a Lysandri Executive! Accesos a tu curso y Comprobante de Pago");

            String htmlBody = construirPlantillaHtml(nombre, cursoTitulo, email, moodlePassword, comprobante);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            notifEntity.setEstado("ENVIADO");
            notifEntity.setFechaEnvio(OffsetDateTime.now());
            notifEntity.setUltimoError(null);
            notificacionLogRepository.save(notifEntity);
            log.info("Correo de accesos y comprobante enviado exitosamente para clave: {}", claveIdempotencia);
        } catch (Exception e) {
            notifEntity.setEstado("FALLIDO");
            notifEntity.setUltimoError(com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
            try {
                notificacionLogRepository.save(notifEntity);
            } catch (Exception ignored) {}
            log.error("Fallo al enviar correo de accesos (intento {}): {}", notifEntity.getIntentos(), com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
            // No propagamos excepción fatal para no revertir la transacción del pago ya confirmado
        }
    }

    private String construirPlantillaHtml(
            String nombre,
            String cursoTitulo,
            String email,
            String password,
            ComprobantePago comprobante) {

        String fechaEmisionStr = comprobante != null && comprobante.getFechaEmision() != null
                ? comprobante.getFechaEmision().format(DATE_FORMATTER)
                : "Fecha de hoy";

        String serieCorrelativo = comprobante != null
                ? String.format("%s-%08d", comprobante.getSerie(), comprobante.getCorrelativo())
                : "N/A";

        String tipoDoc = comprobante != null ? comprobante.getTipo().name() : "COMPROBANTE";
        String subtotalStr = comprobante != null ? comprobante.getMontoSubtotal().toString() : "0.00";
        String igvStr = comprobante != null ? comprobante.getMontoIgv().toString() : "0.00";
        String totalStr = comprobante != null ? comprobante.getMontoTotal().toString() : "0.00";
        String monedaStr = comprobante != null ? comprobante.getMoneda() : "PEN";
        String pdfUrl = comprobante != null && comprobante.getPdfUrl() != null ? comprobante.getPdfUrl() : "#";

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #0b0f19; color: #e2e8f0; margin: 0; padding: 20px; }
                        .container { max-width: 620px; margin: 0 auto; background: #131b2e; border: 1px solid #1e293b; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                        .header { background: linear-gradient(135deg, #1e3a8a 0%%, #3b82f6 100%%); padding: 30px 25px; text-align: center; }
                        .header h1 { margin: 0; color: #ffffff; font-size: 24px; font-weight: 700; letter-spacing: 0.5px; }
                        .header p { margin: 8px 0 0 0; color: #93c5fd; font-size: 14px; }
                        .content { padding: 30px 25px; }
                        .welcome { font-size: 16px; line-height: 1.6; color: #cbd5e1; margin-bottom: 20px; }
                        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 20px; margin-bottom: 20px; }
                        .card-title { font-size: 15px; font-weight: 600; color: #60a5fa; margin-top: 0; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
                        .data-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; }
                        .data-label { color: #94a3b8; }
                        .data-value { color: #f8fafc; font-weight: 600; font-family: monospace; }
                        .btn-cta { display: inline-block; background: #2563eb; color: #ffffff !important; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; text-align: center; margin: 15px 0 5px 0; }
                        .btn-cta:hover { background: #1d4ed8; }
                        .footer { background: #0f172a; padding: 20px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #1e293b; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>LYSANDRI EXECUTIVE</h1>
                            <p>Plataforma Directiva de Educación Tecnológica</p>
                        </div>
                        <div class="content">
                            <p class="welcome">Estimado(a) <strong>%s</strong>,</p>
                            <p class="welcome">Tu compra ha sido procesada con éxito. Ya te encuentras oficialmente matriculado(a) en el programa de especialización: <strong>%s</strong>.</p>
                            
                            <div class="card">
                                <h3 class="card-title">Tus Credenciales del Campus Virtual (Moodle)</h3>
                                <div class="data-row"><span class="data-label">Campus LMS:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Usuario / Email:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Contraseña Temporal:</span> <span class="data-value" style="color:#fbbf24;">%s</span></div>
                                <div style="text-align: center; margin-top: 15px;">
                                    <a href="%s" class="btn-cta" target="_blank">Ingresar al Campus Virtual</a>
                                </div>
                            </div>
                
                            <div class="card">
                                <h3 class="card-title">Comprobante de Pago Electrónico (%s)</h3>
                                <div class="data-row"><span class="data-label">Serie y Número:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Fecha de Emisión:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Subtotal (Base Imponible):</span> <span class="data-value">%s %s</span></div>
                                <div class="data-row"><span class="data-label">IGV (18%%):</span> <span class="data-value">%s %s</span></div>
                                <div class="data-row" style="border-top: 1px solid #334155; padding-top: 6px; margin-top: 6px;"><span class="data-label" style="color: #f8fafc; font-weight: bold;">Total Pagado:</span> <span class="data-value" style="color: #4ade80; font-size: 16px;">%s %s</span></div>
                                <div style="text-align: center; margin-top: 15px;">
                                    <a href="%s" style="color: #93c5fd; text-decoration: underline; font-size: 13px;">Descargar Comprobante en PDF</a>
                                </div>
                            </div>
                        </div>
                        <div class="footer">
                            <p>© 2026 Lysandri Executive Education. Todos los derechos reservados.<br>Este es un correo automático de confirmación de pago y matrícula.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                nombre,
                cursoTitulo,
                moodleUrl,
                email,
                password,
                moodleUrl,
                tipoDoc,
                serieCorrelativo,
                fechaEmisionStr,
                monedaStr, subtotalStr,
                monedaStr, igvStr,
                monedaStr, totalStr,
                pdfUrl
        );
    }

    @Override
    public void enviarBienvenidaCorporativa(
            String email,
            String nombre,
            String empresaNombre,
            String cursoTitulo,
            String username,
            String moodlePassword) {

        log.info("Preparando correo corporativo de bienvenida - Curso: {}, Empresa: {}", cursoTitulo, empresaNombre);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Lysandri Executive Education");
            helper.setTo(email);
            helper.setSubject("¡Bienvenido(a) al Campus Virtual! Acceso a tu Programa Corporativo en Lysandri");

            String htmlBody = construirPlantillaCorporativaHtml(nombre, empresaNombre, cursoTitulo, username, moodlePassword);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Correo corporativo de bienvenida enviado exitosamente para empresa: {}", empresaNombre);
        } catch (Exception e) {
            log.error("Fallo al enviar correo corporativo: {}", com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
        }
    }

    private String construirPlantillaCorporativaHtml(
            String nombre,
            String empresaNombre,
            String cursoTitulo,
            String username,
            String password) {

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #0b0f19; color: #e2e8f0; margin: 0; padding: 20px; }
                        .container { max-width: 620px; margin: 0 auto; background: #131b2e; border: 1px solid #1e293b; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                        .header { background: linear-gradient(135deg, #1e1b4b 0%%, #4338ca 100%%); padding: 30px 25px; text-align: center; }
                        .header h1 { margin: 0; color: #ffffff; font-size: 24px; font-weight: 700; }
                        .header p { margin: 8px 0 0 0; color: #c7d2fe; font-size: 14px; }
                        .content { padding: 30px 25px; }
                        .welcome { font-size: 15px; line-height: 1.6; color: #cbd5e1; margin-bottom: 20px; }
                        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 20px; margin-bottom: 20px; }
                        .card-title { font-size: 14px; font-weight: 600; color: #818cf8; margin-top: 0; margin-bottom: 12px; text-transform: uppercase; }
                        .data-row { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 14px; }
                        .data-label { color: #94a3b8; }
                        .data-value { color: #f8fafc; font-weight: 600; font-family: monospace; }
                        .btn-cta { display: inline-block; background: #4f46e5; color: #ffffff !important; padding: 12px 28px; text-decoration: none; border-radius: 6px; font-weight: 600; text-align: center; margin: 15px 0 5px 0; }
                        .footer { background: #0f172a; padding: 20px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #1e293b; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>LYSANDRI EXECUTIVE</h1>
                            <p>Programa Corporativo In-Company</p>
                        </div>
                        <div class="content">
                            <p class="welcome">Estimado(a) <strong>%s</strong>,<br><br>Tu organización <strong>%s</strong> ha coordinado tu matrícula en el programa ejecutivo <strong>%s</strong> a través de nuestra división corporativa.</p>
                            <div class="card">
                                <h3 class="card-title">Tus Credenciales del Campus Virtual Moodle</h3>
                                <div class="data-row"><span class="data-label">Plataforma:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Usuario:</span> <span class="data-value">%s</span></div>
                                <div class="data-row"><span class="data-label">Contraseña Temporal:</span> <span class="data-value">%s</span></div>
                                <div style="text-align: center; margin-top: 15px;">
                                    <a href="%s" class="btn-cta">Ingresar al Campus Virtual</a>
                                </div>
                            </div>
                        </div>
                        <div class="footer">
                            <p>© 2026 Lysandri Executive Education. Todos los derechos reservados.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                nombre,
                empresaNombre != null && !empresaNombre.isBlank() ? empresaNombre : "tu empresa",
                cursoTitulo,
                moodleUrl,
                username,
                password,
                moodleUrl
        );
    }

    private final java.util.concurrent.ConcurrentMap<String, jakarta.mail.Transport> transportesActivos = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void enviarEnlaceActivacion(String email, String nombre, String enlaceActivacion) {
        String idIntentoFallback = "legacy-attempt-" + java.util.UUID.randomUUID();
        enviarEnlaceActivacion(email, nombre, enlaceActivacion, idIntentoFallback);
    }

    @Override
    public void enviarEnlaceActivacion(String email, String nombre, String enlaceActivacion, String idIntento) {
        String attemptId = (idIntento != null && !idIntento.isBlank()) ? idIntento.trim() : ("attempt-" + java.util.UUID.randomUUID());
        log.info("Preparando despacho seguro de correo de activación para intento: {}", attemptId);

        jakarta.mail.Transport transport = null;
        AbortableSocketFactory.getInstance().setCurrentAttemptId(attemptId);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Lysandri Executive Education");
            helper.setTo(email);
            helper.setSubject("Activa tu cuenta en Lysandri Executive Education");

            String htmlBody = construirPlantillaActivacionHtml(nombre, enlaceActivacion);
            helper.setText(htmlBody, true);
            message.saveChanges();

            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl jms) {
                jms.getJavaMailProperties().putIfAbsent("mail.smtp.socketFactory", AbortableSocketFactory.getInstance());
                jakarta.mail.Session session = jms.getSession();
                String protocol = jms.getProtocol() != null ? jms.getProtocol() : "smtp";
                transport = session.getTransport(protocol);
                transportesActivos.put(attemptId, transport);

                if (jms.getUsername() != null && !jms.getUsername().isBlank()) {
                    transport.connect(jms.getHost(), jms.getPort(), jms.getUsername(), jms.getPassword());
                } else if (jms.getHost() != null) {
                    transport.connect(jms.getHost(), jms.getPort(), null, null);
                } else {
                    transport.connect();
                }

                transport.sendMessage(message, message.getAllRecipients());
                log.info("Correo de activación enviado exitosamente para intento: {}", attemptId);
            } else {
                mailSender.send(message);
                log.info("Correo de activación enviado exitosamente para intento: {}", attemptId);
            }
        } catch (Exception e) {
            log.error("Fallo al enviar correo de activación para intento {}: {}", attemptId, com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()));
            throw new RuntimeException("No se pudo enviar el correo de activación para intento " + attemptId + ": " + com.lysandri.security.SanitizerUtils.sanitizeMessage(e.getMessage()), e);
        } finally {
            AbortableSocketFactory.getInstance().clearCurrentAttemptId();
            if (transport != null) {
                transportesActivos.remove(attemptId, transport);
                try {
                    transport.close();
                } catch (Exception ignored) {}
            }
            AbortableSocketFactory.getInstance().limpiarIntento(attemptId);
        }
    }

    @Override
    public void abortarEnvio(String idIntento) {
        if (idIntento == null || idIntento.isBlank()) return;
        String attemptId = idIntento.trim();
        boolean socketAborted = AbortableSocketFactory.getInstance().abortSocket(attemptId);
        if (socketAborted) {
            log.warn("Socket TCP de transporte SMTP abortado activamente para intento: {}", attemptId);
        }
        jakarta.mail.Transport transport = transportesActivos.get(attemptId);
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception ignored) {}
            transportesActivos.remove(attemptId, transport);
        }
    }

    private String construirPlantillaActivacionHtml(String nombre, String enlaceActivacion) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #0b0f19; color: #e2e8f0; margin: 0; padding: 20px; }
                        .container { max-width: 620px; margin: 0 auto; background: #131b2e; border: 1px solid #1e293b; border-radius: 12px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.5); }
                        .header { background: linear-gradient(135deg, #1e3a8a 0%%, #3b82f6 100%%); padding: 30px 25px; text-align: center; }
                        .header h1 { margin: 0; color: #ffffff; font-size: 24px; font-weight: 700; letter-spacing: 0.5px; }
                        .header p { margin: 8px 0 0 0; color: #93c5fd; font-size: 14px; }
                        .content { padding: 30px 25px; }
                        .welcome { font-size: 16px; line-height: 1.6; color: #cbd5e1; margin-bottom: 20px; }
                        .card { background: #1e293b; border: 1px solid #334155; border-radius: 8px; padding: 20px; margin-bottom: 20px; text-align: center; }
                        .btn-cta { display: inline-block; background: #2563eb; color: #ffffff !important; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px; margin: 15px 0; }
                        .btn-cta:hover { background: #1d4ed8; }
                        .note { font-size: 13px; color: #94a3b8; line-height: 1.5; margin-top: 15px; }
                        .footer { background: #0f172a; padding: 20px; text-align: center; font-size: 12px; color: #64748b; border-top: 1px solid #1e293b; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>LYSANDRI EXECUTIVE</h1>
                            <p>Activación de Cuenta de Usuario</p>
                        </div>
                        <div class="content">
                            <p class="welcome">Estimado(a) <strong>%s</strong>,</p>
                            <p class="welcome">Para completar la activación de tu cuenta y definir tu contraseña de acceso a la plataforma ejecutiva, por favor haz clic en el siguiente botón:</p>
                            
                            <div class="card">
                                <a href="%s" class="btn-cta" target="_blank">Activar Mi Cuenta</a>
                                <p class="note">Este enlace es de un solo uso y caduca en 48 horas.<br>Si el botón no funciona, copia y pega este enlace en tu navegador:</p>
                                <p class="note" style="word-break: break-all; color: #60a5fa;">%s</p>
                            </div>
                            
                            <p class="welcome">Si no realizaste esta solicitud, puedes ignorar este mensaje.</p>
                        </div>
                        <div class="footer">
                            <p>© 2026 Lysandri Executive Education. Todos los derechos reservados.<br>Este es un correo seguro de activación de cuenta.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                nombre != null && !nombre.isBlank() ? nombre : "Cliente",
                enlaceActivacion,
                enlaceActivacion
        );
    }
}
