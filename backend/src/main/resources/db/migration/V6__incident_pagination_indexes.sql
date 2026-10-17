CREATE INDEX IF NOT EXISTS idx_incidents_risk_score_id
    ON incidents (risk_score DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_incidents_risk_level
    ON incidents (risk_level);
