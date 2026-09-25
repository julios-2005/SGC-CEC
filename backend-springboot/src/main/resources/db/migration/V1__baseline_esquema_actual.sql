-- =====================================================================
-- V1: Baseline del esquema tal como existía en producción antes de
-- adoptar Flyway. Generado a partir de un pg_dump real (solo estructura:
-- tablas, secuencias, constraints, índices y foreign keys — sin datos).
--
-- Esta migración NO se ejecuta contra bases de datos que ya tienen las
-- tablas creadas: se marca como "baseline" (ver instrucciones de
-- despliegue) para que Flyway la registre como aplicada sin intentar
-- recrear nada. Solo corre de verdad en una base de datos nueva y vacía
-- (ej. para levantar un entorno de pruebas desde cero).
-- =====================================================================

--
-- PostgreSQL database dump
--


-- Dumped from database version 18.4
-- Dumped by pg_dump version 18.4

-- Started on 2026-09-22 16:48:57

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 219 (class 1259 OID 26012)
-- Name: cec_migraciones; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.cec_migraciones (
    clave character varying(120) NOT NULL,
    sha256 character varying(64) NOT NULL,
    aplicada timestamp without time zone DEFAULT now() NOT NULL
);



--
-- TOC entry 220 (class 1259 OID 26019)
-- Name: cec_respaldo_importacion; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.cec_respaldo_importacion (
    migracion character varying(120) NOT NULL,
    tabla character varying(100) NOT NULL,
    id_original bigint NOT NULL,
    datos text NOT NULL
);



--
-- TOC entry 221 (class 1259 OID 26028)
-- Name: coordinadores; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.coordinadores (
    id bigint NOT NULL,
    cedula character varying(20) NOT NULL,
    nombres character varying(80) NOT NULL,
    apellidos character varying(80) NOT NULL,
    telefono character varying(20),
    correo character varying(100),
    foto character varying(255),
    estado boolean DEFAULT true NOT NULL
);



--
-- TOC entry 222 (class 1259 OID 26039)
-- Name: coordinadores_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.coordinadores_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5211 (class 0 OID 0)
-- Dependencies: 222
-- Name: coordinadores_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.coordinadores_id_seq OWNED BY public.coordinadores.id;


--
-- TOC entry 223 (class 1259 OID 26040)
-- Name: cursos; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.cursos (
    id_curso bigint NOT NULL,
    nombre character varying(255) NOT NULL,
    codigo character varying(20) NOT NULL,
    horas integer NOT NULL,
    costo numeric(10,2) NOT NULL,
    cupos_totales integer NOT NULL,
    cupos_restantes integer NOT NULL,
    modalidad character varying(20) NOT NULL,
    estado character varying(20) DEFAULT 'EN_ESPERA'::character varying NOT NULL,
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    foto character varying(255),
    ambito character varying(20),
    CONSTRAINT chk_cupos_restantes_no_negativo CHECK ((cupos_restantes >= 0)),
    CONSTRAINT ck_curso_cupos_restantes_no_supera_totales CHECK ((cupos_restantes <= cupos_totales)),
    CONSTRAINT cursos_costo_check CHECK ((costo >= (0)::numeric)),
    CONSTRAINT cursos_cupos_restantes_check CHECK ((cupos_restantes >= 0)),
    CONSTRAINT cursos_cupos_totales_check CHECK ((cupos_totales > 0)),
    CONSTRAINT cursos_estado_check CHECK (((estado)::text = ANY (ARRAY[('EN_ESPERA'::character varying)::text, ('EN_PROCESO'::character varying)::text, ('FINALIZADO'::character varying)::text]))),
    CONSTRAINT cursos_horas_check CHECK ((horas > 0)),
    CONSTRAINT cursos_modalidad_check CHECK (((modalidad)::text = ANY (ARRAY[('PRESENCIAL'::character varying)::text, ('VIRTUAL'::character varying)::text, ('HIBRIDO'::character varying)::text])))
);



--
-- TOC entry 224 (class 1259 OID 26065)
-- Name: cursos_id_curso_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.cursos_id_curso_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5212 (class 0 OID 0)
-- Dependencies: 224
-- Name: cursos_id_curso_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.cursos_id_curso_seq OWNED BY public.cursos.id_curso;


--
-- TOC entry 225 (class 1259 OID 26066)
-- Name: descuentos; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.descuentos (
    id bigint NOT NULL,
    nombre character varying(50) NOT NULL,
    tipo_usuario character varying(30),
    porcentaje numeric(5,2) NOT NULL,
    estado boolean DEFAULT true NOT NULL,
    CONSTRAINT descuentos_porcentaje_check CHECK (((porcentaje >= (0)::numeric) AND (porcentaje <= (100)::numeric))),
    CONSTRAINT descuentos_tipo_usuario_check CHECK (((tipo_usuario)::text = ANY (ARRAY[('EXTERNO'::character varying)::text, ('ESTUDIANTE_UPSE'::character varying)::text, ('DOCENTE_UPSE'::character varying)::text, ('ADMINISTRATIVO_UPSE'::character varying)::text, ('MAESTRANDO'::character varying)::text, ('GRADUADO'::character varying)::text, ('PERSONA_DISCAPACIDAD'::character varying)::text])))
);



--
-- TOC entry 226 (class 1259 OID 26076)
-- Name: descuentos_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.descuentos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5213 (class 0 OID 0)
-- Dependencies: 226
-- Name: descuentos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.descuentos_id_seq OWNED BY public.descuentos.id;


--
-- TOC entry 227 (class 1259 OID 26077)
-- Name: especialistas; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.especialistas (
    id bigint NOT NULL,
    cedula character varying(20) NOT NULL,
    nombres character varying(80) NOT NULL,
    apellidos character varying(80) NOT NULL,
    telefono character varying(20),
    correo character varying(100),
    especialidad character varying(100),
    area_conocimiento character varying(150),
    estado boolean DEFAULT true NOT NULL,
    pais_nacionalidad character varying(5)
);



--
-- TOC entry 228 (class 1259 OID 26088)
-- Name: especialistas_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.especialistas_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5214 (class 0 OID 0)
-- Dependencies: 228
-- Name: especialistas_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.especialistas_id_seq OWNED BY public.especialistas.id;


--
-- TOC entry 229 (class 1259 OID 26089)
-- Name: gastos_cec; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.gastos_cec (
    anio integer NOT NULL,
    importe numeric(12,2) DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT gastos_cec_anio_check CHECK (((anio >= 2000) AND (anio <= 2100))),
    CONSTRAINT gastos_cec_importe_check CHECK ((importe >= (0)::numeric))
);



--
-- TOC entry 230 (class 1259 OID 26099)
-- Name: informe_economico_lineas_egreso; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.informe_economico_lineas_egreso (
    id bigint NOT NULL,
    id_informe_economico bigint NOT NULL,
    concepto character varying(100) NOT NULL,
    cantidad integer NOT NULL,
    valor_unitario numeric(10,2) NOT NULL,
    total numeric(12,2) NOT NULL,
    manual boolean DEFAULT false NOT NULL
);



--
-- TOC entry 231 (class 1259 OID 26110)
-- Name: informe_economico_lineas_egreso_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.informe_economico_lineas_egreso_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5215 (class 0 OID 0)
-- Dependencies: 231
-- Name: informe_economico_lineas_egreso_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.informe_economico_lineas_egreso_id_seq OWNED BY public.informe_economico_lineas_egreso.id;


--
-- TOC entry 232 (class 1259 OID 26111)
-- Name: informe_economico_lineas_ingreso; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.informe_economico_lineas_ingreso (
    id bigint NOT NULL,
    id_informe_economico bigint NOT NULL,
    tipo_usuario character varying(30),
    cantidad integer,
    valor_unitario numeric(10,2),
    total numeric(12,2) NOT NULL,
    etiqueta character varying(50),
    cantidad_manual boolean DEFAULT false NOT NULL,
    CONSTRAINT informe_economico_lineas_ingreso_tipo_usuario_check CHECK (((tipo_usuario)::text = ANY (ARRAY[('EXTERNO'::character varying)::text, ('ESTUDIANTE_UPSE'::character varying)::text, ('DOCENTE_UPSE'::character varying)::text, ('ADMINISTRATIVO_UPSE'::character varying)::text, ('MAESTRANDO'::character varying)::text, ('GRADUADO'::character varying)::text, ('PERSONA_DISCAPACIDAD'::character varying)::text])))
);



--
-- TOC entry 233 (class 1259 OID 26120)
-- Name: informe_economico_lineas_ingreso_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.informe_economico_lineas_ingreso_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5216 (class 0 OID 0)
-- Dependencies: 233
-- Name: informe_economico_lineas_ingreso_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.informe_economico_lineas_ingreso_id_seq OWNED BY public.informe_economico_lineas_ingreso.id;


--
-- TOC entry 234 (class 1259 OID 26121)
-- Name: informes_economicos; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.informes_economicos (
    id bigint NOT NULL,
    id_planificacion bigint,
    participantes integer,
    ingresos_total numeric(12,2) NOT NULL,
    egresos_total numeric(12,2) NOT NULL,
    utilidad numeric(12,2) NOT NULL,
    fecha_actualizacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    historico boolean DEFAULT false NOT NULL,
    referencia_excel character varying(120),
    nombre_referencia character varying(500),
    anio_referencia integer,
    fecha_inicio_referencia date,
    fecha_fin_referencia date,
    docente_referencia character varying(300),
    coordinador_referencia character varying(300),
    observacion_referencia character varying(500),
    cifras_pendientes boolean DEFAULT false NOT NULL,
    excluido_2026 boolean DEFAULT false NOT NULL
);



--
-- TOC entry 235 (class 1259 OID 26138)
-- Name: informes_economicos_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.informes_economicos_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5217 (class 0 OID 0)
-- Dependencies: 235
-- Name: informes_economicos_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.informes_economicos_id_seq OWNED BY public.informes_economicos.id;


--
-- TOC entry 236 (class 1259 OID 26139)
-- Name: inscripciones; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.inscripciones (
    id bigint NOT NULL,
    tipo_usuario character varying(30) NOT NULL,
    nombre_completo character varying(150) NOT NULL,
    id_planificacion bigint NOT NULL,
    cedula character varying(20) NOT NULL,
    telefono character varying(20) NOT NULL,
    correo_electronico character varying(150) NOT NULL,
    sexo character varying(15) NOT NULL,
    comprobante_pago character varying(255) NOT NULL,
    copia_cedula character varying(255) NOT NULL,
    documento_adicional character varying(255),
    comprobante_matricula_pdf character varying(255),
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    estado character varying(20) DEFAULT 'PENDIENTE'::character varying NOT NULL,
    direccion character varying(250) NOT NULL,
    CONSTRAINT inscripciones_estado_check CHECK (((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ACEPTADA'::character varying)::text, ('RECHAZADA'::character varying)::text, ('RETIRADA'::character varying)::text]))),
    CONSTRAINT inscripciones_sexo_check CHECK (((sexo)::text = ANY (ARRAY[('MASCULINO'::character varying)::text, ('FEMENINO'::character varying)::text]))),
    CONSTRAINT inscripciones_tipo_usuario_check CHECK (((tipo_usuario)::text = ANY (ARRAY[('EXTERNO'::character varying)::text, ('ESTUDIANTE_UPSE'::character varying)::text, ('DOCENTE_UPSE'::character varying)::text, ('ADMINISTRATIVO_UPSE'::character varying)::text, ('MAESTRANDO'::character varying)::text, ('GRADUADO'::character varying)::text, ('PERSONA_DISCAPACIDAD'::character varying)::text])))
);



--
-- TOC entry 237 (class 1259 OID 26162)
-- Name: inscripciones_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.inscripciones_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5218 (class 0 OID 0)
-- Dependencies: 237
-- Name: inscripciones_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.inscripciones_id_seq OWNED BY public.inscripciones.id;


--
-- TOC entry 238 (class 1259 OID 26163)
-- Name: planificacion_docentes; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.planificacion_docentes (
    id_planificacion bigint NOT NULL,
    id_especialista bigint NOT NULL,
    orden integer NOT NULL
);



--
-- TOC entry 239 (class 1259 OID 26169)
-- Name: planificacion_honorarios; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.planificacion_honorarios (
    id_planificacion bigint NOT NULL,
    id_especialista bigint NOT NULL,
    honorario numeric(10,2) NOT NULL,
    CONSTRAINT planificacion_honorarios_honorario_check CHECK ((honorario >= (0)::numeric))
);



--
-- TOC entry 240 (class 1259 OID 26176)
-- Name: planificaciones; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.planificaciones (
    id_planificaciones bigint NOT NULL,
    id_curso bigint NOT NULL,
    id_coordinador bigint NOT NULL,
    id_especialista bigint NOT NULL,
    fecha_inicio date NOT NULL,
    horario character varying(100) NOT NULL,
    fecha_fin date NOT NULL,
    modalidad character varying(20) NOT NULL,
    fecha_registro timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    costo_especialista numeric(10,2),
    CONSTRAINT ck_planificacion_fechas CHECK ((fecha_fin >= fecha_inicio)),
    CONSTRAINT planificaciones_modalidad_check CHECK (((modalidad)::text = ANY (ARRAY[('PRESENCIAL'::character varying)::text, ('VIRTUAL'::character varying)::text, ('HIBRIDO'::character varying)::text])))
);



--
-- TOC entry 241 (class 1259 OID 26191)
-- Name: planificaciones_id_planificaciones_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.planificaciones_id_planificaciones_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5219 (class 0 OID 0)
-- Dependencies: 241
-- Name: planificaciones_id_planificaciones_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.planificaciones_id_planificaciones_seq OWNED BY public.planificaciones.id_planificaciones;


--
-- TOC entry 242 (class 1259 OID 26192)
-- Name: usuarios; Type: TABLE; Schema: public; Owner: admin_cec
--

CREATE TABLE public.usuarios (
    id bigint NOT NULL,
    nombre_usuario character varying(50) NOT NULL,
    contrasena character varying(255) NOT NULL,
    id_coordinador bigint,
    rol character varying(20) NOT NULL,
    estado boolean DEFAULT true NOT NULL,
    debe_cambiar_contrasena boolean DEFAULT false NOT NULL,
    fecha_creacion timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ultima_modificacion timestamp without time zone,
    nombre_completo character varying(150),
    CONSTRAINT usuarios_rol_check CHECK (((rol)::text = ANY (ARRAY[('ADMIN_GENERAL'::character varying)::text, ('COORDINADOR'::character varying)::text, ('COBROS'::character varying)::text])))
);



--
-- TOC entry 243 (class 1259 OID 26206)
-- Name: usuarios_id_seq; Type: SEQUENCE; Schema: public; Owner: admin_cec
--

CREATE SEQUENCE public.usuarios_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;



--
-- TOC entry 5220 (class 0 OID 0)
-- Dependencies: 243
-- Name: usuarios_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: admin_cec
--

ALTER SEQUENCE public.usuarios_id_seq OWNED BY public.usuarios.id;


--
-- TOC entry 4922 (class 2604 OID 26207)
-- Name: coordinadores id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.coordinadores ALTER COLUMN id SET DEFAULT nextval('public.coordinadores_id_seq'::regclass);


--
-- TOC entry 4924 (class 2604 OID 26208)
-- Name: cursos id_curso; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.cursos ALTER COLUMN id_curso SET DEFAULT nextval('public.cursos_id_curso_seq'::regclass);


--
-- TOC entry 4927 (class 2604 OID 26209)
-- Name: descuentos id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.descuentos ALTER COLUMN id SET DEFAULT nextval('public.descuentos_id_seq'::regclass);


--
-- TOC entry 4929 (class 2604 OID 26210)
-- Name: especialistas id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.especialistas ALTER COLUMN id SET DEFAULT nextval('public.especialistas_id_seq'::regclass);


--
-- TOC entry 4933 (class 2604 OID 26211)
-- Name: informe_economico_lineas_egreso id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_egreso ALTER COLUMN id SET DEFAULT nextval('public.informe_economico_lineas_egreso_id_seq'::regclass);


--
-- TOC entry 4935 (class 2604 OID 26212)
-- Name: informe_economico_lineas_ingreso id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_ingreso ALTER COLUMN id SET DEFAULT nextval('public.informe_economico_lineas_ingreso_id_seq'::regclass);


--
-- TOC entry 4937 (class 2604 OID 26213)
-- Name: informes_economicos id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informes_economicos ALTER COLUMN id SET DEFAULT nextval('public.informes_economicos_id_seq'::regclass);


--
-- TOC entry 4942 (class 2604 OID 26214)
-- Name: inscripciones id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.inscripciones ALTER COLUMN id SET DEFAULT nextval('public.inscripciones_id_seq'::regclass);


--
-- TOC entry 4945 (class 2604 OID 26215)
-- Name: planificaciones id_planificaciones; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificaciones ALTER COLUMN id_planificaciones SET DEFAULT nextval('public.planificaciones_id_planificaciones_seq'::regclass);


--
-- TOC entry 4947 (class 2604 OID 26216)
-- Name: usuarios id; Type: DEFAULT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.usuarios ALTER COLUMN id SET DEFAULT nextval('public.usuarios_id_seq'::regclass);


--
-- TOC entry 5181 (class 0 OID 26012)
-- Dependencies: 219

-- ============================================================
-- Restricciones, índices y llaves foráneas
-- ============================================================

-- TOC entry 4972 (class 2606 OID 26218)
-- Name: cec_migraciones cec_migraciones_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.cec_migraciones
    ADD CONSTRAINT cec_migraciones_pkey PRIMARY KEY (clave);


--
-- TOC entry 4974 (class 2606 OID 26220)
-- Name: cec_respaldo_importacion cec_respaldo_importacion_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.cec_respaldo_importacion
    ADD CONSTRAINT cec_respaldo_importacion_pkey PRIMARY KEY (migracion, tabla, id_original);


--
-- TOC entry 4976 (class 2606 OID 26222)
-- Name: coordinadores coordinadores_cedula_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.coordinadores
    ADD CONSTRAINT coordinadores_cedula_key UNIQUE (cedula);


--
-- TOC entry 4978 (class 2606 OID 26224)
-- Name: coordinadores coordinadores_correo_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.coordinadores
    ADD CONSTRAINT coordinadores_correo_key UNIQUE (correo);


--
-- TOC entry 4980 (class 2606 OID 26226)
-- Name: coordinadores coordinadores_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.coordinadores
    ADD CONSTRAINT coordinadores_pkey PRIMARY KEY (id);


--
-- TOC entry 4982 (class 2606 OID 26228)
-- Name: cursos cursos_codigo_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.cursos
    ADD CONSTRAINT cursos_codigo_key UNIQUE (codigo);


--
-- TOC entry 4984 (class 2606 OID 26230)
-- Name: cursos cursos_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.cursos
    ADD CONSTRAINT cursos_pkey PRIMARY KEY (id_curso);


--
-- TOC entry 4986 (class 2606 OID 26232)
-- Name: descuentos descuentos_nombre_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.descuentos
    ADD CONSTRAINT descuentos_nombre_key UNIQUE (nombre);


--
-- TOC entry 4988 (class 2606 OID 26234)
-- Name: descuentos descuentos_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.descuentos
    ADD CONSTRAINT descuentos_pkey PRIMARY KEY (id);


--
-- TOC entry 4990 (class 2606 OID 26236)
-- Name: descuentos descuentos_tipo_usuario_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.descuentos
    ADD CONSTRAINT descuentos_tipo_usuario_key UNIQUE (tipo_usuario);


--
-- TOC entry 4992 (class 2606 OID 26238)
-- Name: especialistas especialistas_cedula_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.especialistas
    ADD CONSTRAINT especialistas_cedula_key UNIQUE (cedula);


--
-- TOC entry 4994 (class 2606 OID 26240)
-- Name: especialistas especialistas_correo_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.especialistas
    ADD CONSTRAINT especialistas_correo_key UNIQUE (correo);


--
-- TOC entry 4996 (class 2606 OID 26242)
-- Name: especialistas especialistas_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.especialistas
    ADD CONSTRAINT especialistas_pkey PRIMARY KEY (id);


--
-- TOC entry 4998 (class 2606 OID 26244)
-- Name: gastos_cec gastos_cec_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.gastos_cec
    ADD CONSTRAINT gastos_cec_pkey PRIMARY KEY (anio);


--
-- TOC entry 5000 (class 2606 OID 26246)
-- Name: informe_economico_lineas_egreso informe_economico_lineas_egreso_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_egreso
    ADD CONSTRAINT informe_economico_lineas_egreso_pkey PRIMARY KEY (id);


--
-- TOC entry 5002 (class 2606 OID 26248)
-- Name: informe_economico_lineas_ingreso informe_economico_lineas_ingreso_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_ingreso
    ADD CONSTRAINT informe_economico_lineas_ingreso_pkey PRIMARY KEY (id);


--
-- TOC entry 5004 (class 2606 OID 26250)
-- Name: informes_economicos informes_economicos_id_planificacion_key; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informes_economicos
    ADD CONSTRAINT informes_economicos_id_planificacion_key UNIQUE (id_planificacion);


--
-- TOC entry 5006 (class 2606 OID 26252)
-- Name: informes_economicos informes_economicos_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informes_economicos
    ADD CONSTRAINT informes_economicos_pkey PRIMARY KEY (id);


--
-- TOC entry 5010 (class 2606 OID 26254)
-- Name: inscripciones inscripciones_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.inscripciones
    ADD CONSTRAINT inscripciones_pkey PRIMARY KEY (id);


--
-- TOC entry 5013 (class 2606 OID 26256)
-- Name: planificacion_docentes planificacion_docentes_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_docentes
    ADD CONSTRAINT planificacion_docentes_pkey PRIMARY KEY (id_planificacion, orden);


--
-- TOC entry 5015 (class 2606 OID 26258)
-- Name: planificacion_honorarios planificacion_honorarios_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_honorarios
    ADD CONSTRAINT planificacion_honorarios_pkey PRIMARY KEY (id_planificacion, id_especialista);


--
-- TOC entry 5017 (class 2606 OID 26260)
-- Name: planificaciones planificaciones_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificaciones
    ADD CONSTRAINT planificaciones_pkey PRIMARY KEY (id_planificaciones);


--
-- TOC entry 5019 (class 2606 OID 26262)
-- Name: usuarios uk_nombre_usuario; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT uk_nombre_usuario UNIQUE (nombre_usuario);


--
-- TOC entry 5021 (class 2606 OID 26264)
-- Name: usuarios usuarios_pkey; Type: CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT usuarios_pkey PRIMARY KEY (id);


--
-- TOC entry 5008 (class 1259 OID 26265)
-- Name: idx_inscripciones_planificacion_estado; Type: INDEX; Schema: public; Owner: admin_cec
--

CREATE INDEX idx_inscripciones_planificacion_estado ON public.inscripciones USING btree (id_planificacion, estado);


--
-- TOC entry 5007 (class 1259 OID 26266)
-- Name: uk_informe_referencia_excel; Type: INDEX; Schema: public; Owner: admin_cec
--

CREATE UNIQUE INDEX uk_informe_referencia_excel ON public.informes_economicos USING btree (referencia_excel);


--
-- TOC entry 5011 (class 1259 OID 26267)
-- Name: uk_inscripcion_cedula_planificacion_activa; Type: INDEX; Schema: public; Owner: admin_cec
--

CREATE UNIQUE INDEX uk_inscripcion_cedula_planificacion_activa ON public.inscripciones USING btree (cedula, id_planificacion) WHERE ((estado)::text = ANY (ARRAY[('PENDIENTE'::character varying)::text, ('ACEPTADA'::character varying)::text]));


--
-- TOC entry 5022 (class 2606 OID 26268)
-- Name: informe_economico_lineas_egreso fk_egreso_informe; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_egreso
    ADD CONSTRAINT fk_egreso_informe FOREIGN KEY (id_informe_economico) REFERENCES public.informes_economicos(id) ON DELETE CASCADE;


--
-- TOC entry 5024 (class 2606 OID 26273)
-- Name: informes_economicos fk_informe_planificacion; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informes_economicos
    ADD CONSTRAINT fk_informe_planificacion FOREIGN KEY (id_planificacion) REFERENCES public.planificaciones(id_planificaciones) ON UPDATE CASCADE ON DELETE CASCADE;


--
-- TOC entry 5023 (class 2606 OID 26278)
-- Name: informe_economico_lineas_ingreso fk_ingreso_informe; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.informe_economico_lineas_ingreso
    ADD CONSTRAINT fk_ingreso_informe FOREIGN KEY (id_informe_economico) REFERENCES public.informes_economicos(id) ON DELETE CASCADE;


--
-- TOC entry 5025 (class 2606 OID 26283)
-- Name: inscripciones fk_inscripcion_planificacion; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.inscripciones
    ADD CONSTRAINT fk_inscripcion_planificacion FOREIGN KEY (id_planificacion) REFERENCES public.planificaciones(id_planificaciones) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 5030 (class 2606 OID 26288)
-- Name: planificaciones fk_planificacion_coordinador; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificaciones
    ADD CONSTRAINT fk_planificacion_coordinador FOREIGN KEY (id_coordinador) REFERENCES public.coordinadores(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 5031 (class 2606 OID 26293)
-- Name: planificaciones fk_planificacion_curso; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificaciones
    ADD CONSTRAINT fk_planificacion_curso FOREIGN KEY (id_curso) REFERENCES public.cursos(id_curso) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 5032 (class 2606 OID 26298)
-- Name: planificaciones fk_planificacion_especialista; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificaciones
    ADD CONSTRAINT fk_planificacion_especialista FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 5033 (class 2606 OID 26303)
-- Name: usuarios fk_usuarios_coordinadores; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.usuarios
    ADD CONSTRAINT fk_usuarios_coordinadores FOREIGN KEY (id_coordinador) REFERENCES public.coordinadores(id) ON UPDATE CASCADE ON DELETE RESTRICT;


--
-- TOC entry 5026 (class 2606 OID 26308)
-- Name: planificacion_docentes planificacion_docentes_id_especialista_fkey; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_docentes
    ADD CONSTRAINT planificacion_docentes_id_especialista_fkey FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id);


--
-- TOC entry 5027 (class 2606 OID 26313)
-- Name: planificacion_docentes planificacion_docentes_id_planificacion_fkey; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_docentes
    ADD CONSTRAINT planificacion_docentes_id_planificacion_fkey FOREIGN KEY (id_planificacion) REFERENCES public.planificaciones(id_planificaciones) ON DELETE CASCADE;


--
-- TOC entry 5028 (class 2606 OID 26318)
-- Name: planificacion_honorarios planificacion_honorarios_id_especialista_fkey; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_honorarios
    ADD CONSTRAINT planificacion_honorarios_id_especialista_fkey FOREIGN KEY (id_especialista) REFERENCES public.especialistas(id);


--
-- TOC entry 5029 (class 2606 OID 26323)
-- Name: planificacion_honorarios planificacion_honorarios_id_planificacion_fkey; Type: FK CONSTRAINT; Schema: public; Owner: admin_cec
--

ALTER TABLE ONLY public.planificacion_honorarios
    ADD CONSTRAINT planificacion_honorarios_id_planificacion_fkey FOREIGN KEY (id_planificacion) REFERENCES public.planificaciones(id_planificaciones) ON DELETE CASCADE;


-- Completed on 2026-09-22 16:48:57

--
-- PostgreSQL database dump complete