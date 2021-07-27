--
-- PostgreSQL database dump
--

-- Dumped from database version 13.2 (Debian 13.2-1.pgdg100+1)
-- Dumped by pg_dump version 13.2

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- Name: receiver_type; Type: TYPE; Schema: public; Owner: fasten
--

CREATE TYPE public.receiver_type AS ENUM (
    'static',
    'dynamic',
    'virtual',
    'interface',
    'special'
);


ALTER TYPE public.receiver_type OWNER TO fasten;

--
-- Name: receiver; Type: TYPE; Schema: public; Owner: fasten
--

CREATE TYPE public.receiver AS (
	line integer,
	type public.receiver_type,
	receiver_uri text
);


ALTER TYPE public.receiver OWNER TO fasten;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: artifact_repositories; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.artifact_repositories (
    id bigint NOT NULL,
    repository_base_url text NOT NULL
);


ALTER TABLE public.artifact_repositories OWNER TO fasten;

--
-- Name: artifact_repositories_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.artifact_repositories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.artifact_repositories_id_seq OWNER TO fasten;

--
-- Name: artifact_repositories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.artifact_repositories_id_seq OWNED BY public.artifact_repositories.id;


--
-- Name: binary_module_contents; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.binary_module_contents (
    binary_module_id bigint NOT NULL,
    file_id bigint NOT NULL
);


ALTER TABLE public.binary_module_contents OWNER TO fasten;

--
-- Name: binary_modules; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.binary_modules (
    id bigint NOT NULL,
    package_version_id bigint NOT NULL,
    name text NOT NULL,
    created_at timestamp without time zone,
    metadata jsonb
);


ALTER TABLE public.binary_modules OWNER TO fasten;

--
-- Name: binary_modules_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.binary_modules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.binary_modules_id_seq OWNER TO fasten;

--
-- Name: binary_modules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.binary_modules_id_seq OWNED BY public.binary_modules.id;


--
-- Name: callables; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.callables (
    id bigint NOT NULL,
    module_id bigint NOT NULL,
    fasten_uri text NOT NULL,
    is_internal_call boolean NOT NULL,
    created_at timestamp without time zone,
    line_start integer,
    line_end integer,
    metadata jsonb,
    CONSTRAINT check_module_id CHECK ((((module_id = '-1'::integer) AND (is_internal_call IS FALSE)) OR ((module_id IS NOT NULL) AND (is_internal_call IS TRUE))))
);


ALTER TABLE public.callables OWNER TO fasten;

--
-- Name: callables_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.callables_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.callables_id_seq OWNER TO fasten;

--
-- Name: callables_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.callables_id_seq OWNED BY public.callables.id;


--
-- Name: dependencies; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.dependencies (
    package_version_id bigint NOT NULL,
    dependency_id bigint NOT NULL,
    version_range text[] NOT NULL,
    architecture text[],
    dependency_type text[],
    alternative_group bigint,
    metadata jsonb
);


ALTER TABLE public.dependencies OWNER TO fasten;

--
-- Name: edges; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.edges (
    source_id bigint NOT NULL,
    target_id bigint NOT NULL,
    receivers public.receiver[] NOT NULL,
    metadata jsonb
);


ALTER TABLE public.edges OWNER TO fasten;

--
-- Name: files; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.files (
    id bigint NOT NULL,
    package_version_id bigint NOT NULL,
    path text NOT NULL,
    checksum bytea,
    created_at timestamp without time zone,
    metadata jsonb
);


ALTER TABLE public.files OWNER TO fasten;

--
-- Name: files_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.files_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.files_id_seq OWNER TO fasten;

--
-- Name: files_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.files_id_seq OWNED BY public.files.id;


--
-- Name: ingested_artifacts; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.ingested_artifacts (
    id bigint NOT NULL,
    package_name text NOT NULL,
    version text NOT NULL,
    "timestamp" timestamp without time zone
);


ALTER TABLE public.ingested_artifacts OWNER TO fasten;

--
-- Name: ingested_artifacts_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.ingested_artifacts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.ingested_artifacts_id_seq OWNER TO fasten;

--
-- Name: ingested_artifacts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.ingested_artifacts_id_seq OWNED BY public.ingested_artifacts.id;


--
-- Name: module_contents; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.module_contents (
    module_id bigint NOT NULL,
    file_id bigint NOT NULL
);


ALTER TABLE public.module_contents OWNER TO fasten;

--
-- Name: modules; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.modules (
    id bigint NOT NULL,
    package_version_id bigint NOT NULL,
    namespace text NOT NULL,
    created_at timestamp without time zone,
    metadata jsonb
);


ALTER TABLE public.modules OWNER TO fasten;

--
-- Name: modules_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.modules_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.modules_id_seq OWNER TO fasten;

--
-- Name: modules_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.modules_id_seq OWNED BY public.modules.id;


--
-- Name: package_versions; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.package_versions (
    id bigint NOT NULL,
    package_id bigint NOT NULL,
    version text NOT NULL,
    cg_generator text NOT NULL,
    artifact_repository_id bigint,
    architecture text,
    created_at timestamp without time zone,
    metadata jsonb
);


ALTER TABLE public.package_versions OWNER TO fasten;

--
-- Name: package_versions_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.package_versions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.package_versions_id_seq OWNER TO fasten;

--
-- Name: package_versions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.package_versions_id_seq OWNED BY public.package_versions.id;


--
-- Name: packages; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.packages (
    id bigint NOT NULL,
    package_name text NOT NULL,
    forge text NOT NULL,
    project_name text,
    repository text,
    created_at timestamp without time zone
);


ALTER TABLE public.packages OWNER TO fasten;

--
-- Name: packages_id_seq; Type: SEQUENCE; Schema: public; Owner: fasten
--

CREATE SEQUENCE public.packages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.packages_id_seq OWNER TO fasten;

--
-- Name: packages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: fasten
--

ALTER SEQUENCE public.packages_id_seq OWNED BY public.packages.id;


--
-- Name: virtual_implementations; Type: TABLE; Schema: public; Owner: fasten
--

CREATE TABLE public.virtual_implementations (
    virtual_package_version_id bigint NOT NULL,
    package_version_id bigint NOT NULL
);


ALTER TABLE public.virtual_implementations OWNER TO fasten;

--
-- Name: artifact_repositories id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.artifact_repositories ALTER COLUMN id SET DEFAULT nextval('public.artifact_repositories_id_seq'::regclass);


--
-- Name: binary_modules id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_modules ALTER COLUMN id SET DEFAULT nextval('public.binary_modules_id_seq'::regclass);


--
-- Name: callables id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.callables ALTER COLUMN id SET DEFAULT nextval('public.callables_id_seq'::regclass);


--
-- Name: files id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.files ALTER COLUMN id SET DEFAULT nextval('public.files_id_seq'::regclass);


--
-- Name: ingested_artifacts id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.ingested_artifacts ALTER COLUMN id SET DEFAULT nextval('public.ingested_artifacts_id_seq'::regclass);


--
-- Name: modules id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.modules ALTER COLUMN id SET DEFAULT nextval('public.modules_id_seq'::regclass);


--
-- Name: package_versions id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.package_versions ALTER COLUMN id SET DEFAULT nextval('public.package_versions_id_seq'::regclass);


--
-- Name: packages id; Type: DEFAULT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.packages ALTER COLUMN id SET DEFAULT nextval('public.packages_id_seq'::regclass);


--
-- Data for Name: artifact_repositories; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.artifact_repositories VALUES (-1, 'https://repo.maven.apache.org/maven2/');


--
-- Data for Name: binary_module_contents; Type: TABLE DATA; Schema: public; Owner: fasten
--



--
-- Data for Name: binary_modules; Type: TABLE DATA; Schema: public; Owner: fasten
--



--
-- Data for Name: callables; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.callables VALUES (2, 1, '/com.esotericsoftware.asm/ByteVector.putByte(%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (3, 1, '/com.esotericsoftware.asm/ByteVector.putInt(%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (4, 1, '/com.esotericsoftware.asm/ByteVector.c(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (5, 1, '/com.esotericsoftware.asm/ByteVector.b(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (6, 1, '/com.esotericsoftware.asm/ByteVector.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (7, 1, '/com.esotericsoftware.asm/ByteVector.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (8, 1, '/com.esotericsoftware.asm/ByteVector.a(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (9, 1, '/com.esotericsoftware.asm/ByteVector.putByteArray(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (10, 1, '/com.esotericsoftware.asm/ByteVector.putUTF8(%2Fjava.lang%2FString)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (11, 1, '/com.esotericsoftware.asm/ByteVector.putLong(%2Fjava.lang%2FLongType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (12, 1, '/com.esotericsoftware.asm/ByteVector.putShort(%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (13, 2, '/com.esotericsoftware.reflectasm/MethodAccess.invoke(%2Fjava.lang%2FObject,%2Fjava.lang%2FString,%2Fjava.lang%2FClass%5B%5D,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FObject', true, NULL, 39, 39, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (14, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getIndex(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, 49, 51, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (15, 2, '/com.esotericsoftware.reflectasm/MethodAccess.get(%2Fjava.lang%2FClass)MethodAccess', true, NULL, 84, 286, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (16, 2, '/com.esotericsoftware.reflectasm/MethodAccess.invoke(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FObject', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (17, 2, '/com.esotericsoftware.reflectasm/MethodAccess.addDeclaredMethodsToList(%2Fjava.lang%2FClass,%2Fjava.util%2FArrayList)%2Fjava.lang%2FVoidType', true, NULL, 291, 299, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (18, 2, '/com.esotericsoftware.reflectasm/MethodAccess.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, 30, 30, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (19, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getMethodNames()%2Fjava.lang%2FString%5B%5D', true, NULL, 70, 70, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (20, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getParameterTypes()%2Fjava.lang%2FClass%5B%5D%5B%5D', true, NULL, 74, 74, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (21, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getIndex(%2Fjava.lang%2FString,%2Fjava.lang%2FClass%5B%5D)%2Fjava.lang%2FIntegerType', true, NULL, 56, 58, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (22, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getIndex(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, 63, 65, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (23, 2, '/com.esotericsoftware.reflectasm/MethodAccess.recursiveAddInterfaceMethodsToList(%2Fjava.lang%2FClass,%2Fjava.util%2FArrayList)%2Fjava.lang%2FVoidType', true, NULL, 302, 305, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (24, 2, '/com.esotericsoftware.reflectasm/MethodAccess.invoke(%2Fjava.lang%2FObject,%2Fjava.lang%2FString,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FObject', true, NULL, 44, 44, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (25, 2, '/com.esotericsoftware.reflectasm/MethodAccess.getReturnTypes()%2Fjava.lang%2FClass%5B%5D', true, NULL, 78, 78, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (26, 3, '/com.esotericsoftware.asm/Handler.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (27, 3, '/com.esotericsoftware.asm/Handler.a(Handler,Label,Label)Handler', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (28, 4, '/com.esotericsoftware.asm/Type.getMethodType(Type,Type%5B%5D)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (29, 4, '/com.esotericsoftware.asm/Type.getReturnType(%2Fjava.lang%2FString)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (30, 4, '/com.esotericsoftware.asm/Type.getDescriptor(%2Fjava.lang%2FClass)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (31, 4, '/com.esotericsoftware.asm/Type.hashCode()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (32, 4, '/com.esotericsoftware.asm/Type.toString()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (33, 4, '/com.esotericsoftware.asm/Type._clinit_()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (34, 4, '/com.esotericsoftware.asm/Type.getInternalName(%2Fjava.lang%2FClass)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (35, 4, '/com.esotericsoftware.asm/Type.getInternalName()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (36, 4, '/com.esotericsoftware.asm/Type.getDimensions()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (37, 4, '/com.esotericsoftware.asm/Type.getClassName()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (38, 4, '/com.esotericsoftware.asm/Type.getDescriptor()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (467, -1, '/java.lang/Object.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (39, 4, '/com.esotericsoftware.asm/Type.a(%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FIntegerType)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (40, 4, '/com.esotericsoftware.asm/Type.a(%2Fjava.lang%2FStringBuffer)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (41, 4, '/com.esotericsoftware.asm/Type.a(%2Fjava.lang%2FStringBuffer,%2Fjava.lang%2FClass)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (42, 4, '/com.esotericsoftware.asm/Type.getArgumentTypes()Type%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (43, 4, '/com.esotericsoftware.asm/Type.equals(%2Fjava.lang%2FObject)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (44, 4, '/com.esotericsoftware.asm/Type.getArgumentsAndReturnSizes(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (45, 4, '/com.esotericsoftware.asm/Type.getArgumentTypes(%2Fjava.lang%2FString)Type%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (46, 4, '/com.esotericsoftware.asm/Type.getArgumentsAndReturnSizes()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (47, 4, '/com.esotericsoftware.asm/Type.getArgumentTypes(%2Fjava.lang.reflect%2FMethod)Type%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (48, 4, '/com.esotericsoftware.asm/Type.getMethodDescriptor(Type,Type%5B%5D)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (49, 4, '/com.esotericsoftware.asm/Type.getElementType()Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (50, 4, '/com.esotericsoftware.asm/Type.getConstructorDescriptor(%2Fjava.lang.reflect%2FConstructor)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (51, 4, '/com.esotericsoftware.asm/Type.getOpcode(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (52, 4, '/com.esotericsoftware.asm/Type.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (53, 4, '/com.esotericsoftware.asm/Type.%3Cclinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (54, 4, '/com.esotericsoftware.asm/Type.getMethodType(%2Fjava.lang%2FString)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (55, 4, '/com.esotericsoftware.asm/Type.getMethodDescriptor(%2Fjava.lang.reflect%2FMethod)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (56, 4, '/com.esotericsoftware.asm/Type.getReturnType()Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (57, 4, '/com.esotericsoftware.asm/Type.getType(%2Fjava.lang.reflect%2FConstructor)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (58, 4, '/com.esotericsoftware.asm/Type.getObjectType(%2Fjava.lang%2FString)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (59, 4, '/com.esotericsoftware.asm/Type.getReturnType(%2Fjava.lang.reflect%2FMethod)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (60, 4, '/com.esotericsoftware.asm/Type.getSize()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (61, 4, '/com.esotericsoftware.asm/Type.getType(%2Fjava.lang.reflect%2FMethod)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (62, 4, '/com.esotericsoftware.asm/Type.getType(%2Fjava.lang%2FClass)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (63, 4, '/com.esotericsoftware.asm/Type.getSort()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (64, 4, '/com.esotericsoftware.asm/Type.getType(%2Fjava.lang%2FString)Type', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (65, 5, '/com.esotericsoftware.asm/ClassVisitor.visitOuterClass(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (66, 5, '/com.esotericsoftware.asm/ClassVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType,ClassVisitor)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (67, 5, '/com.esotericsoftware.asm/ClassVisitor.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (68, 5, '/com.esotericsoftware.asm/ClassVisitor.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (69, 5, '/com.esotericsoftware.asm/ClassVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (70, 5, '/com.esotericsoftware.asm/ClassVisitor.visitSource(%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (71, 5, '/com.esotericsoftware.asm/ClassVisitor.visit(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (72, 5, '/com.esotericsoftware.asm/ClassVisitor.visitMethod(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString%5B%5D)MethodVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (468, -1, '/java.lang/Object.equals(Object)BooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (73, 5, '/com.esotericsoftware.asm/ClassVisitor.visitInnerClass(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (74, 5, '/com.esotericsoftware.asm/ClassVisitor.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (75, 5, '/com.esotericsoftware.asm/ClassVisitor.visitField(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FObject)FieldVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (76, 5, '/com.esotericsoftware.asm/ClassVisitor.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (77, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.loadClass(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FClass', true, NULL, 62, 67, '{"access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (78, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.get(%2Fjava.lang%2FClass)AccessClassLoader', true, NULL, 121, 145, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (79, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.defineAccessClass(%2Fjava.lang%2FString,%2Fjava.lang%2FByteType%5B%5D)%2Fjava.lang%2FClass', true, NULL, 56, 57, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (80, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.activeAccessClassLoaders()%2Fjava.lang%2FIntegerType', true, NULL, 161, 163, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (81, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.%3Cclinit%3E()%2Fjava.lang%2FVoidType', true, NULL, 28, 32, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (82, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.%3Cinit%3E(%2Fjava.lang%2FClassLoader)%2Fjava.lang%2FVoidType', true, NULL, 39, 40, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (83, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.getDefineClassMethod()%2Fjava.lang.reflect%2FMethod', true, NULL, 105, 117, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (84, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.remove(%2Fjava.lang%2FClassLoader)%2Fjava.lang%2FVoidType', true, NULL, 150, 158, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (85, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.areInSameRuntimeClassLoader(%2Fjava.lang%2FClass,%2Fjava.lang%2FClass)%2Fjava.lang%2FBooleanType', true, NULL, 85, 95, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (86, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.defineClass(%2Fjava.lang%2FString,%2Fjava.lang%2FByteType%5B%5D)%2Fjava.lang%2FClass', true, NULL, 73, 78, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (87, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.loadAccessClass(%2Fjava.lang%2FString)%2Fjava.lang%2FClass', true, NULL, 45, 52, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (88, 6, '/com.esotericsoftware.reflectasm/AccessClassLoader.getParentClassLoader(%2Fjava.lang%2FClass)%2Fjava.lang%2FClassLoader', true, NULL, 99, 101, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (89, 7, '/com.esotericsoftware.asm/AnnotationVisitor.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (90, 7, '/com.esotericsoftware.asm/AnnotationVisitor.visitArray(%2Fjava.lang%2FString)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (91, 7, '/com.esotericsoftware.asm/AnnotationVisitor.visit(%2Fjava.lang%2FString,%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (92, 7, '/com.esotericsoftware.asm/AnnotationVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType,AnnotationVisitor)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (93, 7, '/com.esotericsoftware.asm/AnnotationVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (94, 7, '/com.esotericsoftware.asm/AnnotationVisitor.visitEnum(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (95, 7, '/com.esotericsoftware.asm/AnnotationVisitor.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FString)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (96, 8, '/com.esotericsoftware.asm/Context.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (97, 9, '/com.esotericsoftware.asm/Frame.d(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (98, 9, '/com.esotericsoftware.asm/Frame.b(ClassWriter,%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (99, 9, '/com.esotericsoftware.asm/Frame.a(ClassWriter,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (100, 9, '/com.esotericsoftware.asm/Frame.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (101, 9, '/com.esotericsoftware.asm/Frame.a(ClassWriter,Frame,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (102, 9, '/com.esotericsoftware.asm/Frame.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,ClassWriter,Item)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (103, 9, '/com.esotericsoftware.asm/Frame.a(ClassWriter,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (104, 9, '/com.esotericsoftware.asm/Frame.a(ClassWriter,%2Fjava.lang%2FIntegerType,Type%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (105, 9, '/com.esotericsoftware.asm/Frame.c(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (106, 9, '/com.esotericsoftware.asm/Frame.b(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (107, 9, '/com.esotericsoftware.asm/Frame.a(ClassWriter,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (108, 9, '/com.esotericsoftware.asm/Frame.a(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (109, 9, '/com.esotericsoftware.asm/Frame.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (110, 9, '/com.esotericsoftware.asm/Frame.%3Cclinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (111, 9, '/com.esotericsoftware.asm/Frame._clinit_()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (112, 9, '/com.esotericsoftware.asm/Frame.a(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (113, 9, '/com.esotericsoftware.asm/Frame.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (114, 10, '/com.esotericsoftware.asm/AnnotationWriter.%3Cinit%3E(ClassWriter,%2Fjava.lang%2FBooleanType,ByteVector,ByteVector,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (115, 10, '/com.esotericsoftware.asm/AnnotationWriter.a(AnnotationWriter%5B%5D,%2Fjava.lang%2FIntegerType,ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (116, 10, '/com.esotericsoftware.asm/AnnotationWriter.a(ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (117, 10, '/com.esotericsoftware.asm/AnnotationWriter.visitEnum(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (118, 10, '/com.esotericsoftware.asm/AnnotationWriter.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (119, 10, '/com.esotericsoftware.asm/AnnotationWriter.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FString)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (120, 10, '/com.esotericsoftware.asm/AnnotationWriter.visitArray(%2Fjava.lang%2FString)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (121, 10, '/com.esotericsoftware.asm/AnnotationWriter.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (122, 10, '/com.esotericsoftware.asm/AnnotationWriter.a(%2Fjava.lang%2FIntegerType,TypePath,ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (123, 10, '/com.esotericsoftware.asm/AnnotationWriter.visit(%2Fjava.lang%2FString,%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (124, 11, '/com.esotericsoftware.asm/Edge.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (125, 12, '/com.esotericsoftware.asm/FieldVisitor.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (126, 12, '/com.esotericsoftware.asm/FieldVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType,FieldVisitor)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (127, 12, '/com.esotericsoftware.asm/FieldVisitor.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (128, 12, '/com.esotericsoftware.asm/FieldVisitor.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (129, 12, '/com.esotericsoftware.asm/FieldVisitor.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (130, 12, '/com.esotericsoftware.asm/FieldVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (131, 13, '/com.esotericsoftware.asm/FieldWriter.a(ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (132, 13, '/com.esotericsoftware.asm/FieldWriter.%3Cinit%3E(ClassWriter,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (133, 13, '/com.esotericsoftware.asm/FieldWriter.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (134, 13, '/com.esotericsoftware.asm/FieldWriter.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (135, 13, '/com.esotericsoftware.asm/FieldWriter.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (136, 13, '/com.esotericsoftware.asm/FieldWriter.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (137, 13, '/com.esotericsoftware.asm/FieldWriter.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (138, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.get(%2Fjava.lang%2FClass)ConstructorAccess', true, NULL, 45, 117, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (139, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.isNonStaticMemberClass()%2Fjava.lang%2FBooleanType', true, NULL, 29, 29, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (140, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.insertNewInstance(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, 131, 139, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (141, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.newInstance()%2Fjava.lang%2FObject', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (142, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.newInstance(%2Fjava.lang%2FObject)%2Fjava.lang%2FObject', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (143, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, 25, 25, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (144, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.insertNewInstanceInner(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, 142, 164, '{"access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (145, 14, '/com.esotericsoftware.reflectasm/ConstructorAccess.insertConstructor(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, 121, 128, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (146, 15, '/com.esotericsoftware.asm/Handle.toString()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (147, 15, '/com.esotericsoftware.asm/Handle.getOwner()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (148, 15, '/com.esotericsoftware.asm/Handle.getDesc()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (149, 15, '/com.esotericsoftware.asm/Handle.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (150, 15, '/com.esotericsoftware.asm/Handle.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (151, 15, '/com.esotericsoftware.asm/Handle.getName()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (152, 15, '/com.esotericsoftware.asm/Handle.equals(%2Fjava.lang%2FObject)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (153, 15, '/com.esotericsoftware.asm/Handle.isInterface()%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (154, 15, '/com.esotericsoftware.asm/Handle.getTag()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (155, 15, '/com.esotericsoftware.asm/Handle.hashCode()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (156, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertGetObject(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.util%2FArrayList)%2Fjava.lang%2FVoidType', true, NULL, 276, 336, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (157, 16, '/com.esotericsoftware.reflectasm/FieldAccess.set(%2Fjava.lang%2FObject,%2Fjava.lang%2FString,%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, 46, 47, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (158, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertSetPrimitive(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.util%2FArrayList,%2Fcom.esotericsoftware.asm%2FType)%2Fjava.lang%2FVoidType', true, NULL, 389, 481, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (159, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setLong(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FLongType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (160, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getLong(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FLongType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (161, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getBoolean(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (162, 16, '/com.esotericsoftware.reflectasm/FieldAccess.get(%2Fjava.lang%2FObject,%2Fjava.lang%2FString)%2Fjava.lang%2FObject', true, NULL, 50, 50, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (163, 16, '/com.esotericsoftware.reflectasm/FieldAccess.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, 28, 28, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (164, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertConstructor(%2Fcom.esotericsoftware.asm%2FClassWriter)%2Fjava.lang%2FVoidType', true, NULL, 188, 195, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (165, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getInt(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (166, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getShort(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FShortType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (167, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getChar(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FCharType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (168, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getDouble(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FDoubleType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (169, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getFieldNames()%2Fjava.lang%2FString%5B%5D', true, NULL, 54, 54, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (170, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getIndex(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, 34, 36, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (171, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getFieldCount()%2Fjava.lang%2FIntegerType', true, NULL, 62, 62, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (172, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getFields()%2Fjava.lang.reflect%2FField%5B%5D', true, NULL, 66, 66, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (173, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getFloat(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FFloatType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (174, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getFieldTypes()%2Fjava.lang%2FClass%5B%5D', true, NULL, 58, 58, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (175, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertGetString(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.util%2FArrayList)%2Fjava.lang%2FVoidType', true, NULL, 339, 385, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (176, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getString(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (177, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getIndex(%2Fjava.lang.reflect%2FField)%2Fjava.lang%2FIntegerType', true, NULL, 40, 42, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (178, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertThrowExceptionForFieldType(%2Fcom.esotericsoftware.asm%2FMethodVisitor,%2Fjava.lang%2FString)%2Fcom.esotericsoftware.asm%2FMethodVisitor', true, NULL, 590, 601, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (179, 16, '/com.esotericsoftware.reflectasm/FieldAccess.getByte(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FByteType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (180, 16, '/com.esotericsoftware.reflectasm/FieldAccess.get(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FObject', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (181, 16, '/com.esotericsoftware.reflectasm/FieldAccess.get(%2Fjava.lang%2FClass)FieldAccess', true, NULL, 113, 183, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (182, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertGetPrimitive(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.util%2FArrayList,%2Fcom.esotericsoftware.asm%2FType)%2Fjava.lang%2FVoidType', true, NULL, 485, 572, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (183, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertSetObject(%2Fcom.esotericsoftware.asm%2FClassWriter,%2Fjava.lang%2FString,%2Fjava.util%2FArrayList)%2Fjava.lang%2FVoidType', true, NULL, 198, 273, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (184, 16, '/com.esotericsoftware.reflectasm/FieldAccess.insertThrowExceptionForFieldNotFound(%2Fcom.esotericsoftware.asm%2FMethodVisitor)%2Fcom.esotericsoftware.asm%2FMethodVisitor', true, NULL, 575, 586, '{"access": "private", "defined": true}');
INSERT INTO public.callables VALUES (185, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setBoolean(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (186, 16, '/com.esotericsoftware.reflectasm/FieldAccess.set(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (187, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setShort(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FShortType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (188, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setChar(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (189, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setByte(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FByteType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (190, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setInt(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (191, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setFloat(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FFloatType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (192, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setDouble(%2Fjava.lang%2FObject,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FDoubleType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": false}');
INSERT INTO public.callables VALUES (193, 16, '/com.esotericsoftware.reflectasm/FieldAccess.setFields(%2Fjava.lang.reflect%2FField%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, 70, 71, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (194, 17, '/com.esotericsoftware.asm/Attribute.isCodeAttribute()%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (195, 17, '/com.esotericsoftware.asm/Attribute.read(ClassReader,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FIntegerType,Label%5B%5D)Attribute', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (196, 17, '/com.esotericsoftware.asm/Attribute.a(ClassWriter,%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (197, 17, '/com.esotericsoftware.asm/Attribute.getLabels()Label%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (198, 17, '/com.esotericsoftware.asm/Attribute.a(ClassWriter,%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (199, 17, '/com.esotericsoftware.asm/Attribute.write(ClassWriter,%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)ByteVector', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (200, 17, '/com.esotericsoftware.asm/Attribute.isUnknown()%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (201, 17, '/com.esotericsoftware.asm/Attribute.%3Cinit%3E(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (202, 17, '/com.esotericsoftware.asm/Attribute.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (203, 18, '/com.esotericsoftware.asm/Label.a()Label', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (204, 18, '/com.esotericsoftware.asm/Label.a(%2Fjava.lang%2FLongType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (205, 18, '/com.esotericsoftware.asm/Label.a(Label)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (206, 18, '/com.esotericsoftware.asm/Label.toString()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (207, 18, '/com.esotericsoftware.asm/Label.a(MethodWriter,ByteVector,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (208, 18, '/com.esotericsoftware.asm/Label.b(Label,%2Fjava.lang%2FLongType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (209, 18, '/com.esotericsoftware.asm/Label.getOffset()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (210, 18, '/com.esotericsoftware.asm/Label.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (211, 18, '/com.esotericsoftware.asm/Label.a(%2Fjava.lang%2FLongType)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (212, 18, '/com.esotericsoftware.asm/Label.a(MethodWriter,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FByteType%5B%5D)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (213, 18, '/com.esotericsoftware.asm/Label.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (214, 19, '/com.esotericsoftware.asm/TypePath.getStepArgument(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (216, 19, '/com.esotericsoftware.asm/TypePath.%3Cinit%3E(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (217, 19, '/com.esotericsoftware.asm/TypePath.getLength()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (218, 19, '/com.esotericsoftware.asm/TypePath.toString()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (219, 19, '/com.esotericsoftware.asm/TypePath.getStep(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (220, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (221, 20, '/com.esotericsoftware.asm/ClassWriter.visitOuterClass(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (222, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FLongType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (223, 20, '/com.esotericsoftware.asm/ClassWriter.b(%2Fjava.lang%2FString)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (224, 20, '/com.esotericsoftware.asm/ClassWriter.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (225, 20, '/com.esotericsoftware.asm/ClassWriter.newInvokeDynamic(%2Fjava.lang%2FString,%2Fjava.lang%2FString,Handle,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (226, 20, '/com.esotericsoftware.asm/ClassWriter.newMethod(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (227, 20, '/com.esotericsoftware.asm/ClassWriter.toByteArray()%2Fjava.lang%2FByteType%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (228, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (229, 20, '/com.esotericsoftware.asm/ClassWriter.newUTF8(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (230, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (231, 20, '/com.esotericsoftware.asm/ClassWriter.b(Item)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (232, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (233, 20, '/com.esotericsoftware.asm/ClassWriter.newHandle(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (234, 20, '/com.esotericsoftware.asm/ClassWriter.newField(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (235, 20, '/com.esotericsoftware.asm/ClassWriter.newClass(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (236, 20, '/com.esotericsoftware.asm/ClassWriter.c(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (237, 20, '/com.esotericsoftware.asm/ClassWriter.newConst(%2Fjava.lang%2FObject)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (238, 20, '/com.esotericsoftware.asm/ClassWriter.c(Item)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (239, 20, '/com.esotericsoftware.asm/ClassWriter.c(%2Fjava.lang%2FString)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (240, 20, '/com.esotericsoftware.asm/ClassWriter.getCommonSuperClass(%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (241, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString,%2Fjava.lang%2FString,Handle,%2Fjava.lang%2FObject%5B%5D)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (242, 20, '/com.esotericsoftware.asm/ClassWriter.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (243, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString,%2Fjava.lang%2FString)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (244, 20, '/com.esotericsoftware.asm/ClassWriter.visitInnerClass(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (245, 20, '/com.esotericsoftware.asm/ClassWriter.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (246, 20, '/com.esotericsoftware.asm/ClassWriter.b(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (247, 20, '/com.esotericsoftware.asm/ClassWriter.newHandle(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (248, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (249, 20, '/com.esotericsoftware.asm/ClassWriter.newMethodType(%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (250, 20, '/com.esotericsoftware.asm/ClassWriter.visit(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (251, 20, '/com.esotericsoftware.asm/ClassWriter.newNameType(%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (252, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (253, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (254, 20, '/com.esotericsoftware.asm/ClassWriter.visitSource(%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (255, 20, '/com.esotericsoftware.asm/ClassWriter.a(Item)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (256, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FIntegerType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (257, 20, '/com.esotericsoftware.asm/ClassWriter.visitMethod(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString%5B%5D)MethodVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (258, 20, '/com.esotericsoftware.asm/ClassWriter.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (259, 20, '/com.esotericsoftware.asm/ClassWriter.visitField(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FObject)FieldVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (260, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FObject)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (261, 20, '/com.esotericsoftware.asm/ClassWriter.%3Cinit%3E(ClassReader,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (262, 20, '/com.esotericsoftware.asm/ClassWriter._clinit_()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (263, 20, '/com.esotericsoftware.asm/ClassWriter.%3Cclinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (264, 20, '/com.esotericsoftware.asm/ClassWriter.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (265, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FFloatType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (266, 20, '/com.esotericsoftware.asm/ClassWriter.a(%2Fjava.lang%2FDoubleType)Item', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (267, 21, '/com.esotericsoftware.asm/Opcodes.%3Cclinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (268, 22, '/com.esotericsoftware.asm/MethodWriter.visitInsnAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (269, 22, '/com.esotericsoftware.asm/MethodWriter.visitAnnotationDefault()AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (270, 22, '/com.esotericsoftware.asm/MethodWriter.visitTryCatchBlock(Label,Label,Label,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (271, 22, '/com.esotericsoftware.asm/MethodWriter.visitTryCatchAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (272, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (273, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FIntegerType,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (1, 1, '/com.esotericsoftware.asm/ByteVector.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (274, 22, '/com.esotericsoftware.asm/MethodWriter.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (275, 22, '/com.esotericsoftware.asm/MethodWriter.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (276, 22, '/com.esotericsoftware.asm/MethodWriter.b()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (277, 22, '/com.esotericsoftware.asm/MethodWriter.b(Frame)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (278, 22, '/com.esotericsoftware.asm/MethodWriter.b(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FShortType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (279, 22, '/com.esotericsoftware.asm/MethodWriter.f()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (280, 22, '/com.esotericsoftware.asm/MethodWriter.visitInvokeDynamicInsn(%2Fjava.lang%2FString,%2Fjava.lang%2FString,Handle,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (281, 22, '/com.esotericsoftware.asm/MethodWriter.visitLineNumber(%2Fjava.lang%2FIntegerType,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (282, 22, '/com.esotericsoftware.asm/MethodWriter.visitCode()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (283, 22, '/com.esotericsoftware.asm/MethodWriter.visitParameterAnnotation(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (284, 22, '/com.esotericsoftware.asm/MethodWriter.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (285, 22, '/com.esotericsoftware.asm/MethodWriter.visitTableSwitchInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,Label,Label%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (286, 22, '/com.esotericsoftware.asm/MethodWriter.visitTypeInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (287, 22, '/com.esotericsoftware.asm/MethodWriter.visitIincInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (288, 22, '/com.esotericsoftware.asm/MethodWriter.visitInsn(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (289, 22, '/com.esotericsoftware.asm/MethodWriter.visitIntInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (290, 22, '/com.esotericsoftware.asm/MethodWriter.visitParameter(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (291, 22, '/com.esotericsoftware.asm/MethodWriter.visitLdcInsn(%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (292, 22, '/com.esotericsoftware.asm/MethodWriter.visitJumpInsn(%2Fjava.lang%2FIntegerType,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (293, 22, '/com.esotericsoftware.asm/MethodWriter.visitLocalVariable(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,Label,Label,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (294, 22, '/com.esotericsoftware.asm/MethodWriter.visitMethodInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (295, 22, '/com.esotericsoftware.asm/MethodWriter.visitMultiANewArrayInsn(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (296, 22, '/com.esotericsoftware.asm/MethodWriter.visitLocalVariableAnnotation(%2Fjava.lang%2FIntegerType,TypePath,Label%5B%5D,Label%5B%5D,%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (297, 22, '/com.esotericsoftware.asm/MethodWriter.visitLookupSwitchInsn(Label,%2Fjava.lang%2FIntegerType%5B%5D,Label%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (298, 22, '/com.esotericsoftware.asm/MethodWriter.visitMaxs(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (299, 22, '/com.esotericsoftware.asm/MethodWriter.visitFieldInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (300, 22, '/com.esotericsoftware.asm/MethodWriter.%3Cinit%3E(ClassWriter,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString%5B%5D,%2Fjava.lang%2FBooleanType,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (301, 22, '/com.esotericsoftware.asm/MethodWriter.visitLabel(Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (302, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (303, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (304, 22, '/com.esotericsoftware.asm/MethodWriter.visitVarInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (305, 22, '/com.esotericsoftware.asm/MethodWriter.visitFrame(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (306, 22, '/com.esotericsoftware.asm/MethodWriter.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (307, 22, '/com.esotericsoftware.asm/MethodWriter.c()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (308, 22, '/com.esotericsoftware.asm/MethodWriter.c(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (309, 22, '/com.esotericsoftware.asm/MethodWriter.e()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (310, 22, '/com.esotericsoftware.asm/MethodWriter.d()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (311, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (312, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (313, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FIntegerType%5B%5D,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (314, 22, '/com.esotericsoftware.asm/MethodWriter.a(Label,Label%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (315, 22, '/com.esotericsoftware.asm/MethodWriter.a(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (316, 22, '/com.esotericsoftware.asm/MethodWriter.a(ByteVector)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (317, 22, '/com.esotericsoftware.asm/MethodWriter.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (318, 23, '/com.esotericsoftware.reflectasm/PublicConstructorAccess.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, 17, 17, '{"access": "public", "defined": true}');
INSERT INTO public.callables VALUES (319, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.lang%2FObject%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,Label%5B%5D)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (320, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FBooleanType,%2Fjava.lang%2FBooleanType,Context)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (321, 24, '/com.esotericsoftware.asm/ClassReader.getInterfaces()%2Fjava.lang%2FString%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (322, 24, '/com.esotericsoftware.asm/ClassReader.readUnsignedShort(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (323, 24, '/com.esotericsoftware.asm/ClassReader.a(ClassWriter)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (324, 24, '/com.esotericsoftware.asm/ClassReader.readLabel(%2Fjava.lang%2FIntegerType,Label%5B%5D)Label', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "protected", "defined": true}');
INSERT INTO public.callables VALUES (325, 24, '/com.esotericsoftware.asm/ClassReader.readInt(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (326, 24, '/com.esotericsoftware.asm/ClassReader.getSuperName()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (327, 24, '/com.esotericsoftware.asm/ClassReader.readByte(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (328, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (329, 24, '/com.esotericsoftware.asm/ClassReader.a(ClassVisitor,Context,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (330, 24, '/com.esotericsoftware.asm/ClassReader.a(Attribute%5B%5D,%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FIntegerType,Label%5B%5D)Attribute', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (331, 24, '/com.esotericsoftware.asm/ClassReader.accept(ClassVisitor,Attribute%5B%5D,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (332, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FBooleanType,AnnotationVisitor)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (333, 24, '/com.esotericsoftware.asm/ClassReader.getClassName()%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (334, 24, '/com.esotericsoftware.asm/ClassReader.getItem(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (335, 24, '/com.esotericsoftware.asm/ClassReader.getMaxStringLength()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (336, 24, '/com.esotericsoftware.asm/ClassReader.a(MethodVisitor,Context,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FIntegerType%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (337, 24, '/com.esotericsoftware.asm/ClassReader.b(ClassVisitor,Context,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (338, 24, '/com.esotericsoftware.asm/ClassReader.getAccess()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (339, 24, '/com.esotericsoftware.asm/ClassReader.b(MethodVisitor,Context,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (340, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D,%2Fjava.lang%2FString,AnnotationVisitor)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (342, 24, '/com.esotericsoftware.asm/ClassReader.a(Context,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (343, 24, '/com.esotericsoftware.asm/ClassReader.readShort(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FShortType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (344, 24, '/com.esotericsoftware.asm/ClassReader.getItemCount()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (345, 24, '/com.esotericsoftware.asm/ClassReader.readLong(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FLongType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (346, 24, '/com.esotericsoftware.asm/ClassReader.readClass(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (348, 24, '/com.esotericsoftware.asm/ClassReader.a(ClassWriter,Item%5B%5D,%2Fjava.lang%2FCharType%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (347, 24, '/com.esotericsoftware.asm/ClassReader.a(MethodVisitor,Context,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (349, 24, '/com.esotericsoftware.asm/ClassReader.readConst(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D)%2Fjava.lang%2FObject', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (350, 24, '/com.esotericsoftware.asm/ClassReader.a(%2Fjava.io%2FInputStream,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FByteType%5B%5D', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (351, 24, '/com.esotericsoftware.asm/ClassReader.readUTF8(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FCharType%5B%5D)%2Fjava.lang%2FString', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (352, 24, '/com.esotericsoftware.asm/ClassReader.%3Cinit%3E(%2Fjava.lang%2FByteType%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (353, 24, '/com.esotericsoftware.asm/ClassReader.a(Context)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (354, 24, '/com.esotericsoftware.asm/ClassReader.%3Cinit%3E(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (355, 24, '/com.esotericsoftware.asm/ClassReader.%3Cinit%3E(%2Fjava.io%2FInputStream)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (356, 24, '/com.esotericsoftware.asm/ClassReader.a()%2Fjava.lang%2FIntegerType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "private", "defined": true}');
INSERT INTO public.callables VALUES (357, 24, '/com.esotericsoftware.asm/ClassReader.%3Cinit%3E(%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (358, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FFloatType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (359, 25, '/com.esotericsoftware.asm/Item.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (360, 25, '/com.esotericsoftware.asm/Item.a(Item)%2Fjava.lang%2FBooleanType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (361, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (362, 25, '/com.esotericsoftware.asm/Item.%3Cinit%3E()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (363, 25, '/com.esotericsoftware.asm/Item.%3Cinit%3E(%2Fjava.lang%2FIntegerType,Item)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (364, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (365, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (366, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FLongType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (367, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (368, 25, '/com.esotericsoftware.asm/Item.a(%2Fjava.lang%2FDoubleType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "packagePrivate", "defined": true}');
INSERT INTO public.callables VALUES (369, 26, '/com.esotericsoftware.asm/MethodVisitor.visitVarInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (370, 26, '/com.esotericsoftware.asm/MethodVisitor.visitTryCatchAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (371, 26, '/com.esotericsoftware.asm/MethodVisitor.visitMultiANewArrayInsn(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (372, 26, '/com.esotericsoftware.asm/MethodVisitor.visitAnnotationDefault()AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (373, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLdcInsn(%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (375, 26, '/com.esotericsoftware.asm/MethodVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (376, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLookupSwitchInsn(Label,%2Fjava.lang%2FIntegerType%5B%5D,Label%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (377, 26, '/com.esotericsoftware.asm/MethodVisitor.visitAttribute(Attribute)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (378, 26, '/com.esotericsoftware.asm/MethodVisitor.visitAnnotation(%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (379, 26, '/com.esotericsoftware.asm/MethodVisitor.visitEnd()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (380, 26, '/com.esotericsoftware.asm/MethodVisitor.visitInvokeDynamicInsn(%2Fjava.lang%2FString,%2Fjava.lang%2FString,Handle,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (381, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLabel(Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (382, 26, '/com.esotericsoftware.asm/MethodVisitor.visitJumpInsn(%2Fjava.lang%2FIntegerType,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (383, 26, '/com.esotericsoftware.asm/MethodVisitor.visitInsnAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (384, 26, '/com.esotericsoftware.asm/MethodVisitor.visitIntInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (385, 26, '/com.esotericsoftware.asm/MethodVisitor.visitFieldInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (386, 26, '/com.esotericsoftware.asm/MethodVisitor.visitInsn(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (387, 26, '/com.esotericsoftware.asm/MethodVisitor.visitFrame(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (388, 26, '/com.esotericsoftware.asm/MethodVisitor.visitIincInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (389, 26, '/com.esotericsoftware.asm/MethodVisitor.visitTypeAnnotation(%2Fjava.lang%2FIntegerType,TypePath,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (390, 26, '/com.esotericsoftware.asm/MethodVisitor.%3Cinit%3E(%2Fjava.lang%2FIntegerType,MethodVisitor)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (391, 26, '/com.esotericsoftware.asm/MethodVisitor.visitCode()%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (392, 26, '/com.esotericsoftware.asm/MethodVisitor.visitParameterAnnotation(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (393, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLineNumber(%2Fjava.lang%2FIntegerType,Label)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (394, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLocalVariable(%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,Label,Label,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (395, 26, '/com.esotericsoftware.asm/MethodVisitor.visitMaxs(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (396, 26, '/com.esotericsoftware.asm/MethodVisitor.visitMethodInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (397, 26, '/com.esotericsoftware.asm/MethodVisitor.visitTypeInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (398, 26, '/com.esotericsoftware.asm/MethodVisitor.visitTryCatchBlock(Label,Label,Label,%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (399, 26, '/com.esotericsoftware.asm/MethodVisitor.visitTableSwitchInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,Label,Label%5B%5D)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (400, 26, '/com.esotericsoftware.asm/MethodVisitor.visitMethodInsn(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (401, 26, '/com.esotericsoftware.asm/MethodVisitor.visitParameter(%2Fjava.lang%2FString,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (402, -1, '/java.lang/String.replace(CharType,CharType)String', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (403, -1, '/java.lang/String.equals(Object)BooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (404, -1, '/java.lang/String.charAt(IntegerType)CharType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (405, -1, '/java.lang/String.%3Cinit%3E(CharType%5B%5D,IntegerType,IntegerType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (406, -1, '/java.lang.ref/WeakReference.%3Cinit%3E(%2Fjava.lang%2FObject)%2Fjava.lang%2FVoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (407, -1, '/java.lang/Short.%3Cinit%3E(ShortType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (408, -1, '/java.lang/StringBuffer.append(CharType)StringBuffer', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (409, -1, '/java.lang/StringBuffer.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (410, -1, '/java.lang/StringBuffer.toString()String', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (411, -1, '/java.lang/StringBuffer.%3Cinit%3E(String)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (412, -1, '/java.lang/StringBuffer.append(String)StringBuffer', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (413, -1, '/java.util/HashSet.%3Cinit%3E()%2Fjava.lang%2FVoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (414, -1, '/java.lang/ClassLoader.getSystemClassLoader()ClassLoader', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (415, -1, '/java.lang/ClassLoader.getSystemResourceAsStream(String)%2Fjava.io%2FInputStream', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (416, -1, '/java.lang/ClassLoader.loadClass(String,BooleanType)Class', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (417, -1, '/java.lang/ClassLoader.%3Cinit%3E(ClassLoader)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (418, -1, '/java.io/IOException.%3Cinit%3E(%2Fjava.lang%2FString)%2Fjava.lang%2FVoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (419, -1, '/java.lang/Math.min(IntegerType,IntegerType)IntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (420, -1, '/java.lang/Math.max(IntegerType,IntegerType)IntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (421, -1, '/java.lang/RuntimeException.%3Cinit%3E(Throwable)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (422, -1, '/java.lang/RuntimeException.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (423, -1, '/java.lang/RuntimeException.%3Cinit%3E(String,Throwable)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (424, -1, '/java.lang/RuntimeException.%3Cinit%3E(String)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (425, -1, '/java.lang/Byte.%3Cinit%3E(ByteType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (426, -1, '/java.lang/Long.%3Cinit%3E(LongType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (427, -1, '/com.esotericsoftware.reflectasm/AccessClassLoader.defineClass(%2Fjava.lang%2FString,%2Fjava.lang%2FByteType%5B%5D,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.security%2FProtectionDomain)%2Fjava.lang%2FClass', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (428, -1, '/com.esotericsoftware.reflectasm/AccessClassLoader.getParent()%2Fjava.lang%2FClassLoader', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (429, -1, '/java.lang/Float.%3Cinit%3E(FloatType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (430, -1, '/java.lang/Float.floatToRawIntBits(FloatType)IntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (431, -1, '/java.lang/Float.intBitsToFloat(IntegerType)FloatType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (432, -1, '/java.lang/Exception.toString()String', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (433, -1, '/java.lang.reflect/Modifier.isPublic(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (434, -1, '/java.lang.reflect/Modifier.isPrivate(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (435, -1, '/java.lang.reflect/Modifier.isStatic(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (436, -1, '/java.util/WeakHashMap.%3Cinit%3E()%2Fjava.lang%2FVoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (437, -1, '/java.lang/Double.longBitsToDouble(LongType)DoubleType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (438, -1, '/java.lang/Double.%3Cinit%3E(DoubleType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (439, -1, '/java.lang/Double.doubleToRawLongBits(DoubleType)LongType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (440, -1, '/java.lang/Character.%3Cinit%3E(CharType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (441, -1, '/java.util/Arrays.equals(%2Fjava.lang%2FObject%5B%5D,%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (442, -1, '/java.util/Arrays.toString(%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FString', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (443, -1, '/java.lang/System.arraycopy(Object,IntegerType,Object,IntegerType,IntegerType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (444, -1, '/java.lang/System.identityHashCode(Object)IntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (445, -1, '/java.lang/Integer.valueOf(IntegerType)Integer', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (446, -1, '/java.lang/Integer.%3Cinit%3E(IntegerType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (447, -1, '/java.lang/Class.getName()String', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (448, -1, '/java.lang/Class.getDeclaredMethod(String,Class%5B%5D)%2Fjava.lang.reflect%2FMethod', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (449, -1, '/java.lang/Class.forName(String,BooleanType,ClassLoader)Class', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (450, -1, '/java.util/ArrayList.size()%2Fjava.lang%2FIntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (451, -1, '/java.util/ArrayList.isEmpty()%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (452, -1, '/java.util/ArrayList.get(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FObject', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (453, -1, '/java.util/ArrayList.%3Cinit%3E()%2Fjava.lang%2FVoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (454, -1, '/java.util/ArrayList.add(%2Fjava.lang%2FObject)%2Fjava.lang%2FBooleanType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (455, -1, '/java.util/ArrayList.toArray(%2Fjava.lang%2FObject%5B%5D)%2Fjava.lang%2FObject%5B%5D', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (456, -1, '/java.lang/IllegalStateException.%3Cinit%3E(String)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (457, -1, '/java.lang/IllegalStateException.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (458, -1, '/java.lang/StringBuilder.append(String)StringBuilder', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (459, -1, '/java.lang/StringBuilder.toString()String', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (460, -1, '/java.lang/StringBuilder.setLength(IntegerType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (461, -1, '/java.lang/StringBuilder.%3Cinit%3E(IntegerType)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (462, -1, '/java.lang/StringBuilder.append(CharType)StringBuilder', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (463, -1, '/java.lang/StringBuilder.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (464, -1, '/java.lang/IllegalArgumentException.%3Cinit%3E(String)VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (465, -1, '/java.lang/IllegalArgumentException.%3Cinit%3E()VoidType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (466, -1, '/java.lang/Object.getClass()Class', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (215, 19, '/com.esotericsoftware.asm/TypePath.fromString(%2Fjava.lang%2FString)TypePath', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (341, 24, '/com.esotericsoftware.asm/ClassReader.accept(ClassVisitor,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');
INSERT INTO public.callables VALUES (870, -1, '/java.lang/Object.hashCode()IntegerType', false, NULL, NULL, NULL, '{}');
INSERT INTO public.callables VALUES (374, 26, '/com.esotericsoftware.asm/MethodVisitor.visitLocalVariableAnnotation(%2Fjava.lang%2FIntegerType,TypePath,Label%5B%5D,Label%5B%5D,%2Fjava.lang%2FIntegerType%5B%5D,%2Fjava.lang%2FString,%2Fjava.lang%2FBooleanType)AnnotationVisitor', true, NULL, NULL, NULL, '{"last": "notFound", "first": "notFound", "access": "public", "defined": true}');


--
-- Data for Name: dependencies; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.dependencies VALUES (1, 2, '{4.8.2}', NULL, NULL, NULL, '{"type": "", "scope": "test", "groupId": "junit", "optional": false, "artifactId": "junit", "classifier": "", "exclusions": [], "versionConstraints": [{"lowerBound": "4.8.2", "upperBound": "4.8.2", "isLowerHardRequirement": false, "isUpperHardRequirement": false}]}');


--
-- Data for Name: edges; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.edges VALUES (246, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (232, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 247, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 251, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (246, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (232, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (182, 257, '{"(527,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (232, 246, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])","(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (102, 253, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (182, 210, '{"(534,special,[/com.esotericsoftware.asm/Label])","(544,special,[/com.esotericsoftware.asm/Label])","(538,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (232, 234, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (223, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 30, '{"(297,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (232, 226, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (102, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (223, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (175, 257, '{"(340,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (182, 43, '{"(537,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (175, 210, '{"(357,special,[/com.esotericsoftware.asm/Label])","(347,special,[/com.esotericsoftware.asm/Label])","(351,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (156, 63, '{"(300,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (182, 37, '{"(563,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (239, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (156, 62, '{"(299,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (182, 38, '{"(487,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (104, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (100, 420, '{"(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (223, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (100, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (227, 317, '{"(404,virtual,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (183, 257, '{"(199,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 316, '{"(404,virtual,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (103, 220, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (27, 26, '{"(404,special,[/com.esotericsoftware.asm/Handler])"}', '{}');
INSERT INTO public.edges VALUES (27, 27, '{"(404,static,[/com.esotericsoftware.asm/Handler])"}', '{}');
INSERT INTO public.edges VALUES (183, 210, '{"(208,special,[/com.esotericsoftware.asm/Label])","(207,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (182, 63, '{"(489,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (182, 62, '{"(537,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (103, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 282, '{"(137,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(127,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (15, 306, '{"(272,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(132,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (241, 31, '{"(404,virtual,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (158, 257, '{"(434,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 305, '{"(157,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(155,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(258,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (15, 288, '{"(263,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(254,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(168,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(270,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(130,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(261,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(226,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 210, '{"(442,special,[/com.esotericsoftware.asm/Label])","(452,special,[/com.esotericsoftware.asm/Label])","(446,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (15, 289, '{"(167,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (15, 301, '{"(257,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(153,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (236, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 291, '{"(264,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 38, '{"(255,virtual,[/com.esotericsoftware.asm/Type])","(263,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (182, 463, '{"(527,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (241, 155, '{"(404,virtual,[/com.esotericsoftware.asm/Handle])"}', '{}');
INSERT INTO public.edges VALUES (241, 153, '{"(404,virtual,[/com.esotericsoftware.asm/Handle])"}', '{}');
INSERT INTO public.edges VALUES (183, 35, '{"(258,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (15, 298, '{"(271,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(131,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (15, 285, '{"(149,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 43, '{"(445,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (236, 238, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 286, '{"(176,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(141,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(172,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(262,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(207,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(260,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(200,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(204,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(196,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(192,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(188,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(184,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(180,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (15, 304, '{"(266,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(144,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(128,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(166,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(158,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(140,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(142,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (104, 38, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (183, 63, '{"(221,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (182, 468, '{"(549,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (158, 37, '{"(472,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (241, 870, '{"(404,virtual,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (239, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (183, 62, '{"(213,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (158, 38, '{"(392,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (15, 79, '{"(276,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (15, 78, '{"(114,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (98, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 87, '{"(116,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (300, 301, '{"(404,virtual,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (239, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 121, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 116, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (158, 63, '{"(394,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (175, 468, '{"(361,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (158, 62, '{"(445,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (227, 133, '{"(404,virtual,[/com.esotericsoftware.asm/FieldWriter])"}', '{}');
INSERT INTO public.edges VALUES (239, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 131, '{"(404,virtual,[/com.esotericsoftware.asm/FieldWriter])"}', '{}');
INSERT INTO public.edges VALUES (238, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (229, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (102, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (229, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (158, 463, '{"(434,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (102, 424, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (245, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (158, 468, '{"(456,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (235, 248, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (317, 121, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (273, 124, '{"(404,special,[/com.esotericsoftware.asm/Edge])"}', '{}');
INSERT INTO public.edges VALUES (237, 260, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (178, 463, '{"(594,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (106, 420, '{"(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (257, 300, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (106, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (91, 123, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (93, 92, '{"(404,special,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (103, 419, '{"(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (316, 121, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (316, 116, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (316, 115, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])","(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (234, 228, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 396, '{"(267,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(269,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(229,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(129,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(244,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(265,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(235,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(201,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(197,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(250,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(222,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(193,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(189,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(185,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(181,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(241,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(177,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(268,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(232,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(173,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(247,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(238,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (95, 119, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (300, 375, '{"(404,special,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (314, 273, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (259, 132, '{"(404,special,[/com.esotericsoftware.asm/FieldWriter])"}', '{}');
INSERT INTO public.edges VALUES (90, 120, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (314, 309, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (238, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (233, 247, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (97, 420, '{"(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (247, 232, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (97, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (89, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (362, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (225, 241, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 17, '{"(92,static,[/com.esotericsoftware.reflectasm/MethodAccess])"}', '{}');
INSERT INTO public.edges VALUES (94, 117, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (240, 424, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (15, 23, '{"(96,static,[/com.esotericsoftware.reflectasm/MethodAccess])"}', '{}');
INSERT INTO public.edges VALUES (229, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (359, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (226, 230, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (313, 312, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 202, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (229, 10, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (227, 198, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (240, 466, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 196, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (240, 449, '{"(404,static,[/java.lang/Class])","(404,static,[/java.lang/Class])"}', '{}');
INSERT INTO public.edges VALUES (227, 7, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (363, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (227, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (227, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (227, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (227, 352, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (314, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (249, 239, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (242, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (242, 122, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (300, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (251, 243, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 341, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (317, 198, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])","(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (245, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (229, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (69, 66, '{"(404,special,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (277, 302, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (245, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (15, 264, '{"(121,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (277, 276, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (229, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (358, 430, '{"(404,static,[/java.lang/Float])"}', '{}');
INSERT INTO public.edges VALUES (311, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (311, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (316, 202, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])","(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (316, 198, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (316, 196, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])","(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (307, 272, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 227, '{"(275,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 250, '{"(123,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (227, 227, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 224, '{"(274,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (368, 439, '{"(404,static,[/java.lang/Double])"}', '{}');
INSERT INTO public.edges VALUES (316, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (316, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (15, 257, '{"(135,virtual,[/com.esotericsoftware.asm/ClassWriter])","(126,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (316, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (316, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (15, 210, '{"(148,special,[/com.esotericsoftware.asm/Label])","(147,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (250, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (300, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (250, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (272, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (300, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (272, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (244, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (96, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (15, 38, '{"(210,virtual,[/com.esotericsoftware.asm/Type])","(204,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (310, 315, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 30, '{"(214,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (310, 303, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 35, '{"(207,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (208, 124, '{"(404,special,[/com.esotericsoftware.asm/Edge])"}', '{}');
INSERT INTO public.edges VALUES (300, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (244, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (245, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (310, 313, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (18, 467, '{"(30,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (310, 312, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (310, 278, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (310, 308, '{"(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])","(404,static,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (317, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 63, '{"(224,virtual,[/com.esotericsoftware.asm/Type])","(170,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (124, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (318, 143, '{"(17,special,[/com.esotericsoftware.reflectasm/ConstructorAccess])"}', '{}');
INSERT INTO public.edges VALUES (15, 62, '{"(169,static,[/com.esotericsoftware.asm/Type])","(224,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (311, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (300, 44, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (26, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (15, 463, '{"(111,special,[/java.lang/StringBuilder])","(110,special,[/java.lang/StringBuilder])","(286,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (15, 464, '{"(86,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (279, 302, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (17, 434, '{"(296,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (23, 17, '{"(302,static,[/com.esotericsoftware.reflectasm/MethodAccess])"}', '{}');
INSERT INTO public.edges VALUES (279, 276, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (316, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (254, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (244, 248, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (23, 23, '{"(304,static,[/com.esotericsoftware.reflectasm/MethodAccess])"}', '{}');
INSERT INTO public.edges VALUES (254, 4, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (14, 463, '{"(51,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (14, 464, '{"(51,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (272, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 453, '{"(88,special,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (15, 435, '{"(218,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (15, 423, '{"(286,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (242, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (227, 424, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (244, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (22, 463, '{"(65,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (22, 464, '{"(65,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (267, 446, '{"(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])","(404,special,[/java.lang/Integer])"}', '{}');
INSERT INTO public.edges VALUES (242, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (244, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 461, '{"(151,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (309, 109, '{"(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (21, 463, '{"(58,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (92, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (21, 464, '{"(58,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (92, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (21, 441, '{"(57,static,[/java.util/Arrays])"}', '{}');
INSERT INTO public.edges VALUES (21, 442, '{"(58,static,[/java.util/Arrays])"}', '{}');
INSERT INTO public.edges VALUES (221, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (274, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (317, 424, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (221, 251, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (269, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (276, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (254, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (314, 203, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (198, 199, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (128, 134, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (242, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (196, 199, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (272, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (130, 126, '{"(404,special,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (305, 311, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (196, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (196, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (125, 136, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (71, 250, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (196, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (305, 302, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (305, 276, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (305, 279, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (129, 137, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (67, 245, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (307, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (307, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (378, 274, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (74, 258, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (127, 135, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (288, 309, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (310, 197, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (195, 201, '{"(404,special,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (372, 269, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (198, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (66, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (76, 224, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (207, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (310, 7, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (207, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (299, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (310, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (66, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (310, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (310, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (310, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (196, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (377, 275, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (75, 259, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (210, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (199, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (391, 282, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (73, 244, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (201, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (287, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (292, 273, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (268, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (375, 390, '{"(404,special,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (274, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (268, 122, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (379, 306, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (72, 257, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (288, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (274, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (292, 309, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (301, 273, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (269, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (385, 299, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (65, 221, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (292, 301, '{"(404,virtual,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (275, 194, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (207, 213, '{"(404,special,[/com.esotericsoftware.asm/Label])","(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (387, 305, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (70, 254, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (289, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (6, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (213, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (279, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (208, 211, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (208, 205, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (208, 204, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (309, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (388, 287, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (280, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (68, 242, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (309, 212, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (5, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (292, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (386, 288, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (4, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (274, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (301, 109, '{"(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (299, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (383, 268, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (2, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (305, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (297, 314, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (310, 443, '{"(404,static,[/java.lang/System])","(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (305, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (291, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (384, 289, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (53, 52, '{"(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (298, 124, '{"(404,special,[/com.esotericsoftware.asm/Edge])","(404,special,[/com.esotericsoftware.asm/Edge])"}', '{}');
INSERT INTO public.edges VALUES (53, 33, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (195, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (305, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (9, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (298, 27, '{"(404,static,[/com.esotericsoftware.asm/Handler])"}', '{}');
INSERT INTO public.edges VALUES (298, 302, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (287, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (287, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (298, 276, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (298, 277, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])","(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (287, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (380, 280, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (3, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (298, 310, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (287, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (209, 456, '{"(404,special,[/java.lang/IllegalStateException])"}', '{}');
INSERT INTO public.edges VALUES (296, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (68, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (1, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (288, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (299, 228, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (11, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (382, 292, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (126, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (268, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (206, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (323, 359, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (7, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (323, 358, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (323, 368, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (305, 253, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (126, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (323, 367, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (323, 366, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (268, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (12, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (323, 361, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (381, 301, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (206, 444, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (323, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])","(404,virtual,[/com.esotericsoftware.asm/Item])","(404,virtual,[/com.esotericsoftware.asm/Item])","(404,virtual,[/com.esotericsoftware.asm/Item])","(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (305, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (289, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (289, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (52, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (39, 52, '{"(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (8, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (10, 8, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (373, 291, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (10, 4, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (280, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (347, 275, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (298, 101, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (298, 104, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (347, 299, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 305, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 287, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (280, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (347, 288, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 268, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 289, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (393, 281, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 280, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 292, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 301, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 291, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 281, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 293, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (285, 314, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (347, 296, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 297, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 298, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 294, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (352, 357, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 295, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (294, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (292, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (347, 285, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 270, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (292, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (347, 286, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 304, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (394, 293, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (354, 352, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (295, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (354, 350, '{"(404,static,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (270, 26, '{"(404,special,[/com.esotericsoftware.asm/Handler])"}', '{}');
INSERT INTO public.edges VALUES (268, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (4, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (374, 296, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (283, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])","(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (355, 352, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (127, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (348, 359, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (291, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (291, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (355, 350, '{"(404,static,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (348, 364, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (280, 241, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (45, 39, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (305, 457, '{"(404,special,[/java.lang/IllegalStateException])"}', '{}');
INSERT INTO public.edges VALUES (376, 297, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (305, 420, '{"(404,static,[/java.lang/Math])","(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (390, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (329, 134, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])","(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (281, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (329, 136, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (329, 137, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (329, 135, '{"(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])","(404,virtual,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (390, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (9, 443, '{"(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (281, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (271, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (395, 298, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (293, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])","(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (271, 122, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (293, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (332, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (323, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (396, 294, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (357, 343, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (296, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (357, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (47, 62, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (291, 260, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (292, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])","(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (292, 203, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (280, 44, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (296, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (296, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (336, 271, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (323, 328, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (296, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (292, 207, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (323, 348, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 123, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (304, 309, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (340, 119, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (340, 120, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])","(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (340, 117, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (356, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (284, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (400, 294, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (284, 122, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (356, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (297, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (297, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (304, 301, '{"(404,virtual,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (297, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (301, 212, '{"(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (323, 327, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (323, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (323, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 96, '{"(404,special,[/com.esotericsoftware.asm/Context])"}', '{}');
INSERT INTO public.edges VALUES (323, 345, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (323, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (37, 37, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (371, 295, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (323, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (37, 36, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (37, 49, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (10, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (50, 41, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (353, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (401, 290, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (294, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (294, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (286, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (81, 82, '{"(32,special,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (38, 40, '{"(404,special,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (293, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (342, 327, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (81, 88, '{"(31,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (342, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (342, 324, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (342, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 274, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (392, 283, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (337, 269, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (337, 275, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (295, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (337, 282, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (337, 306, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (304, 102, '{"(404,virtual,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (295, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (348, 7, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (296, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (37, 405, '{"(404,special,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (30, 41, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (348, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (354, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (354, 415, '{"(404,static,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (37, 411, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (290, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (399, 285, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (348, 356, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 290, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 195, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (337, 284, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (290, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (298, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (396, 396, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (283, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (50, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (357, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (370, 271, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (339, 283, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 353, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 342, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (294, 230, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (283, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (297, 207, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (357, 465, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (49, 39, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (348, 349, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (347, 320, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (348, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 336, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (348, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (400, 396, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (38, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (348, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (295, 248, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (49, 36, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (298, 203, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (398, 270, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (285, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (285, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (285, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (329, 342, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (79, 86, '{"(57,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (298, 208, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (347, 327, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 349, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (339, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (329, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 324, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 343, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (271, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (329, 330, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (347, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (30, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (347, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (323, 431, '{"(404,static,[/java.lang/Float])"}', '{}');
INSERT INTO public.edges VALUES (323, 437, '{"(404,static,[/java.lang/Double])"}', '{}');
INSERT INTO public.edges VALUES (298, 45, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (389, 284, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (271, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (86, 83, '{"(73,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (329, 349, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (329, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (320, 319, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (329, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (329, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (383, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (342, 216, '{"(404,special,[/com.esotericsoftware.asm/TypePath])"}', '{}');
INSERT INTO public.edges VALUES (294, 44, '{"(404,static,[/com.esotericsoftware.asm/Type])","(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (78, 82, '{"(142,special,[/com.esotericsoftware.reflectasm/AccessClassLoader])","(127,special,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (397, 286, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (290, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (55, 41, '{"(404,static,[/com.esotericsoftware.asm/Type])","(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (78, 88, '{"(121,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (332, 340, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (320, 324, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (284, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (320, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (369, 304, '{"(404,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (283, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (284, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (48, 40, '{"(404,special,[/com.esotericsoftware.asm/Type])","(404,special,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (35, 405, '{"(404,special,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (347, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (340, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (298, 420, '{"(404,static,[/java.lang/Math])","(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (332, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (332, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (286, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (329, 259, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (350, 443, '{"(404,static,[/java.lang/System])","(404,static,[/java.lang/System])"}', '{}');
INSERT INTO public.edges VALUES (54, 39, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (340, 349, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (336, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 345, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (330, 201, '{"(404,special,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (340, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (145, 282, '{"(122,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (145, 306, '{"(127,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (283, 45, '{"(404,static,[/com.esotericsoftware.asm/Type])","(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (304, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (304, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (330, 195, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])","(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (304, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (145, 288, '{"(125,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (328, 405, '{"(404,special,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (285, 207, '{"(404,virtual,[/com.esotericsoftware.asm/Label])","(404,virtual,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (271, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (336, 327, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (138, 85, '{"(108,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (138, 79, '{"(99,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (336, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (87, 77, '{"(47,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (138, 78, '{"(53,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (350, 418, '{"(404,special,[/java.io/IOException])"}', '{}');
INSERT INTO public.edges VALUES (336, 324, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (145, 298, '{"(126,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (138, 87, '{"(55,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (336, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (55, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (270, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (336, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (140, 282, '{"(132,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (140, 306, '{"(138,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (140, 288, '{"(134,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(136,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (58, 52, '{"(404,special,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (145, 304, '{"(123,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (28, 48, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (348, 870, '{"(404,virtual,[/java.lang/Object])","(404,virtual,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (319, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (319, 324, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (140, 298, '{"(137,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (28, 64, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (48, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (144, 282, '{"(143,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (319, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (144, 306, '{"(163,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (286, 248, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (348, 31, '{"(404,virtual,[/java.lang/Object])","(404,virtual,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (144, 288, '{"(153,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(160,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(149,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(151,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(157,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(146,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (140, 286, '{"(133,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (284, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (144, 291, '{"(158,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (283, 420, '{"(404,static,[/java.lang/Math])"}', '{}');
INSERT INTO public.edges VALUES (144, 298, '{"(154,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(161,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (341, 331, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 356, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (144, 286, '{"(156,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(145,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(148,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (144, 304, '{"(147,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 342, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 328, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 329, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 330, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 337, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 342, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (29, 39, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (337, 347, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 64, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (331, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 340, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 330, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (331, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (374, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (304, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (337, 339, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])","(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (339, 332, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (337, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 431, '{"(404,static,[/java.lang/Float])"}', '{}');
INSERT INTO public.edges VALUES (336, 216, '{"(404,special,[/com.esotericsoftware.asm/TypePath])"}', '{}');
INSERT INTO public.edges VALUES (340, 437, '{"(404,static,[/java.lang/Double])"}', '{}');
INSERT INTO public.edges VALUES (116, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (114, 93, '{"(404,special,[/com.esotericsoftware.asm/AnnotationVisitor])"}', '{}');
INSERT INTO public.edges VALUES (138, 145, '{"(94,static,[/com.esotericsoftware.reflectasm/ConstructorAccess])"}', '{}');
INSERT INTO public.edges VALUES (138, 140, '{"(95,static,[/com.esotericsoftware.reflectasm/ConstructorAccess])"}', '{}');
INSERT INTO public.edges VALUES (138, 144, '{"(96,static,[/com.esotericsoftware.reflectasm/ConstructorAccess])"}', '{}');
INSERT INTO public.edges VALUES (340, 425, '{"(404,special,[/java.lang/Byte])"}', '{}');
INSERT INTO public.edges VALUES (340, 407, '{"(404,special,[/java.lang/Short])"}', '{}');
INSERT INTO public.edges VALUES (145, 396, '{"(124,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (339, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (340, 440, '{"(404,special,[/java.lang/Character])"}', '{}');
INSERT INTO public.edges VALUES (339, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (59, 62, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (331, 250, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 245, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])","(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 258, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 224, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 244, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 221, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 254, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (331, 242, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])","(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (400, 464, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (115, 121, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (140, 396, '{"(135,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (338, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (115, 118, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (337, 257, '{"(404,virtual,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (64, 39, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (333, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (181, 79, '{"(173,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (181, 78, '{"(142,static,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (181, 87, '{"(144,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (144, 396, '{"(150,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(152,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(159,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (321, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (215, 7, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (215, 6, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (119, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (215, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (321, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (62, 30, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (339, 45, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (120, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (62, 64, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (401, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (61, 55, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (61, 64, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (57, 50, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (81, 436, '{"(28,special,[/java.util/WeakHashMap])"}', '{}');
INSERT INTO public.edges VALUES (82, 417, '{"(39,special,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (82, 413, '{"(36,special,[/java.util/HashSet])"}', '{}');
INSERT INTO public.edges VALUES (138, 264, '{"(91,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (57, 64, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (326, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (370, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (85, 414, '{"(90,static,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (138, 227, '{"(99,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (138, 250, '{"(92,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (133, 121, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (138, 224, '{"(98,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (264, 362, '{"(404,special,[/com.esotericsoftware.asm/Item])","(404,special,[/com.esotericsoftware.asm/Item])","(404,special,[/com.esotericsoftware.asm/Item])","(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (116, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (116, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (116, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (131, 116, '{"(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])","(404,virtual,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (181, 164, '{"(152,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (346, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (181, 156, '{"(153,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (346, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (389, 422, '{"(404,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (264, 69, '{"(404,special,[/com.esotericsoftware.asm/ClassVisitor])"}', '{}');
INSERT INTO public.edges VALUES (181, 182, '{"(159,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(165,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(155,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(161,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(167,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(157,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(163,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(169,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (181, 175, '{"(171,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (145, 257, '{"(121,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (181, 183, '{"(154,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (122, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (181, 158, '{"(162,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(168,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(158,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(164,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(170,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(160,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(166,static,[/com.esotericsoftware.reflectasm/FieldAccess])","(156,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (122, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (78, 468, '{"(123,virtual,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (122, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (122, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (122, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (86, 428, '{"(73,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (86, 445, '{"(74,static,[/java.lang/Integer])","(74,static,[/java.lang/Integer])"}', '{}');
INSERT INTO public.edges VALUES (349, 327, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (86, 466, '{"(74,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])","(78,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (349, 346, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (86, 427, '{"(78,virtual,[/com.esotericsoftware.reflectasm/AccessClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (349, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (349, 345, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (134, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (349, 351, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (349, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (140, 257, '{"(131,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (115, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (215, 216, '{"(404,special,[/com.esotericsoftware.asm/TypePath])"}', '{}');
INSERT INTO public.edges VALUES (132, 130, '{"(404,special,[/com.esotericsoftware.asm/FieldVisitor])"}', '{}');
INSERT INTO public.edges VALUES (78, 406, '{"(143,special,[/java.lang.ref/WeakReference])"}', '{}');
INSERT INTO public.edges VALUES (115, 9, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (216, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (115, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (115, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (143, 467, '{"(25,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (144, 257, '{"(142,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (265, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (123, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (265, 358, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (123, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (138, 463, '{"(79,special,[/java.lang/StringBuilder])","(68,special,[/java.lang/StringBuilder])","(111,special,[/java.lang/StringBuilder])","(50,special,[/java.lang/StringBuilder])","(106,special,[/java.lang/StringBuilder])","(83,special,[/java.lang/StringBuilder])","(49,special,[/java.lang/StringBuilder])","(71,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (266, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (119, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (88, 414, '{"(100,static,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (266, 368, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (119, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (345, 325, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (135, 114, '{"(404,special,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (138, 435, '{"(46,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (138, 434, '{"(82,static,[/java.lang.reflect/Modifier])","(70,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (138, 423, '{"(80,special,[/java.lang/RuntimeException])","(68,special,[/java.lang/RuntimeException])","(106,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (135, 122, '{"(404,static,[/com.esotericsoftware.asm/AnnotationWriter])"}', '{}');
INSERT INTO public.edges VALUES (120, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (256, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (138, 424, '{"(114,special,[/java.lang/RuntimeException])","(84,special,[/java.lang/RuntimeException])","(71,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (256, 367, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (87, 421, '{"(49,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (120, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (351, 328, '{"(404,special,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (123, 265, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (181, 264, '{"(149,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (123, 266, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (123, 256, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (138, 433, '{"(87,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (123, 222, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (123, 260, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (222, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (77, 416, '{"(67,special,[/java.lang/ClassLoader])"}', '{}');
INSERT INTO public.edges VALUES (349, 54, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (349, 58, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (222, 366, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (324, 210, '{"(404,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (144, 463, '{"(152,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (110, 111, '{"(404,static,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (351, 322, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])","(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (117, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (264, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (349, 149, '{"(404,special,[/com.esotericsoftware.asm/Handle])"}', '{}');
INSERT INTO public.edges VALUES (117, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (123, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (150, 149, '{"(404,special,[/com.esotericsoftware.asm/Handle])"}', '{}');
INSERT INTO public.edges VALUES (181, 227, '{"(173,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (181, 250, '{"(150,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (181, 224, '{"(172,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (349, 431, '{"(404,static,[/java.lang/Float])"}', '{}');
INSERT INTO public.edges VALUES (349, 437, '{"(404,static,[/java.lang/Double])"}', '{}');
INSERT INTO public.edges VALUES (248, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (119, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (248, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (133, 198, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (263, 262, '{"(404,static,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (261, 323, '{"(404,virtual,[/com.esotericsoftware.asm/ClassReader])"}', '{}');
INSERT INTO public.edges VALUES (349, 446, '{"(404,special,[/java.lang/Integer])"}', '{}');
INSERT INTO public.edges VALUES (149, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (255, 360, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (123, 38, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (349, 429, '{"(404,special,[/java.lang/Float])"}', '{}');
INSERT INTO public.edges VALUES (120, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (349, 426, '{"(404,special,[/java.lang/Long])"}', '{}');
INSERT INTO public.edges VALUES (131, 202, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (349, 438, '{"(404,special,[/java.lang/Double])"}', '{}');
INSERT INTO public.edges VALUES (131, 196, '{"(404,virtual,[/com.esotericsoftware.asm/Attribute])"}', '{}');
INSERT INTO public.edges VALUES (164, 282, '{"(189,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (164, 306, '{"(194,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (220, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (164, 288, '{"(192,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (131, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (131, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (265, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (265, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (164, 298, '{"(193,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (163, 467, '{"(28,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (134, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (156, 282, '{"(278,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (156, 306, '{"(335,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (132, 260, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 299, '{"(296,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (261, 264, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 305, '{"(331,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(293,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (156, 288, '{"(327,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (164, 304, '{"(190,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (117, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (134, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (156, 301, '{"(330,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(292,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (266, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (181, 463, '{"(139,special,[/java.lang/StringBuilder])","(138,special,[/java.lang/StringBuilder])","(183,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (181, 464, '{"(114,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (156, 298, '{"(334,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (266, 11, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (182, 282, '{"(528,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 306, '{"(571,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (156, 285, '{"(287,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 299, '{"(554,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (243, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (182, 305, '{"(562,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(567,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(551,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 288, '{"(556,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (156, 286, '{"(295,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (156, 304, '{"(279,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(294,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (132, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (108, 105, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (182, 301, '{"(566,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(550,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(561,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (243, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (256, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (256, 3, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (181, 453, '{"(116,special,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (182, 298, '{"(570,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (181, 435, '{"(123,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (181, 434, '{"(124,static,[/java.lang.reflect/Modifier])"}', '{}');
INSERT INTO public.edges VALUES (181, 423, '{"(183,special,[/java.lang/RuntimeException])"}', '{}');
INSERT INTO public.edges VALUES (175, 282, '{"(341,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 306, '{"(384,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 285, '{"(545,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 299, '{"(367,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 305, '{"(364,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(375,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(380,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 288, '{"(369,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 286, '{"(553,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (182, 304, '{"(552,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(529,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (133, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (175, 301, '{"(379,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(363,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(374,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (265, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (222, 2, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (222, 11, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (175, 298, '{"(383,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (135, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (183, 282, '{"(200,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 306, '{"(272,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 299, '{"(262,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (265, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (175, 285, '{"(358,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (228, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (183, 305, '{"(216,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(268,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 288, '{"(264,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 286, '{"(366,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (175, 304, '{"(365,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(342,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (131, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (135, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (183, 301, '{"(267,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(215,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (228, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (266, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (183, 298, '{"(271,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 282, '{"(436,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 306, '{"(480,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 299, '{"(463,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 285, '{"(209,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (266, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (158, 305, '{"(471,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(459,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(476,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (230, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (158, 288, '{"(465,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 286, '{"(223,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(218,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(251,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(247,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(258,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(255,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(243,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(239,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(235,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(231,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(227,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (183, 304, '{"(217,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(219,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(201,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (99, 106, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (99, 98, '{"(404,static,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (134, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (158, 301, '{"(475,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(470,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(458,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (248, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (230, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (256, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (158, 298, '{"(479,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 285, '{"(453,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (101, 107, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (241, 359, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (256, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (184, 288, '{"(585,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(576,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(578,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 286, '{"(461,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 304, '{"(460,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(462,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(437,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (101, 103, '{"(404,static,[/com.esotericsoftware.asm/Frame])","(404,static,[/com.esotericsoftware.asm/Frame])","(404,static,[/com.esotericsoftware.asm/Frame])","(404,static,[/com.esotericsoftware.asm/Frame])","(404,static,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (241, 364, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (241, 361, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (184, 291, '{"(579,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (222, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (102, 113, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (164, 396, '{"(191,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (102, 112, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (102, 108, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (102, 100, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (222, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (232, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (102, 99, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (260, 265, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (178, 288, '{"(600,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(591,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(593,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (260, 266, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (184, 286, '{"(577,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(575,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (260, 256, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (184, 304, '{"(581,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (260, 222, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (102, 106, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (260, 248, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (102, 105, '{"(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])","(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (232, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (178, 291, '{"(594,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (102, 97, '{"(404,special,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (156, 396, '{"(308,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(320,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(311,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(323,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(302,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(314,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(305,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(317,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (146, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (260, 232, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (260, 223, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (223, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (260, 239, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (178, 286, '{"(592,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(590,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (178, 304, '{"(596,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (104, 98, '{"(404,static,[/com.esotericsoftware.asm/Frame])"}', '{}');
INSERT INTO public.edges VALUES (135, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (223, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (248, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (248, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (248, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 184, '{"(333,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (220, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (252, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (260, 38, '{"(404,virtual,[/com.esotericsoftware.asm/Type])","(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (252, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (183, 396, '{"(228,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(224,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(252,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(248,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(244,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(240,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(236,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(232,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (260, 35, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (220, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (220, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (182, 184, '{"(569,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (182, 178, '{"(563,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (220, 240, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (236, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (253, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (175, 184, '{"(382,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (175, 178, '{"(376,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (253, 238, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (243, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (239, 365, '{"(404,virtual,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (243, 252, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (184, 396, '{"(580,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(583,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(584,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(582,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (260, 464, '{"(404,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (243, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (238, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');
INSERT INTO public.edges VALUES (183, 184, '{"(270,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (241, 1, '{"(404,special,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (170, 463, '{"(36,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (170, 464, '{"(36,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (260, 409, '{"(404,special,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (243, 229, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 12, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])","(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (178, 396, '{"(598,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(599,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(597,virtual,[/com.esotericsoftware.asm/MethodVisitor])","(595,virtual,[/com.esotericsoftware.asm/MethodVisitor])"}', '{}');
INSERT INTO public.edges VALUES (158, 184, '{"(478,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (158, 178, '{"(472,static,[/com.esotericsoftware.reflectasm/FieldAccess])"}', '{}');
INSERT INTO public.edges VALUES (228, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (109, 467, '{"(404,special,[/java.lang/Object])"}', '{}');
INSERT INTO public.edges VALUES (177, 463, '{"(42,special,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (177, 464, '{"(42,special,[/java.lang/IllegalArgumentException])"}', '{}');
INSERT INTO public.edges VALUES (228, 252, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (228, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (228, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (223, 5, '{"(404,virtual,[/com.esotericsoftware.asm/ByteVector])"}', '{}');
INSERT INTO public.edges VALUES (230, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (230, 252, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (107, 236, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])","(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (228, 251, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (164, 257, '{"(188,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (230, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (108, 44, '{"(404,static,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (230, 235, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 210, '{"(286,special,[/com.esotericsoftware.asm/Label])","(285,special,[/com.esotericsoftware.asm/Label])"}', '{}');
INSERT INTO public.edges VALUES (241, 237, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 255, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 252, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (230, 251, '{"(404,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (241, 231, '{"(404,special,[/com.esotericsoftware.asm/ClassWriter])","(404,special,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (156, 257, '{"(277,virtual,[/com.esotericsoftware.asm/ClassWriter])"}', '{}');
INSERT INTO public.edges VALUES (182, 458, '{"(527,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (158, 458, '{"(434,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (102, 408, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (178, 458, '{"(594,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (240, 432, '{"(404,virtual,[/java.lang/Exception])"}', '{}');
INSERT INTO public.edges VALUES (276, 307, '{"(404,special,[/com.esotericsoftware.asm/MethodWriter])"}', '{}');
INSERT INTO public.edges VALUES (15, 459, '{"(222,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (14, 458, '{"(51,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (15, 458, '{"(286,virtual,[/java.lang/StringBuilder])","(210,virtual,[/java.lang/StringBuilder])","(111,virtual,[/java.lang/StringBuilder])","(110,virtual,[/java.lang/StringBuilder])","(214,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (15, 450, '{"(98,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (15, 452, '{"(218,virtual,[/java.util/ArrayList])","(103,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (15, 451, '{"(139,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (15, 460, '{"(160,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (15, 462, '{"(213,virtual,[/java.lang/StringBuilder])","(161,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (300, 403, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (22, 458, '{"(65,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (21, 458, '{"(58,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (272, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (272, 408, '{"(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (272, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (206, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (354, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (37, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (37, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (37, 402, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (50, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (50, 408, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (38, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (30, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (353, 403, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (348, 403, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (55, 408, '{"(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (55, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (347, 403, '{"(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (48, 408, '{"(404,virtual,[/java.lang/StringBuffer])","(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (48, 410, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (283, 403, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (329, 403, '{"(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (331, 403, '{"(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (337, 403, '{"(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])","(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (83, 448, '{"(108,virtual,[/java.lang/Class])"}', '{}');
INSERT INTO public.edges VALUES (138, 458, '{"(71,virtual,[/java.lang/StringBuilder])","(111,virtual,[/java.lang/StringBuilder])","(79,virtual,[/java.lang/StringBuilder])","(68,virtual,[/java.lang/StringBuilder])","(50,virtual,[/java.lang/StringBuilder])","(49,virtual,[/java.lang/StringBuilder])","(106,virtual,[/java.lang/StringBuilder])","(83,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (144, 458, '{"(152,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (77, 447, '{"(65,virtual,[/java.lang/Class])","(64,virtual,[/java.lang/Class])","(63,virtual,[/java.lang/Class])","(62,virtual,[/java.lang/Class])"}', '{}');
INSERT INTO public.edges VALUES (181, 458, '{"(139,virtual,[/java.lang/StringBuilder])","(138,virtual,[/java.lang/StringBuilder])","(183,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (181, 450, '{"(180,virtual,[/java.util/ArrayList])","(131,virtual,[/java.util/ArrayList])","(130,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (181, 452, '{"(133,virtual,[/java.util/ArrayList])","(134,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (123, 404, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (181, 454, '{"(125,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (181, 455, '{"(180,virtual,[/java.util/ArrayList])"}', '{}');
INSERT INTO public.edges VALUES (263, 404, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (146, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (170, 458, '{"(36,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (260, 412, '{"(404,virtual,[/java.lang/StringBuffer])"}', '{}');
INSERT INTO public.edges VALUES (177, 458, '{"(42,virtual,[/java.lang/StringBuilder])"}', '{}');
INSERT INTO public.edges VALUES (110, 404, '{"(404,virtual,[/java.lang/String])"}', '{}');
INSERT INTO public.edges VALUES (260, 63, '{"(404,virtual,[/com.esotericsoftware.asm/Type])"}', '{}');
INSERT INTO public.edges VALUES (239, 363, '{"(404,special,[/com.esotericsoftware.asm/Item])"}', '{}');


--
-- Data for Name: files; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.files VALUES (6, 1, 'com/esotericsoftware/reflectasm/AccessClassLoader.java', NULL, NULL, NULL);
-- The following file has a JSONB in its `metadata` field for testing purposes
INSERT INTO public.files VALUES (16, 1, 'com/esotericsoftware/reflectasm/FieldAccess.java', NULL, NULL, cast('{"not": "empty"}' as jsonb));
INSERT INTO public.files VALUES (1, 1, 'NotFound', NULL, NULL, NULL);
INSERT INTO public.files VALUES (14, 1, 'com/esotericsoftware/reflectasm/ConstructorAccess.java', NULL, NULL, NULL);
INSERT INTO public.files VALUES (23, 1, 'com/esotericsoftware/reflectasm/PublicConstructorAccess.java', NULL, NULL, NULL);
INSERT INTO public.files VALUES (2, 1, 'com/esotericsoftware/reflectasm/MethodAccess.java', NULL, NULL, NULL);


--
-- Data for Name: ingested_artifacts; Type: TABLE DATA; Schema: public; Owner: fasten
--



--
-- Data for Name: module_contents; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.module_contents VALUES (1, 1);
INSERT INTO public.module_contents VALUES (2, 2);
INSERT INTO public.module_contents VALUES (3, 1);
INSERT INTO public.module_contents VALUES (4, 1);
INSERT INTO public.module_contents VALUES (5, 1);
INSERT INTO public.module_contents VALUES (6, 6);
INSERT INTO public.module_contents VALUES (7, 1);
INSERT INTO public.module_contents VALUES (8, 1);
INSERT INTO public.module_contents VALUES (9, 1);
INSERT INTO public.module_contents VALUES (10, 1);
INSERT INTO public.module_contents VALUES (11, 1);
INSERT INTO public.module_contents VALUES (12, 1);
INSERT INTO public.module_contents VALUES (13, 1);
INSERT INTO public.module_contents VALUES (14, 14);
INSERT INTO public.module_contents VALUES (15, 1);
INSERT INTO public.module_contents VALUES (16, 16);
INSERT INTO public.module_contents VALUES (17, 1);
INSERT INTO public.module_contents VALUES (18, 1);
INSERT INTO public.module_contents VALUES (19, 1);
INSERT INTO public.module_contents VALUES (20, 1);
INSERT INTO public.module_contents VALUES (21, 1);
INSERT INTO public.module_contents VALUES (22, 1);
INSERT INTO public.module_contents VALUES (23, 23);
INSERT INTO public.module_contents VALUES (24, 1);
INSERT INTO public.module_contents VALUES (25, 1);
INSERT INTO public.module_contents VALUES (26, 1);


--
-- Data for Name: modules; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.modules VALUES (-1, -1, 'global_external_callables', NULL, NULL);
INSERT INTO public.modules VALUES (1, 1, '/com.esotericsoftware.asm/ByteVector', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (2, 1, '/com.esotericsoftware.reflectasm/MethodAccess', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (3, 1, '/com.esotericsoftware.asm/Handler', NULL, '{"final": false, "access": "packagePrivate", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (4, 1, '/com.esotericsoftware.asm/Type', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (5, 1, '/com.esotericsoftware.asm/ClassVisitor', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (6, 1, '/com.esotericsoftware.reflectasm/AccessClassLoader', NULL, '{"final": false, "access": "packagePrivate", "superClasses": ["/java.lang/ClassLoader"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (7, 1, '/com.esotericsoftware.asm/AnnotationVisitor', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (8, 1, '/com.esotericsoftware.asm/Context', NULL, '{"final": false, "access": "packagePrivate", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (9, 1, '/com.esotericsoftware.asm/Frame', NULL, '{"final": true, "access": "packagePrivate", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (10, 1, '/com.esotericsoftware.asm/AnnotationWriter', NULL, '{"final": true, "access": "packagePrivate", "superClasses": ["/com.esotericsoftware.asm/AnnotationVisitor", "/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (11, 1, '/com.esotericsoftware.asm/Edge', NULL, '{"final": false, "access": "packagePrivate", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (12, 1, '/com.esotericsoftware.asm/FieldVisitor', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (13, 1, '/com.esotericsoftware.asm/FieldWriter', NULL, '{"final": true, "access": "packagePrivate", "superClasses": ["/com.esotericsoftware.asm/FieldVisitor", "/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (14, 1, '/com.esotericsoftware.reflectasm/ConstructorAccess', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (15, 1, '/com.esotericsoftware.asm/Handle', NULL, '{"final": true, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (16, 1, '/com.esotericsoftware.reflectasm/FieldAccess', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (17, 1, '/com.esotericsoftware.asm/Attribute', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (18, 1, '/com.esotericsoftware.asm/Label', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (19, 1, '/com.esotericsoftware.asm/TypePath', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (20, 1, '/com.esotericsoftware.asm/ClassWriter', NULL, '{"final": false, "access": "public", "superClasses": ["/com.esotericsoftware.asm/ClassVisitor", "/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (21, 1, '/com.esotericsoftware.asm/Opcodes', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (22, 1, '/com.esotericsoftware.asm/MethodWriter', NULL, '{"final": false, "access": "packagePrivate", "superClasses": ["/com.esotericsoftware.asm/MethodVisitor", "/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (23, 1, '/com.esotericsoftware.reflectasm/PublicConstructorAccess', NULL, '{"final": false, "access": "public", "superClasses": ["/com.esotericsoftware.reflectasm/ConstructorAccess", "/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (24, 1, '/com.esotericsoftware.asm/ClassReader', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (25, 1, '/com.esotericsoftware.asm/Item', NULL, '{"final": true, "access": "packagePrivate", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');
INSERT INTO public.modules VALUES (26, 1, '/com.esotericsoftware.asm/MethodVisitor', NULL, '{"final": false, "access": "public", "superClasses": ["/java.lang/Object"], "superInterfaces": []}');


--
-- Data for Name: package_versions; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.package_versions VALUES (-1, -1, '0.0.1', 'OPAL', -1, NULL, NULL, NULL);
INSERT INTO public.package_versions VALUES (1, 1, '1.11.8', 'OPAL', -1, NULL, '2018-12-28 22:41:19', '{"commitTag": "", "sourcesUrl": "https://repo.maven.apache.org/maven2/com/esotericsoftware/reflectasm/1.11.8/reflectasm-1.11.8-sources.jar", "packagingType": "bundle", "parentCoordinate": "org.sonatype.oss:oss-parent:7", "dependencyManagement": {"dependencies": []}}');


--
-- Data for Name: packages; Type: TABLE DATA; Schema: public; Owner: fasten
--

INSERT INTO public.packages VALUES (-1, 'external_callables_library', 'mvn', NULL, NULL, NULL);
INSERT INTO public.packages VALUES (2, 'junit:junit', 'mvn', NULL, NULL, NULL);
INSERT INTO public.packages VALUES (1, 'com.esotericsoftware:reflectasm', 'mvn', 'ReflectASM', 'https://github.com/EsotericSoftware/reflectasm', NULL);


--
-- Data for Name: virtual_implementations; Type: TABLE DATA; Schema: public; Owner: fasten
--



--
-- Name: artifact_repositories_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.artifact_repositories_id_seq', 1, false);


--
-- Name: binary_modules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.binary_modules_id_seq', 1, false);


--
-- Name: callables_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.callables_id_seq', 1271, true);


--
-- Name: files_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.files_id_seq', 78, true);


--
-- Name: ingested_artifacts_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.ingested_artifacts_id_seq', 1, false);


--
-- Name: modules_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.modules_id_seq', 78, true);


--
-- Name: package_versions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.package_versions_id_seq', 6, true);


--
-- Name: packages_id_seq; Type: SEQUENCE SET; Schema: public; Owner: fasten
--

SELECT pg_catalog.setval('public.packages_id_seq', 9, true);


--
-- Name: artifact_repositories artifact_repositories_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.artifact_repositories
    ADD CONSTRAINT artifact_repositories_pkey PRIMARY KEY (id);


--
-- Name: binary_modules binary_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_modules
    ADD CONSTRAINT binary_modules_pkey PRIMARY KEY (id);


--
-- Name: callables callables_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.callables
    ADD CONSTRAINT callables_pkey PRIMARY KEY (id);


--
-- Name: files files_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.files
    ADD CONSTRAINT files_pkey PRIMARY KEY (id);


--
-- Name: ingested_artifacts ingested_artifacts_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.ingested_artifacts
    ADD CONSTRAINT ingested_artifacts_pkey PRIMARY KEY (id);


--
-- Name: modules modules_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_pkey PRIMARY KEY (id);


--
-- Name: package_versions package_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.package_versions
    ADD CONSTRAINT package_versions_pkey PRIMARY KEY (id);


--
-- Name: packages packages_pkey; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.packages
    ADD CONSTRAINT packages_pkey PRIMARY KEY (id);


--
-- Name: artifact_repositories unique_artifact_repositories; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.artifact_repositories
    ADD CONSTRAINT unique_artifact_repositories UNIQUE (repository_base_url);


--
-- Name: binary_module_contents unique_binary_module_file; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_module_contents
    ADD CONSTRAINT unique_binary_module_file UNIQUE (binary_module_id, file_id);


--
-- Name: ingested_artifacts unique_ingested_artifacts; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.ingested_artifacts
    ADD CONSTRAINT unique_ingested_artifacts UNIQUE (package_name, version);


--
-- Name: module_contents unique_module_file; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.module_contents
    ADD CONSTRAINT unique_module_file UNIQUE (module_id, file_id);


--
-- Name: packages unique_package_forge; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.packages
    ADD CONSTRAINT unique_package_forge UNIQUE (package_name, forge);


--
-- Name: package_versions unique_package_version_generator; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.package_versions
    ADD CONSTRAINT unique_package_version_generator UNIQUE (package_id, version, cg_generator);


--
-- Name: edges unique_source_target; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT unique_source_target UNIQUE (source_id, target_id);


--
-- Name: callables unique_uri_call; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.callables
    ADD CONSTRAINT unique_uri_call UNIQUE (module_id, fasten_uri, is_internal_call);


--
-- Name: dependencies unique_version_dependency_range; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.dependencies
    ADD CONSTRAINT unique_version_dependency_range UNIQUE (package_version_id, dependency_id, version_range);


--
-- Name: binary_modules unique_version_name; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_modules
    ADD CONSTRAINT unique_version_name UNIQUE (package_version_id, name);


--
-- Name: modules unique_version_namespace; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT unique_version_namespace UNIQUE (package_version_id, namespace);


--
-- Name: files unique_version_path; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.files
    ADD CONSTRAINT unique_version_path UNIQUE (package_version_id, path);


--
-- Name: virtual_implementations unique_virtual_implementation; Type: CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.virtual_implementations
    ADD CONSTRAINT unique_virtual_implementation UNIQUE (virtual_package_version_id, package_version_id);


--
-- Name: callables_module_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX callables_module_id ON public.callables USING btree (module_id);


--
-- Name: files_package_version_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX files_package_version_id ON public.files USING btree (package_version_id);


--
-- Name: module_contents_file_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX module_contents_file_id ON public.module_contents USING btree (file_id);


--
-- Name: module_contents_module_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX module_contents_module_id ON public.module_contents USING btree (module_id);


--
-- Name: modules_package_version_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX modules_package_version_id ON public.modules USING btree (package_version_id);


--
-- Name: package_versions_package_id; Type: INDEX; Schema: public; Owner: fasten
--

CREATE INDEX package_versions_package_id ON public.package_versions USING btree (package_id);


--
-- Name: binary_module_contents binary_module_contents_binary_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_module_contents
    ADD CONSTRAINT binary_module_contents_binary_module_id_fkey FOREIGN KEY (binary_module_id) REFERENCES public.binary_modules(id);


--
-- Name: binary_module_contents binary_module_contents_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_module_contents
    ADD CONSTRAINT binary_module_contents_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.files(id);


--
-- Name: binary_modules binary_modules_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.binary_modules
    ADD CONSTRAINT binary_modules_package_version_id_fkey FOREIGN KEY (package_version_id) REFERENCES public.package_versions(id);


--
-- Name: callables callables_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.callables
    ADD CONSTRAINT callables_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(id);


--
-- Name: dependencies dependencies_dependency_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.dependencies
    ADD CONSTRAINT dependencies_dependency_id_fkey FOREIGN KEY (dependency_id) REFERENCES public.packages(id);


--
-- Name: dependencies dependencies_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.dependencies
    ADD CONSTRAINT dependencies_package_version_id_fkey FOREIGN KEY (package_version_id) REFERENCES public.package_versions(id);


--
-- Name: edges edges_source_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT edges_source_id_fkey FOREIGN KEY (source_id) REFERENCES public.callables(id);


--
-- Name: edges edges_target_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.edges
    ADD CONSTRAINT edges_target_id_fkey FOREIGN KEY (target_id) REFERENCES public.callables(id);


--
-- Name: files files_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.files
    ADD CONSTRAINT files_package_version_id_fkey FOREIGN KEY (package_version_id) REFERENCES public.package_versions(id);


--
-- Name: module_contents module_contents_file_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.module_contents
    ADD CONSTRAINT module_contents_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.files(id);


--
-- Name: module_contents module_contents_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.module_contents
    ADD CONSTRAINT module_contents_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(id);


--
-- Name: modules modules_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_package_version_id_fkey FOREIGN KEY (package_version_id) REFERENCES public.package_versions(id);


--
-- Name: package_versions package_versions_artifact_repository_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.package_versions
    ADD CONSTRAINT package_versions_artifact_repository_id_fkey FOREIGN KEY (artifact_repository_id) REFERENCES public.artifact_repositories(id);


--
-- Name: package_versions package_versions_package_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.package_versions
    ADD CONSTRAINT package_versions_package_id_fkey FOREIGN KEY (package_id) REFERENCES public.packages(id);


--
-- Name: virtual_implementations virtual_implementations_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.virtual_implementations
    ADD CONSTRAINT virtual_implementations_package_version_id_fkey FOREIGN KEY (package_version_id) REFERENCES public.package_versions(id);


--
-- Name: virtual_implementations virtual_implementations_virtual_package_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: fasten
--

ALTER TABLE ONLY public.virtual_implementations
    ADD CONSTRAINT virtual_implementations_virtual_package_version_id_fkey FOREIGN KEY (virtual_package_version_id) REFERENCES public.package_versions(id);


--
-- PostgreSQL database dump complete
--

