CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    org_id          BIGINT       NOT NULL REFERENCES organizations(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
