package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.RolUsuario;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "USUARIO")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_user")
    private Long idUser;

    @Column(name = "moodle_user_id", unique = true)
    private Long moodleUserId;

    @Column(name = "nombres", nullable = false, length = 100)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 100)
    private String apellidos;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "passw", nullable = false)
    private String passw;

    @Column(name = "telefono", length = 25)
    private String telefono;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    @Builder.Default
    private RolUsuario rol = RolUsuario.CLIENTE;

    @Column(name = "activo", nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "fecha_actualizacion")
    @Builder.Default
    private OffsetDateTime fechaActualizacion = OffsetDateTime.now();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return this.passw;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.activo;
    }
}
