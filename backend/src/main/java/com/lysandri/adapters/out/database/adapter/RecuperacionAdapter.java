package com.lysandri.adapters.out.database.adapter;

import com.lysandri.domain.ports.out.RecuperacionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@Repository
public class RecuperacionAdapter implements RecuperacionPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaccion;

    public RecuperacionAdapter(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transaccion = new TransactionTemplate(manager);
    }

    @Override
    public boolean guardarSolicitud(String correo, String tokenHash, String enlaceCifrado) {
        return Boolean.TRUE.equals(transaccion.execute(estado -> {
            var usuarios = jdbc.queryForList("""
                SELECT id_user FROM usuario WHERE lower(email) = ?
                AND activo = true AND bloqueado_admin = false AND pendiente_activacion = false FOR UPDATE
                """, Long.class, correo);
            if (usuarios.isEmpty()) return false;
            long id = usuarios.get(0);
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM recuperacion_cuenta WHERE id_user = ?
                AND fecha_solicitud > clock_timestamp() - INTERVAL '60 seconds')
                """, Boolean.class, id))) return false;
            jdbc.update("UPDATE recuperacion_cuenta SET fecha_revocacion = clock_timestamp(), enlace_cifrado = NULL WHERE id_user = ? AND fecha_uso IS NULL AND fecha_revocacion IS NULL", id);
            jdbc.update("INSERT INTO recuperacion_cuenta(id_user, token_hash, enlace_cifrado) VALUES (?, ?, ?)", id, tokenHash, enlaceCifrado);
            return true;
        }));
    }

    @Override
    public boolean confirmar(String tokenHash, String passwordHash) {
        return Boolean.TRUE.equals(transaccion.execute(estado -> {
            var ids = jdbc.queryForList("SELECT id_user FROM recuperacion_cuenta WHERE token_hash = ?", Long.class, tokenHash);
            if (ids.isEmpty()) return false;
            long id = ids.get(0);
            // La solicitud y el consumo bloquean primero al usuario para conservar el mismo orden.
            var usuarios = jdbc.queryForList("SELECT id_user FROM usuario WHERE id_user = ? AND activo = true AND bloqueado_admin = false AND pendiente_activacion = false FOR UPDATE", Long.class, id);
            if (usuarios.isEmpty()) return false;
            int consumidos = jdbc.update("""
                UPDATE recuperacion_cuenta SET fecha_uso = clock_timestamp(), enlace_cifrado = NULL
                WHERE token_hash = ? AND fecha_uso IS NULL AND fecha_revocacion IS NULL
                AND fecha_expiracion > clock_timestamp()
                """, tokenHash);
            if (consumidos != 1) return false;
            jdbc.update("UPDATE usuario SET passw = ?, version_credenciales = version_credenciales + 1, fecha_actualizacion = clock_timestamp() WHERE id_user = ?", passwordHash, id);
            jdbc.update("UPDATE recuperacion_cuenta SET fecha_revocacion = clock_timestamp(), enlace_cifrado = NULL WHERE id_user = ? AND fecha_uso IS NULL AND fecha_revocacion IS NULL", id);
            jdbc.update("UPDATE usuario_activacion_token SET revocado = true, fecha_revocacion = clock_timestamp() WHERE id_user = ? AND usado = false AND revocado = false", id);
            jdbc.update("INSERT INTO aviso_cuenta(id_user,tipo) VALUES (?, 'CUENTA_RECUPERADA')", id);
            return true;
        }));
    }

    @Override
    public List<CorreoPendiente> tomarPendientes() {
        return transaccion.execute(estado -> jdbc.query("""
            UPDATE recuperacion_cuenta r SET estado_correo = 'ENVIANDO'
            WHERE r.id_recuperacion IN (
                SELECT c.id_recuperacion FROM recuperacion_cuenta c JOIN usuario u ON u.id_user = c.id_user
                WHERE c.estado_correo = 'PENDIENTE' AND c.fecha_uso IS NULL AND c.fecha_revocacion IS NULL
                AND c.fecha_expiracion > clock_timestamp() AND u.activo = true
                AND u.bloqueado_admin = false AND u.pendiente_activacion = false
                ORDER BY c.id_recuperacion LIMIT 5 FOR UPDATE OF c SKIP LOCKED)
            RETURNING r.id_recuperacion, (SELECT email FROM usuario WHERE id_user = r.id_user),
            (SELECT nombres FROM usuario WHERE id_user = r.id_user), r.enlace_cifrado
            """, (rs, fila) -> new CorreoPendiente(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4))));
    }

    @Override
    public void registrarEnvio(long id, boolean enviado) {
        jdbc.update("UPDATE recuperacion_cuenta SET estado_correo = ?, enlace_cifrado = CASE WHEN ? THEN NULL ELSE enlace_cifrado END WHERE id_recuperacion = ? AND estado_correo = 'ENVIANDO'",
                enviado ? "ENVIADO" : "REVISAR", enviado, id);
    }
}
