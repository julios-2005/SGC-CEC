-- Versión de token por usuario: un cambio de contraseña la incrementa y los JWT
-- emitidos con la versión anterior (claim "tv") dejan de ser aceptados.
ALTER TABLE usuarios ADD COLUMN version_token integer NOT NULL DEFAULT 0;
