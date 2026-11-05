package com.lysandri.adapters.out.database.entity;

import com.lysandri.domain.model.whatsapp.DireccionMensajeWhatsApp;
import com.lysandri.domain.model.whatsapp.EstadoMensajeWhatsApp;
import com.lysandri.domain.model.whatsapp.TipoMensajeWhatsApp;
import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "whatsapp_mensaje", indexes = {
        @Index(name = "idx_whatsapp_id_proveedor", columnList = "id_proveedor"),
        @Index(name = "idx_whatsapp_id_solicitud", columnList = "id_solicitud"),
        @Index(name = "idx_whatsapp_telefono_remitente", columnList = "telefono_remitente"),
        @Index(name = "idx_whatsapp_telefono_destinatario", columnList = "telefono_destinatario"),
        @Index(name = "idx_whatsapp_timestamp", columnList = "timestamp_mensaje"),
        @Index(name = "idx_whatsapp_token_intento", columnList = "token_intento"),
        @Index(name = "idx_whatsapp_bloqueado_hasta", columnList = "bloqueado_hasta")
})
public class WhatsAppMensajeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_proveedor", unique = true, length = 120)
    private String idProveedor;

    @Column(name = "id_solicitud", unique = true, length = 100)
    private String idSolicitud;

    @Column(name = "telefono_remitente", nullable = false, length = 50)
    private String telefonoRemitente;

    @Column(name = "telefono_destinatario", nullable = false, length = 50)
    private String telefonoDestinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "direccion", nullable = false, length = 20)
    private DireccionMensajeWhatsApp direccion;

    @Column(name = "contenido", columnDefinition = "TEXT")
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoMensajeWhatsApp tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoMensajeWhatsApp estado;

    @Column(name = "timestamp_mensaje", nullable = false)
    private OffsetDateTime timestampMensaje;

    @Column(name = "operador", length = 100)
    private String operador;

    @Column(name = "token_intento", length = 100)
    private String tokenIntento;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @Column(name = "intento_count", nullable = false)
    private Integer intentoCount = 0;

    @Column(name = "error_detalle", columnDefinition = "TEXT")
    private String errorDetalle;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    public WhatsAppMensajeEntity() {
    }

    public WhatsAppMensajeEntity(Long id, String idProveedor, String idSolicitud, String telefonoRemitente,
                                 String telefonoDestinatario, DireccionMensajeWhatsApp direccion, String contenido,
                                 TipoMensajeWhatsApp tipo, EstadoMensajeWhatsApp estado, OffsetDateTime timestampMensaje,
                                 String operador, String tokenIntento, OffsetDateTime bloqueadoHasta,
                                 Integer intentoCount, String errorDetalle, OffsetDateTime fechaCreacion) {
        this.id = id;
        this.idProveedor = idProveedor;
        this.idSolicitud = idSolicitud;
        this.telefonoRemitente = telefonoRemitente;
        this.telefonoDestinatario = telefonoDestinatario;
        this.direccion = direccion;
        this.contenido = contenido;
        this.tipo = tipo;
        this.estado = estado;
        this.timestampMensaje = timestampMensaje;
        this.operador = operador;
        this.tokenIntento = tokenIntento;
        this.bloqueadoHasta = bloqueadoHasta;
        this.intentoCount = intentoCount != null ? intentoCount : 0;
        this.errorDetalle = errorDetalle;
        this.fechaCreacion = fechaCreacion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String idProveedor;
        private String idSolicitud;
        private String telefonoRemitente;
        private String telefonoDestinatario;
        private DireccionMensajeWhatsApp direccion;
        private String contenido;
        private TipoMensajeWhatsApp tipo;
        private EstadoMensajeWhatsApp estado;
        private OffsetDateTime timestampMensaje;
        private String operador;
        private String tokenIntento;
        private OffsetDateTime bloqueadoHasta;
        private Integer intentoCount = 0;
        private String errorDetalle;
        private OffsetDateTime fechaCreacion;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder idProveedor(String idProveedor) { this.idProveedor = idProveedor; return this; }
        public Builder idSolicitud(String idSolicitud) { this.idSolicitud = idSolicitud; return this; }
        public Builder telefonoRemitente(String telefonoRemitente) { this.telefonoRemitente = telefonoRemitente; return this; }
        public Builder telefonoDestinatario(String telefonoDestinatario) { this.telefonoDestinatario = telefonoDestinatario; return this; }
        public Builder direccion(DireccionMensajeWhatsApp direccion) { this.direccion = direccion; return this; }
        public Builder contenido(String contenido) { this.contenido = contenido; return this; }
        public Builder tipo(TipoMensajeWhatsApp tipo) { this.tipo = tipo; return this; }
        public Builder estado(EstadoMensajeWhatsApp estado) { this.estado = estado; return this; }
        public Builder timestampMensaje(OffsetDateTime timestampMensaje) { this.timestampMensaje = timestampMensaje; return this; }
        public Builder operador(String operador) { this.operador = operador; return this; }
        public Builder tokenIntento(String tokenIntento) { this.tokenIntento = tokenIntento; return this; }
        public Builder bloqueadoHasta(OffsetDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; return this; }
        public Builder intentoCount(Integer intentoCount) { this.intentoCount = intentoCount != null ? intentoCount : 0; return this; }
        public Builder errorDetalle(String errorDetalle) { this.errorDetalle = errorDetalle; return this; }
        public Builder fechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; return this; }

        public WhatsAppMensajeEntity build() {
            return new WhatsAppMensajeEntity(id, idProveedor, idSolicitud, telefonoRemitente, telefonoDestinatario,
                    direccion, contenido, tipo, estado, timestampMensaje, operador, tokenIntento, bloqueadoHasta,
                    intentoCount, errorDetalle, fechaCreacion);
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIdProveedor() { return idProveedor; }
    public void setIdProveedor(String idProveedor) { this.idProveedor = idProveedor; }

    public String getIdSolicitud() { return idSolicitud; }
    public void setIdSolicitud(String idSolicitud) { this.idSolicitud = idSolicitud; }

    public String getTelefonoRemitente() { return telefonoRemitente; }
    public void setTelefonoRemitente(String telefonoRemitente) { this.telefonoRemitente = telefonoRemitente; }

    public String getTelefonoDestinatario() { return telefonoDestinatario; }
    public void setTelefonoDestinatario(String telefonoDestinatario) { this.telefonoDestinatario = telefonoDestinatario; }

    public DireccionMensajeWhatsApp getDireccion() { return direccion; }
    public void setDireccion(DireccionMensajeWhatsApp direccion) { this.direccion = direccion; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public TipoMensajeWhatsApp getTipo() { return tipo; }
    public void setTipo(TipoMensajeWhatsApp tipo) { this.tipo = tipo; }

    public EstadoMensajeWhatsApp getEstado() { return estado; }
    public void setEstado(EstadoMensajeWhatsApp estado) { this.estado = estado; }

    public OffsetDateTime getTimestampMensaje() { return timestampMensaje; }
    public void setTimestampMensaje(OffsetDateTime timestampMensaje) { this.timestampMensaje = timestampMensaje; }

    public String getOperador() { return operador; }
    public void setOperador(String operador) { this.operador = operador; }

    public String getTokenIntento() { return tokenIntento; }
    public void setTokenIntento(String tokenIntento) { this.tokenIntento = tokenIntento; }

    public OffsetDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(OffsetDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }

    public Integer getIntentoCount() { return intentoCount; }
    public void setIntentoCount(Integer intentoCount) { this.intentoCount = intentoCount != null ? intentoCount : 0; }

    public String getErrorDetalle() { return errorDetalle; }
    public void setErrorDetalle(String errorDetalle) { this.errorDetalle = errorDetalle; }

    public OffsetDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(OffsetDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }
}
