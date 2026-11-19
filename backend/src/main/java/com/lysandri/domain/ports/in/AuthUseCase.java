package com.lysandri.domain.ports.in;

import com.lysandri.domain.model.Usuario;
import com.lysandri.domain.model.RolUsuario;

public interface AuthUseCase {

    record RegisterCommand(
            String nombres,
            String apellidos,
            String email,
            String password,
            String telefono,
            boolean aceptaTerminos,
            boolean aceptaPrivacidad,
            String version
    ) {
        public RegisterCommand(String nombres,String apellidos,String email,String password,String telefono) {
            this(nombres,apellidos,email,password,telefono,false,false,"");
        }
    }

    record LoginCommand(
            String email,
            String password
    ) {}

    record AuthResponse(
            String token,
            Long idUser,
            String email,
            String nombreCompleto,
            String rol,
            Long moodleUserId,
            boolean pendienteActivacion,
            String mensaje
    ) {
        public AuthResponse(String token,Long idUser,String email,String nombreCompleto,String rol,Long moodleUserId) {
            this(token,idUser,email,nombreCompleto,rol,moodleUserId,false,"");
        }
    }

    record ActivarCuentaCommand(
            String token,
            String nuevaPassword
    ) {}

    record SolicitarActivacionCommand(
            String email
    ) {}

    record MensajePublicoResponse(
            String mensaje
    ) {}

    AuthResponse register(RegisterCommand command);

    AuthResponse registerInternal(RegisterCommand command, RolUsuario role);

    AuthResponse login(LoginCommand command);

    AuthResponse activarCuenta(ActivarCuentaCommand command);

    MensajePublicoResponse solicitarNuevoEnlaceActivacion(SolicitarActivacionCommand command);

    Usuario obtenerUsuarioAutenticado(String email);
}
