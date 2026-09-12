CREATE TABLE schema_initialization_check (
    id BIGSERIAL PRIMARY KEY,
    initialized_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    description VARCHAR(255) NOT NULL
);

INSERT INTO schema_initialization_check (description) VALUES ('Phase 0 schema initialized successfully');
