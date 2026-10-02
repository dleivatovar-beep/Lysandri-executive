package com.lysandri.adapters.out.notification;

import com.lysandri.domain.model.ComprobantePago;
import com.lysandri.domain.ports.out.NotificationPort;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationAdapter implements NotificationPort {

    private final JavaMailSender mailSender;

    @Value("${moodle.url:http://localhost:8080}")
    private String moodleUrl;

    @Value("${spring.mail.username:no-reply@lysandri.com}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public void enviarAccesosYFactura(
            String email,
            String nombre,
            String cursoTitulo,
            String moodlePassword,
            ComprobantePago comprobante) {

        log.info("Preparando envío de correo de accesos y comprobante para: {} ({})", email, cursoTitulo);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, "Lysandri Executive Education");
            helper.setTo(email);
            helper.setSubject("¡Bienvenido a Lysandri Executive! Accesos a tu curso y Comprobante de Pago");

            String htmlBody = construirPlantillaHtml(nombre, cursoTitulo, email, moodlePassword, comprobante);
            helper.setText(htmlBody, true);

            mailSender.send(message);
            log.info("Correo de accesos y comprobante enviado exitosamente a: {}", email);
        } catch (Exception e) {
            log.error("Fallo al enviar correo a {} (posible falta de servidor SMTP en entorno actual): {}", email, e.getMessage());
            // No propagamos excepción fatal para no revertir la transacción del pago ya confirmado en Stripe
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
}
