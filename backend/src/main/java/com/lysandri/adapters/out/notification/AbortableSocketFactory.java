package com.lysandri.adapters.out.notification;

import lombok.extern.slf4j.Slf4j;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * SocketFactory personalizado que registra sockets SMTP activos por identificador único de intento (idIntento),
 * permitiendo su cierre forzado e inmediato (abort) ante el agotamiento del presupuesto global o pérdida de reserva,
 * sin depender de Thread.interrupt() ni quedar bloqueado por sincronización interna de Jakarta Mail.
 *
 * GARANTÍAS DE AISLAMIENTO:
 * 1. Recursos indexados exclusivamente por idIntento inmutable. Dos envíos al mismo correo poseen sockets independientes.
 * 2. Eliminación condicional por identidad de recurso (socket), impidiendo que una limpieza tardía cierre o remueva otro intento.
 * 3. Detección de aborto previo a la inicialización: si un intento es abortado antes de crear su socket TCP, el socket
 *    se cierra inmediatamente al instanciarse y se aborta con SocketException.
 * 4. Logs técnicos que nunca exponen direcciones de correo electrónico, nombres ni credenciales.
 */
@Slf4j
public class AbortableSocketFactory extends SocketFactory {

    private static final AbortableSocketFactory INSTANCE = new AbortableSocketFactory();

    public static AbortableSocketFactory getInstance() {
        return INSTANCE;
    }

    private final SocketFactory defaultFactory = SocketFactory.getDefault();
    private final ConcurrentMap<String, Socket> activeSocketsByAttempt = new ConcurrentHashMap<>();
    private final Set<String> abortedAttempts = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<String> currentAttemptId = new ThreadLocal<>();

    public void setCurrentAttemptId(String idIntento) {
        if (idIntento != null && !idIntento.isBlank()) {
            currentAttemptId.set(idIntento.trim());
        } else {
            currentAttemptId.remove();
        }
    }

    public void clearCurrentAttemptId() {
        currentAttemptId.remove();
    }

    public String getCurrentAttemptId() {
        return currentAttemptId.get();
    }

    private Socket track(Socket socket) throws IOException {
        if (socket == null) {
            return null;
        }
        String idIntento = currentAttemptId.get();
        if (idIntento != null) {
            // Verificar si el aborto fue solicitado antes de la creación o registro del socket
            if (abortedAttempts.contains(idIntento)) {
                log.warn("Intento {} ya fue marcado como abortado antes de crear el socket. Cerrando inmediatamente.", idIntento);
                try {
                    socket.close();
                } catch (IOException ignored) {}
                throw new SocketException("Operación de red abortada antes de la inicialización del socket para intento: " + idIntento);
            }

            activeSocketsByAttempt.put(idIntento, socket);

            // Doble verificación: comprobar si se solicitó aborto durante la asignación
            if (abortedAttempts.contains(idIntento)) {
                activeSocketsByAttempt.remove(idIntento, socket);
                try {
                    socket.close();
                } catch (IOException ignored) {}
                throw new SocketException("Operación de red abortada concurrentemente durante registro de socket para intento: " + idIntento);
            }
        }
        return socket;
    }

    /**
     * Aborta forzadamente el socket TCP asociado al intento indicado.
     * Si el socket aún no fue creado, registra el intento en abortedAttempts para que falle inmediatamente al crearse.
     *
     * @param idIntento Identificador técnico único del intento.
     * @return true si se cerró un socket activo existente, false si no había socket creado o ya estaba cerrado.
     */
    public boolean abortSocket(String idIntento) {
        if (idIntento == null || idIntento.isBlank()) {
            return false;
        }
        String key = idIntento.trim();
        abortedAttempts.add(key);

        Socket socket = activeSocketsByAttempt.remove(key);
        if (socket != null && !socket.isClosed()) {
            log.warn("Abortando forzadamente socket TCP activo para intento: {}", key);
            try {
                socket.close();
                return true;
            } catch (IOException e) {
                log.debug("Excepción al forzar cierre de socket TCP para intento {}: {}", key, e.getMessage());
            }
        }
        return socket != null;
    }

    /**
     * Desregistra un socket condicionalmente por identidad del recurso,
     * garantizando que una limpieza tardía no elimine el socket de un intento posterior.
     */
    public void unregisterSocket(String idIntento, Socket socket) {
        if (idIntento != null && socket != null) {
            activeSocketsByAttempt.remove(idIntento.trim(), socket);
        }
    }

    /**
     * Limpia completamente el estado técnico del intento al culminar su ciclo de vida.
     */
    public void limpiarIntento(String idIntento) {
        if (idIntento != null) {
            String key = idIntento.trim();
            activeSocketsByAttempt.remove(key);
            abortedAttempts.remove(key);
        }
    }

    public boolean tieneSocketActivo(String idIntento) {
        if (idIntento == null) return false;
        Socket s = activeSocketsByAttempt.get(idIntento.trim());
        return s != null && !s.isClosed();
    }

    public boolean esIntentoAbortado(String idIntento) {
        return idIntento != null && abortedAttempts.contains(idIntento.trim());
    }

    public int getCantidadSocketsActivos() {
        return activeSocketsByAttempt.size();
    }

    public int getCantidadIntentosAbortados() {
        return abortedAttempts.size();
    }

    public void reset() {
        activeSocketsByAttempt.values().forEach(s -> {
            try {
                if (s != null && !s.isClosed()) s.close();
            } catch (IOException ignored) {}
        });
        activeSocketsByAttempt.clear();
        abortedAttempts.clear();
        currentAttemptId.remove();
    }

    @Override
    public Socket createSocket() throws IOException {
        return track(defaultFactory.createSocket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return track(defaultFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return track(defaultFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return track(defaultFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return track(defaultFactory.createSocket(address, port, localAddress, localPort));
    }
}
