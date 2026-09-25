-- ============================================================
-- schema-actual.sql
--
-- Reemplaza a v13-funcionalidad.sql, v14-reportes.sql,
-- v15-ingresos-editables.sql y v16-quitar-docentes-curso.sql
-- (consolidados en un solo archivo, sin cambiar el resultado
-- final del esquema).
--
-- Aditivo e idempotente: usa "IF NOT EXISTS" / "ON CONFLICT ...
-- DO NOTHING" en todo, así que puede ejecutarse en cada arranque
-- (spring.sql.init.mode=always) tanto contra una base ya
-- existente (no hace nada, todo ya está) como contra una base
-- nueva (deja el esquema completo listo para que Hibernate lo
-- valide con ddl-auto=validate).
--
-- No toca datos de negocio (ingresos, egresos, participantes,
-- ediciones manuales) — de eso se encarga la base de datos en
-- producción, no un script de arranque. Ver v17 (retirado de
-- schema-locations tras su única aplicación).
-- ============================================================

-- ── Cursos: ámbito ──────────────────────────────────────────
ALTER TABLE cursos ADD COLUMN IF NOT EXISTS ambito varchar(20);

-- ── Docentes por planificación ──────────────────────────────
-- (curso_docentes NO se crea aquí: existió brevemente entre v13
-- y v16, pero se eliminó porque los docentes se asignan solo en
-- la Planificación, nunca en el Curso. Ver DROP defensivo al
-- final de este archivo.)
CREATE TABLE IF NOT EXISTS planificacion_docentes (
    id_planificacion bigint NOT NULL REFERENCES planificaciones(id_planificaciones) ON DELETE CASCADE,
    id_especialista bigint NOT NULL REFERENCES especialistas(id),
    orden integer NOT NULL,
    PRIMARY KEY (id_planificacion, orden)
);

-- Honorario individual por docente, para planificaciones con
-- varios especialistas (cada uno puede tener un honorario propio).
CREATE TABLE IF NOT EXISTS planificacion_honorarios (
    id_planificacion bigint NOT NULL REFERENCES planificaciones(id_planificaciones) ON DELETE CASCADE,
    id_especialista bigint NOT NULL REFERENCES especialistas(id),
    honorario numeric(10,2) NOT NULL CHECK (honorario >= 0),
    PRIMARY KEY (id_planificacion, id_especialista)
);

-- ── Gasto anual del CEC ──────────────────────────────────────
CREATE TABLE IF NOT EXISTS gastos_cec (
    anio integer PRIMARY KEY CHECK (anio BETWEEN 2000 AND 2100),
    importe numeric(12,2) NOT NULL DEFAULT 0 CHECK (importe >= 0),
    version bigint NOT NULL DEFAULT 0
);
-- Importe de apertura 2026: solo se inserta si la fila no existe
-- todavía; nunca sobrescribe una edición posterior hecha por el CEC.
INSERT INTO gastos_cec(anio, importe, version)
VALUES (2026, 55960.00, 0) ON CONFLICT (anio) DO NOTHING;

-- ── Informe Económico: soporte para referencias sin Planificación ──
ALTER TABLE informes_economicos ALTER COLUMN id_planificacion DROP NOT NULL;
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS referencia_excel varchar(120);
CREATE UNIQUE INDEX IF NOT EXISTS uk_informe_referencia_excel ON informes_economicos(referencia_excel);
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS nombre_referencia varchar(500);
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS anio_referencia integer;
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS fecha_inicio_referencia date;
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS fecha_fin_referencia date;
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS docente_referencia varchar(300);
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS coordinador_referencia varchar(300);
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS observacion_referencia varchar(500);
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS cifras_pendientes boolean NOT NULL DEFAULT false;
ALTER TABLE informes_economicos ADD COLUMN IF NOT EXISTS excluido_2026 boolean NOT NULL DEFAULT false;

-- ── Líneas de ingreso: campos opcionales + edición manual ───
ALTER TABLE informe_economico_lineas_ingreso ALTER COLUMN valor_unitario DROP NOT NULL;
ALTER TABLE informe_economico_lineas_ingreso ALTER COLUMN tipo_usuario DROP NOT NULL;
ALTER TABLE informe_economico_lineas_ingreso
    ADD COLUMN IF NOT EXISTS cantidad_manual boolean NOT NULL DEFAULT false;

-- ── Líneas de egreso: distinguir manual vs. automático ──────
ALTER TABLE informe_economico_lineas_egreso ADD COLUMN IF NOT EXISTS manual boolean NOT NULL DEFAULT false;

-- ── Control de migraciones de datos ─────────────────────────
-- Infraestructura para que un script de datos (como v17) pueda
-- registrar que ya se aplicó y no repetirse. Hoy no la usa nada,
-- pero se conserva por si se retoma ese patrón más adelante.
CREATE TABLE IF NOT EXISTS cec_migraciones(
    clave varchar(120) PRIMARY KEY,
    sha256 varchar(64) NOT NULL,
    aplicada timestamp NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS cec_respaldo_importacion(
    migracion varchar(120) NOT NULL,
    tabla varchar(100) NOT NULL,
    id_original bigint NOT NULL,
    datos text NOT NULL,
    PRIMARY KEY (migracion, tabla, id_original)
);

-- ── Limpieza defensiva ───────────────────────────────────────
-- Por si este script corre contra una base que llegó a tener
-- curso_docentes (creada por la v13 original) pero nunca llegó a
-- aplicar la v16 que la eliminaba. Los docentes se asignan
-- únicamente en planificacion_docentes.
DROP TABLE IF EXISTS curso_docentes;
