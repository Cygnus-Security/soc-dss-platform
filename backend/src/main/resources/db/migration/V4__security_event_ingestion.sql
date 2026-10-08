ALTER TABLE security_alerts
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS schema_version VARCHAR(16),
    ADD COLUMN IF NOT EXISTS ingested_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS normalized_severity DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS confidence DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS raw_reference VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS normalizer_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS metadata_json TEXT,
    ADD COLUMN IF NOT EXISTS ingestion_status VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS ux_security_alerts_tenant_event
    ON security_alerts (tenant_id, event_id);

CREATE INDEX IF NOT EXISTS ix_security_alerts_event_timestamp
    ON security_alerts (event_timestamp);

CREATE INDEX IF NOT EXISTS ix_security_alerts_source_type
    ON security_alerts (source, incident_type);
