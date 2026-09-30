-- V3: Garantizar relación 1 a 1 entre usuario e instructor
ALTER TABLE INSTRUCTOR
    ADD CONSTRAINT uq_instructor_id_user
    UNIQUE (id_user);
