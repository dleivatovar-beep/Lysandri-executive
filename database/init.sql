CREATE EXTENSION IF NOT EXISTS "vector";

DROP TABLE IF EXISTS SOLICITUD_INFORMACION CASCADE;
DROP TABLE IF EXISTS CHAT_MENSAJES CASCADE;
DROP TABLE IF EXISTS PROGRESO_LECCION CASCADE;
DROP TABLE IF EXISTS INSCRIPCIONES CASCADE;
DROP TABLE IF EXISTS SUSCRIPCIONES CASCADE;
DROP TABLE IF EXISTS DETALLE_ORDENES CASCADE;
DROP TABLE IF EXISTS ORDENES CASCADE;
DROP TABLE IF EXISTS LECCION CASCADE;
DROP TABLE IF EXISTS PROGRAMA CASCADE;
DROP TABLE IF EXISTS INSTRUCTOR CASCADE;
DROP TABLE IF EXISTS USUARIOS CASCADE;

CREATE TABLE USUARIOS (
    id_user SERIAL PRIMARY KEY,
    nombres VARCHAR(100) NOT NULL,
    apellidos VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    passw VARCHAR(255) NOT NULL,
    telefono VARCHAR(20),
    rol VARCHAR(20) NOT NULL CHECK (rol IN ('ESTUDIANTE', 'INSTRUCTOR', 'ADMIN'))
);

CREATE INDEX idx_usuarios_email ON USUARIOS(email);

CREATE TABLE INSTRUCTOR (
    id_instructor_dni VARCHAR(20) PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    especialidad VARCHAR(150),
    direccion_instructor VARCHAR(200)
);

CREATE INDEX idx_instructor_id_user ON INSTRUCTOR(id_user);

CREATE TABLE PROGRAMA (
    id_programa SERIAL PRIMARY KEY,
    id_instructor_dni VARCHAR(20) NOT NULL REFERENCES INSTRUCTOR(id_instructor_dni) ON DELETE CASCADE,
    titulo_programa VARCHAR(200) NOT NULL,
    duracion_programa VARCHAR(50),
    tipo_programa VARCHAR(50),
    level VARCHAR(20),
    fecha_inicio_global DATE,
    fecha_final_global DATE,
    requisitos TEXT,
    metodologia TEXT
);

CREATE INDEX idx_programa_id_instructor_dni ON PROGRAMA(id_instructor_dni);

CREATE TABLE LECCION (
    id_leccion SERIAL PRIMARY KEY,
    id_programa INT NOT NULL REFERENCES PROGRAMA(id_programa) ON DELETE CASCADE,
    titulo_leccion VARCHAR(200) NOT NULL,
    descripcion TEXT,
    tipo_contenido VARCHAR(50),
    media_url VARCHAR(255),
    duracion_leccion VARCHAR(20),
    orden INT DEFAULT 1
);

CREATE INDEX idx_leccion_id_programa ON LECCION(id_programa);
CREATE INDEX idx_leccion_orden ON LECCION(id_programa, orden);

CREATE TABLE ORDENES (
    id_ordenes SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    fecha_orden TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    estado_order VARCHAR(30),
    total DECIMAL(10, 2) NOT NULL,
    metodo_pago VARCHAR(50),
    direccion_envio VARCHAR(255)
);

CREATE INDEX idx_ordenes_id_user ON ORDENES(id_user);

CREATE TABLE DETALLE_ORDENES (
    id_detalle_ordenes SERIAL PRIMARY KEY,
    id_ordenes INT NOT NULL REFERENCES ORDENES(id_ordenes) ON DELETE CASCADE,
    id_programa INT NOT NULL REFERENCES PROGRAMA(id_programa) ON DELETE RESTRICT,
    precio_unitario DECIMAL(10, 2) NOT NULL,
    cantidad INT NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL
);

CREATE INDEX idx_detalle_ordenes_id_ordenes ON DETALLE_ORDENES(id_ordenes);
CREATE INDEX idx_detalle_ordenes_id_programa ON DETALLE_ORDENES(id_programa);

CREATE TABLE SUSCRIPCIONES (
    id_suscripcion SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    tipo_plan VARCHAR(50),
    fecha_inicio DATE NOT NULL,
    fecha_finalizacion DATE,
    estado VARCHAR(30)
);

CREATE INDEX idx_suscripciones_id_user ON SUSCRIPCIONES(id_user);

CREATE TABLE INSCRIPCIONES (
    id_inscripcion SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    id_programa INT NOT NULL REFERENCES PROGRAMA(id_programa) ON DELETE CASCADE,
    fecha_inscripcion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    fecha_limite_acceso DATE,
    estatus VARCHAR(30),
    porcentaje_progreso DECIMAL(5, 2) DEFAULT 0.00
);

CREATE INDEX idx_inscripciones_id_user ON INSCRIPCIONES(id_user);
CREATE INDEX idx_inscripciones_id_programa ON INSCRIPCIONES(id_programa);

CREATE TABLE PROGRESO_LECCION (
    id_progreso SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    id_leccion INT NOT NULL REFERENCES LECCION(id_leccion) ON DELETE CASCADE,
    completado BOOLEAN DEFAULT FALSE,
    fecha_completado TIMESTAMPTZ
);

CREATE INDEX idx_progreso_leccion_id_user ON PROGRESO_LECCION(id_user);
CREATE INDEX idx_progreso_leccion_id_leccion ON PROGRESO_LECCION(id_leccion);

CREATE TABLE CHAT_MENSAJES (
    id_mensaje SERIAL PRIMARY KEY,
    id_user INT NOT NULL REFERENCES USUARIOS(id_user) ON DELETE CASCADE,
    pregunta TEXT NOT NULL,
    respuesta_ia TEXT NOT NULL,
    fecha TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chat_mensajes_id_user ON CHAT_MENSAJES(id_user);

CREATE TABLE SOLICITUD_INFORMACION (
    id_solicitud SERIAL PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    id_programa INT REFERENCES PROGRAMA(id_programa) ON DELETE SET NULL,
    mensaje TEXT,
    estado VARCHAR(30) NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    fecha_atencion TIMESTAMPTZ,
    notas_admin TEXT
);

CREATE INDEX idx_solicitud_email ON SOLICITUD_INFORMACION(email);
CREATE INDEX idx_solicitud_estado ON SOLICITUD_INFORMACION(estado);
CREATE INDEX idx_solicitud_id_programa ON SOLICITUD_INFORMACION(id_programa);
