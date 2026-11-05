package com.lysandri.domain.model.whatsapp;

public enum EstadoMensajeWhatsApp {
    RECIBIDO(0),
    PENDIENTE_ENVIO(1),
    EN_PROCESO_ENVIO(2),
    ENVIADO(3),
    ENTREGADO(4),
    LEIDO(5),
    RESULTADO_INCIERTO(98),
    FALLIDO(99);

    private final int precedencia;

    EstadoMensajeWhatsApp(int precedencia) {
        this.precedencia = precedencia;
    }

    public int getPrecedencia() {
        return precedencia;
    }

    /**
     * Comprueba si es válido transicionar al nuevo estado.
     * Evita que eventos desordenados o retrasados sobrescriban estados más avanzados
     * (por ejemplo, que un webhook "ENTREGADO" recibido tarde sobreescriba "LEIDO").
     */
    public boolean puedeTransicionarA(EstadoMensajeWhatsApp nuevoEstado) {
        if (nuevoEstado == null) {
            return false;
        }
        if (this == FALLIDO) {
            return false; // Estado terminal de fallo confirmado
        }
        if (this == RECIBIDO) {
            return false; // Mensajes entrantes quedan en RECIBIDO
        }
        if (this == LEIDO) {
            return false; // Máximo estado de lectura: no puede retroceder
        }
        if (this == ENTREGADO) {
            return nuevoEstado == LEIDO; // No puede retroceder a ENVIADO ni EN_PROCESO
        }
        if (this == RESULTADO_INCIERTO || this == EN_PROCESO_ENVIO) {
            // Un resultado incierto o en proceso puede conciliarse si se confirma aceptación, entrega, lectura, fallo o incertidumbre
            return nuevoEstado == ENVIADO || nuevoEstado == ENTREGADO || nuevoEstado == LEIDO
                    || nuevoEstado == FALLIDO || nuevoEstado == RESULTADO_INCIERTO;
        }
        if (nuevoEstado == FALLIDO) {
            // Un mensaje solo puede fallar si aún no ha sido leído o entregado
            return this != LEIDO && this != ENTREGADO;
        }
        return nuevoEstado.precedencia >= this.precedencia;
    }
}
