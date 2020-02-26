CREATE TABLE packages(
	id				BIGSERIAL PRIMARY KEY,
  	package_name	TEXT NOT NULL,
	project_name	TEXT,
	repository		TEXT,
  	created_at    	TIMESTAMP
);

CREATE TABLE package_versions(
  id			BIGSERIAL PRIMARY KEY,
  package_id	BIGINT NOT NULL REFERENCES packages(id),
  version		TEXT NOT NULL,
  cg_generator	TEXT NOT NULL,
  created_at	TIMESTAMP,
  metadata 		JSONB
);

CREATE TABLE dependencies(
  package_id    BIGINT NOT NULL REFERENCES package_versions(id),
  dependency_id BIGINT NOT NULL REFERENCES package_versions(id),
  version_range TEXT NOT NULL
);

CREATE TABLE files(
	id			BIGSERIAL PRIMARY KEY,
	package_id	BIGINT NOT NULL REFERENCES package_versions(id),
	namespaces  TEXT NOT NULL,
  	sha256      BYTEA,
  	created_at  TIMESTAMP,
  	metadata    JSONB
);

CREATE TABLE callables(
	id		  	BIGSERIAL PRIMARY KEY,
	file_id		BIGINT NOT NULL REFERENCES files(id),
	fasten_uri	TEXT NOT NULL,
  	created_at  TIMESTAMP,
	metadata	JSONB
);


CREATE TABLE edges(
	source_id	BIGINT NOT NULL REFERENCES callables(id),
	target_id	BIGINT NOT NULL REFERENCES callables(id),
	metadata	JSONB
);