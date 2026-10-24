CREATE TABLE cv (
    id               BIGSERIAL PRIMARY KEY,
    first_name       VARCHAR(255) NOT NULL,
    last_name        VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NOT NULL UNIQUE,
    summary          TEXT,
    source_file_name VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE experience (
    id         BIGSERIAL PRIMARY KEY,
    cv_id      BIGINT       NOT NULL REFERENCES cv (id) ON DELETE CASCADE,
    start_year INTEGER,
    end_year   INTEGER,
    position   VARCHAR(255) NOT NULL,
    company    VARCHAR(255)
);

CREATE INDEX idx_experience_cv_id ON experience (cv_id);
