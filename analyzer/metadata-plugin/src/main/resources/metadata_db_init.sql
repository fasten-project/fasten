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
    id         BIGSERIAL PRIMARY KEY,
    module_id  BIGINT NOT NULL REFERENCES modules (id),
    path       TEXT   NOT NULL,
    checksum   BYTEA,
    created_at TIMESTAMP,
    metadata   JSONB
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

CREATE INDEX packages_compound_index ON packages (package_name, forge);
CREATE INDEX package_versions_compound_index ON package_versions (package_id, version, cg_generator);
CREATE INDEX dependencies_compound_index ON dependencies (package_version_id, dependency_id, version_range);
CREATE INDEX modules_compound_index ON modules (package_version_id, namespace);
CREATE INDEX files_compound_index ON files (module_id, path);
CREATE INDEX callables_compound_index ON callables (fasten_uri, is_internal_call);

CREATE UNIQUE INDEX CONCURRENTLY unique_source_target ON edges USING btree (source_id, target_id);
ALTER TABLE edges
    ADD CONSTRAINT unique_source_target UNIQUE USING INDEX unique_source_target;
