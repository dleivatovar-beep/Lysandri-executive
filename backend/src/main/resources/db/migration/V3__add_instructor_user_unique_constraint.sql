ALTER TABLE INSTRUCTOR
    ADD CONSTRAINT uq_instructor_id_user
    UNIQUE (id_user);