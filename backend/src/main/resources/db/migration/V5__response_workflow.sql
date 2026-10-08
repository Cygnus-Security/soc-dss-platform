ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS correlation_key VARCHAR(512);

CREATE UNIQUE INDEX IF NOT EXISTS ux_incidents_correlation_key
    ON incidents (correlation_key);

CREATE TABLE IF NOT EXISTS response_actions (
    id VARCHAR(36) PRIMARY KEY,
    incident_id BIGINT NOT NULL REFERENCES incidents(id) ON DELETE CASCADE,
    action_type VARCHAR(64) NOT NULL,
    target VARCHAR(255) NOT NULL,
    rationale TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_by VARCHAR(255),
    decided_by VARCHAR(255),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    decided_at TIMESTAMP WITH TIME ZONE,
    approval_expires_at TIMESTAMP WITH TIME ZONE,
    result_message TEXT,
    result_payload TEXT,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS ix_response_actions_incident
    ON response_actions (incident_id, requested_at DESC);

CREATE INDEX IF NOT EXISTS ix_response_actions_status
    ON response_actions (status);
