CREATE TABLE ingested_artifacts
(
    id           BIGSERIAL PRIMARY KEY,
    package_name TEXT NOT NULL,
    version      TEXT NOT NULL,
    timestamp    TIMESTAMP
);

CREATE TABLE packages
(
    id           BIGSERIAL PRIMARY KEY,
    package_name TEXT NOT NULL,
    forge        TEXT NOT NULL,
    project_name TEXT,
    repository   TEXT,
    created_at   TIMESTAMP
);

CREATE TABLE artifact_repositories
(
    id                  BIGSERIAL PRIMARY KEY,
    repository_base_url TEXT NOT NULL
);

CREATE TABLE package_versions
(
    id                     BIGSERIAL PRIMARY KEY,
    package_id             BIGINT NOT NULL REFERENCES packages (id),
    version                TEXT   NOT NULL,
    cg_generator           TEXT   NOT NULL,
    artifact_repository_id BIGINT references artifact_repositories (id),
    architecture           TEXT,
    created_at             TIMESTAMP,
    metadata               JSONB
);

CREATE TABLE virtual_implementations
(
    virtual_package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    package_version_id         BIGINT NOT NULL REFERENCES package_versions (id)
);

CREATE TABLE dependencies
(
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    dependency_id      BIGINT NOT NULL REFERENCES packages (id),
    version_range      TEXT[] NOT NULL,
    architecture       TEXT[],
    dependency_type    TEXT[],
    alternative_group  BIGINT,
    metadata           JSONB
);

CREATE TABLE module_names
(
    id   BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TYPE ACCESS AS ENUM ('private', 'public', 'packagePrivate', 'static', 'protected');

CREATE TABLE modules
(
    id                 BIGSERIAL PRIMARY KEY,
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    module_name_id     BIGINT NOT NULL REFERENCES module_names (id),
    final              BOOLEAN,
    access             ACCESS,
    super_classes      BIGINT[],
    super_interfaces   BIGINT[],
    metadata           JSONB,
    annotations        JSONB
);

CREATE TABLE files
(
    id                 BIGSERIAL PRIMARY KEY,
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    path               TEXT   NOT NULL,
    checksum           BYTEA,
    created_at         TIMESTAMP,
    metadata           JSONB
);

CREATE TABLE module_contents
(
    module_id BIGINT NOT NULL REFERENCES modules (id),
    file_id   BIGINT NOT NULL REFERENCES files (id)
);

CREATE TABLE binary_modules
(
    id                 BIGSERIAL PRIMARY KEY,
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    name               TEXT   NOT NULL,
    created_at         TIMESTAMP,
    metadata           JSONB
);

CREATE TABLE binary_module_contents
(
    binary_module_id BIGINT NOT NULL REFERENCES binary_modules (id),
    file_id          BIGINT NOT NULL REFERENCES files (id)
);

CREATE TYPE CALLABLE_TYPE AS ENUM ('internalBinary', 'externalProduct', 'externalStaticFunction', 'externalUndefined', 'internalStaticFunction');

CREATE TABLE callables
(
    id               BIGSERIAL PRIMARY KEY,
    module_id        BIGINT  NOT NULL REFERENCES modules (id),
    fasten_uri       TEXT    NOT NULL,
    is_internal_call BOOLEAN NOT NULL,
    line_start       INTEGER,
    line_end         INTEGER,
    type             CALLABLE_TYPE,
    defined          BOOLEAN,
    access           ACCESS,
    metadata         JSONB
);

CREATE TYPE CALL_TYPE AS ENUM ('static', 'dynamic', 'virtual', 'interface', 'special');

CREATE TABLE call_sites
(
    source_id         BIGINT NOT NULL REFERENCES callables (id),
    target_id         BIGINT NOT NULL REFERENCES callables (id),
    line              INTEGER,
    call_type         CALL_TYPE,
    receiver_type_ids BIGINT[],
    metadata          JSONB
);

-- CREATE INDEX CONCURRENTLY package_versions_package_id ON package_versions USING btree (package_id);
-- CREATE INDEX CONCURRENTLY dependencies_package_version_id ON dependencies USING btree (package_version_id);
-- CREATE INDEX CONCURRENTLY dependencies_dependency_id ON dependencies USING btree (dependency_id);
-- CREATE INDEX CONCURRENTLY files_package_version_id ON files USING btree (package_version_id);
-- CREATE INDEX CONCURRENTLY modules_package_version_id ON modules USING btree (package_version_id);
-- CREATE INDEX CONCURRENTLY module_contents_module_id ON module_contents USING btree (module_id);
-- CREATE INDEX CONCURRENTLY module_contents_file_id ON module_contents USING btree (file_id);
-- CREATE INDEX CONCURRENTLY binary_modules_package_version_id ON binary_modules USING btree (package_version_id);
-- CREATE INDEX CONCURRENTLY binary_module_contents_binary_module_id ON binary_module_contents USING btree (binary_module_id);
-- CREATE INDEX CONCURRENTLY binary_module_contents_file_id ON binary_module_contents USING btree (file_id);
-- CREATE INDEX CONCURRENTLY callables_module_id ON callables USING btree (module_id);
-- CREATE INDEX CONCURRENTLY edges_source_id ON edges USING btree (source_id);
-- CREATE INDEX CONCURRENTLY edges_target_id ON edges USING btree (target_id);

CREATE UNIQUE INDEX CONCURRENTLY unique_ingested_artifacts ON ingested_artifacts USING btree (package_name, version);
ALTER TABLE ingested_artifacts
    ADD CONSTRAINT unique_ingested_artifacts UNIQUE USING INDEX unique_ingested_artifacts;

CREATE UNIQUE INDEX CONCURRENTLY unique_package_forge ON packages USING btree (package_name, forge);
ALTER TABLE packages
    ADD CONSTRAINT unique_package_forge UNIQUE USING INDEX unique_package_forge;

CREATE UNIQUE INDEX CONCURRENTLY unique_artifact_repositories ON artifact_repositories USING btree (repository_base_url);
ALTER TABLE artifact_repositories
    ADD CONSTRAINT unique_artifact_repositories UNIQUE USING INDEX unique_artifact_repositories;

CREATE UNIQUE INDEX CONCURRENTLY unique_package_version_generator ON package_versions USING btree (package_id, version, cg_generator);
ALTER TABLE package_versions
    ADD CONSTRAINT unique_package_version_generator UNIQUE USING INDEX unique_package_version_generator;

CREATE UNIQUE INDEX CONCURRENTLY unique_virtual_implementation ON virtual_implementations USING btree (virtual_package_version_id, package_version_id);
ALTER TABLE virtual_implementations
    ADD CONSTRAINT unique_virtual_implementation UNIQUE USING INDEX unique_virtual_implementation;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_dependency_range ON dependencies USING btree (package_version_id, dependency_id, version_range);
ALTER TABLE dependencies
    ADD CONSTRAINT unique_version_dependency_range UNIQUE USING INDEX unique_version_dependency_range;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_namespace ON modules USING btree (package_version_id, module_name_id);
ALTER TABLE modules
    ADD CONSTRAINT unique_version_namespace UNIQUE USING INDEX unique_version_namespace;

CREATE UNIQUE INDEX CONCURRENTLY unique_module_file ON module_contents USING btree (module_id, file_id);
ALTER TABLE module_contents
    ADD CONSTRAINT unique_module_file UNIQUE USING INDEX unique_module_file;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_name ON binary_modules USING btree (package_version_id, name);
ALTER TABLE binary_modules
    ADD CONSTRAINT unique_version_name UNIQUE USING INDEX unique_version_name;

CREATE UNIQUE INDEX CONCURRENTLY unique_binary_module_file ON binary_module_contents USING btree (binary_module_id, file_id);
ALTER TABLE binary_module_contents
    ADD CONSTRAINT unique_binary_module_file UNIQUE USING INDEX unique_binary_module_file;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_path ON files USING btree (package_version_id, path);
ALTER TABLE files
    ADD CONSTRAINT unique_version_path UNIQUE USING INDEX unique_version_path;

CREATE UNIQUE INDEX CONCURRENTLY unique_uri_call ON callables USING btree (module_id, fasten_uri, is_internal_call);
ALTER TABLE callables
    ADD CONSTRAINT unique_uri_call UNIQUE USING INDEX unique_uri_call;

CREATE UNIQUE INDEX CONCURRENTLY unique_source_target ON call_sites USING btree (source_id, target_id);
ALTER TABLE call_sites
    ADD CONSTRAINT unique_source_target UNIQUE USING INDEX unique_source_target;

ALTER TABLE callables
    ADD CONSTRAINT check_module_id CHECK ((module_id = -1 AND is_internal_call IS false) OR
                                          (module_id IS NOT NULL AND is_internal_call IS true));

CREATE UNIQUE INDEX CONCURRENTLY unique_module_names ON module_names USING btree (name);
ALTER TABLE module_names
    ADD CONSTRAINT unique_module_names UNIQUE USING INDEX unique_module_names;

INSERT INTO packages (id, package_name, forge)
VALUES (-1, 'external_callables_library', 'pypi')
ON CONFLICT DO NOTHING;

INSERT INTO artifact_repositories (id, repository_base_url)
VALUES (-1, 'https://pypi.org/')
ON CONFLICT DO NOTHING;

INSERT INTO package_versions (id, package_id, version, cg_generator, artifact_repository_id)
VALUES (-1, -1, '0.0.1', 'PyCG', -1)
ON CONFLICT DO NOTHING;

INSERT INTO module_names (id, name)
VALUES (-1, 'global_external_callables')
ON CONFLICT DO NOTHING;

INSERT INTO modules (id, package_version_id, module_name_id)
VALUES (-1, -1, -1)
ON CONFLICT DO NOTHING;

CREATE INDEX CONCURRENTLY idx_callables_fasten_uri ON callables USING btree (digest(fasten_uri, 'sha1'::text));