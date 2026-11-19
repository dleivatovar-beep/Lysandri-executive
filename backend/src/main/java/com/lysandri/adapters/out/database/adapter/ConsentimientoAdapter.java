package com.lysandri.adapters.out.database.adapter;
import com.lysandri.domain.ports.out.ConsentimientoPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository @RequiredArgsConstructor
public class ConsentimientoAdapter implements ConsentimientoPort {
    private final JdbcTemplate jdbc;
    @Override
    public void registrar(long usuario, String version) {
        jdbc.update("INSERT INTO consentimiento_usuario(id_user,version,terminos,privacidad) VALUES (?,?,true,true)",usuario,version);
    }

    @Override
    public boolean existeParaUsuario(long usuario) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM consentimiento_usuario WHERE id_user = ?)", Boolean.class, usuario));
    }
}
