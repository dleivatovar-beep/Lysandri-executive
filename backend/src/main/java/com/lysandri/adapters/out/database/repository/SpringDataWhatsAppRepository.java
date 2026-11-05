package com.lysandri.adapters.out.database.repository;

import com.lysandri.adapters.out.database.entity.WhatsAppMensajeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpringDataWhatsAppRepository extends JpaRepository<WhatsAppMensajeEntity, Long> {

    boolean existsByIdProveedor(String idProveedor);

    Optional<WhatsAppMensajeEntity> findByIdProveedor(String idProveedor);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM WhatsAppMensajeEntity m WHERE m.idProveedor = :idProveedor")
    Optional<WhatsAppMensajeEntity> findByIdProveedorForUpdate(@Param("idProveedor") String idProveedor);

    boolean existsByIdSolicitud(String idSolicitud);

    Optional<WhatsAppMensajeEntity> findByIdSolicitud(String idSolicitud);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM WhatsAppMensajeEntity m WHERE m.idSolicitud = :idSolicitud")
    Optional<WhatsAppMensajeEntity> findByIdSolicitudForUpdate(@Param("idSolicitud") String idSolicitud);

    @Modifying
    @Query(value = "INSERT INTO whatsapp_mensaje (" +
            "id_solicitud, telefono_remitente, telefono_destinatario, direccion, " +
            "contenido, tipo, estado, timestamp_mensaje, operador, token_intento, intento_count" +
            ") VALUES (" +
            ":idSolicitud, 'LYSANDRI_OFFICIAL', :destinatario, 'SALIENTE', " +
            ":contenido, 'TEXTO', 'PENDIENTE_ENVIO', NOW(), :operador, NULL, 0" +
            ") ON CONFLICT (id_solicitud) DO NOTHING", nativeQuery = true)
    int insertarIntencionSiNoExiste(
            @Param("idSolicitud") String idSolicitud,
            @Param("destinatario") String destinatario,
            @Param("contenido") String contenido,
            @Param("operador") String operador
    );

    @Modifying
    @Query("UPDATE WhatsAppMensajeEntity m SET m.estado = com.lysandri.domain.model.whatsapp.EstadoMensajeWhatsApp.RESULTADO_INCIERTO, " +
            "m.errorDetalle = 'Recuperado tras reinicio de sistema en estado no confirmado con el proveedor', " +
            "m.tokenIntento = NULL, m.bloqueadoHasta = NULL " +
            "WHERE m.estado = com.lysandri.domain.model.whatsapp.EstadoMensajeWhatsApp.EN_PROCESO_ENVIO")
    int reconciliarEnProcesoTrasReinicio();

    @Query("SELECT m FROM WhatsAppMensajeEntity m WHERE m.telefonoRemitente = :telefono OR m.telefonoDestinatario = :telefono ORDER BY m.timestampMensaje ASC")
    List<WhatsAppMensajeEntity> findAllByTelefono(@Param("telefono") String telefono);
}
