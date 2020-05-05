CREATE TABLE packages
(
    id           BIGSERIAL PRIMARY KEY,
    package_name TEXT NOT NULL,
    forge        TEXT NOT NULL,
    project_name TEXT,
    repository   TEXT,
    created_at   TIMESTAMP
);

CREATE TABLE package_versions
(
    id           BIGSERIAL PRIMARY KEY,
    package_id   BIGINT NOT NULL REFERENCES packages (id),
    version      TEXT   NOT NULL,
    cg_generator TEXT   NOT NULL,
    created_at   TIMESTAMP,
    metadata     JSONB
);

CREATE TABLE dependencies
(
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    dependency_id      BIGINT NOT NULL REFERENCES packages (id),
    version_range      TEXT[] NOT NULL
);

CREATE TABLE modules
(
    id                 BIGSERIAL PRIMARY KEY,
    package_version_id BIGINT NOT NULL REFERENCES package_versions (id),
    namespace          TEXT   NOT NULL,
    created_at         TIMESTAMP,
    metadata           JSONB
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

CREATE TABLE callables
(
    id               BIGSERIAL PRIMARY KEY,
    module_id        BIGINT REFERENCES modules (id),
    fasten_uri       TEXT    NOT NULL,
    is_internal_call BOOLEAN NOT NULL,
    created_at       TIMESTAMP,
    metadata         JSONB
);

CREATE TABLE edges
(
    source_id BIGINT NOT NULL REFERENCES callables (id),
    target_id BIGINT NOT NULL REFERENCES callables (id),
    metadata  JSONB  NOT NULL
);

CREATE UNIQUE INDEX CONCURRENTLY unique_package_forge ON packages USING btree (package_name, forge);
ALTER TABLE packages
    ADD CONSTRAINT unique_package_forge UNIQUE USING INDEX unique_package_forge;

CREATE UNIQUE INDEX CONCURRENTLY unique_package_version_generator ON package_versions USING btree (package_id, version, cg_generator);
ALTER TABLE package_versions
    ADD CONSTRAINT unique_package_version_generator UNIQUE USING INDEX unique_package_version_generator;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_dependency_range ON dependencies USING btree (package_version_id, dependency_id, version_range);
ALTER TABLE dependencies
    ADD CONSTRAINT unique_version_dependency_range UNIQUE USING INDEX unique_version_dependency_range;

CREATE UNIQUE INDEX CONCURRENTLY unique_version_namespace ON modules USING btree (package_version_id, namespace);
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

CREATE UNIQUE INDEX CONCURRENTLY unique_source_target ON edges USING btree (source_id, target_id);
ALTER TABLE edges
    ADD CONSTRAINT unique_source_target UNIQUE USING INDEX unique_source_target;

ALTER TABLE callables
    ADD CONSTRAINT check_module_id CHECK ((module_id = -1 AND is_internal_call IS false) OR
                                          (module_id IS NOT NULL AND is_internal_call IS true));

INSERT INTO packages (id, package_name, forge)
VALUES (-1, 'external_callables_library', 'mvn')
ON CONFLICT DO NOTHING;

INSERT INTO package_versions (id, package_id, version, cg_generator)
VALUES (-1, -1, '0.0.1', 'OPAL')
ON CONFLICT DO NOTHING;

INSERT INTO modules (id, package_version_id, namespace)
VALUES (-1, -1, 'global_external_callables')
ON CONFLICT DO NOTHING;