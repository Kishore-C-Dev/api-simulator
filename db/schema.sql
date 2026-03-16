CREATE TABLE IF NOT EXISTS mappings (
    id             VARCHAR(36)   PRIMARY KEY,
    name           VARCHAR(255),
    namespace      VARCHAR(255),
    priority       INTEGER,
    endpoint_type  VARCHAR(20),
    enabled        BOOLEAN       DEFAULT TRUE,
    tags           JSONB,
    created_at     TIMESTAMPTZ,
    updated_at     TIMESTAMPTZ,
    deleted        BOOLEAN       DEFAULT FALSE,
    request        JSONB,
    response       JSONB,
    delays         JSONB,
    graphql_spec   JSONB,
    soap_spec      JSONB
);
CREATE INDEX IF NOT EXISTS idx_mappings_namespace   ON mappings(namespace);
CREATE INDEX IF NOT EXISTS idx_mappings_ns_en_pri   ON mappings(namespace, enabled, priority DESC);
CREATE INDEX IF NOT EXISTS idx_mappings_deleted      ON mappings(deleted);

CREATE TABLE IF NOT EXISTS user_profiles (
    id                VARCHAR(36)   PRIMARY KEY,
    user_id           VARCHAR(255)  UNIQUE,
    email             VARCHAR(255),
    first_name        VARCHAR(255),
    last_name         VARCHAR(255),
    password_hash     VARCHAR(500),
    namespaces        JSONB,
    default_namespace VARCHAR(255),
    created_at        TIMESTAMPTZ,
    last_login        TIMESTAMPTZ,
    active            BOOLEAN       DEFAULT TRUE,
    deleted           BOOLEAN       DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_profiles_user_id ON user_profiles(user_id);
CREATE        INDEX IF NOT EXISTS idx_user_profiles_email   ON user_profiles(email);

CREATE TABLE IF NOT EXISTS namespaces (
    id           VARCHAR(36)   PRIMARY KEY,
    name         VARCHAR(255)  UNIQUE,
    display_name VARCHAR(255),
    description  TEXT,
    members      JSONB,
    owner        VARCHAR(255),
    created_at   TIMESTAMPTZ,
    active       BOOLEAN       DEFAULT TRUE,
    deleted      BOOLEAN       DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_namespaces_name ON namespaces(name);

CREATE TABLE IF NOT EXISTS datasets (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255),
    description TEXT,
    active      BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMPTZ,
    updated_at  TIMESTAMPTZ
);
