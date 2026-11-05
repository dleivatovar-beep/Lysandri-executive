-- V36: Permitir moodle_course_id nulo para programas en catálogo que aún no tienen curso vinculado en Moodle LMS
ALTER TABLE programa ALTER COLUMN moodle_course_id DROP NOT NULL;

