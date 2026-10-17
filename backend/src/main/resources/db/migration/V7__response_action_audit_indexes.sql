CREATE INDEX IF NOT EXISTS ix_response_actions_requested
    ON response_actions (requested_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS ix_response_actions_status_requested
    ON response_actions (status, requested_at DESC);

CREATE INDEX IF NOT EXISTS ix_response_actions_type_requested
    ON response_actions (action_type, requested_at DESC);
