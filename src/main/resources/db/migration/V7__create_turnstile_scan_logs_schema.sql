CREATE TABLE turnstile_scan_logs (
    id BIGSERIAL PRIMARY KEY,
    turnstile_id VARCHAR(50) NOT NULL,
    match_id BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    operator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    barcode_identifier VARCHAR(120),
    barcode_hash VARCHAR(64),
    ticket_type VARCHAR(50) NOT NULL,
    resale_ticket_id BIGINT REFERENCES resale_tickets(id) ON DELETE SET NULL,
    season_ticket_id BIGINT REFERENCES season_tickets(id) ON DELETE SET NULL,
    scan_result VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(255),
    scanned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_turnstile_scan_logs_match ON turnstile_scan_logs(match_id);
CREATE INDEX idx_turnstile_scan_logs_turnstile ON turnstile_scan_logs(turnstile_id);
CREATE INDEX idx_turnstile_scan_logs_barcode_hash ON turnstile_scan_logs(barcode_hash);
CREATE INDEX idx_turnstile_scan_logs_scanned_at ON turnstile_scan_logs(scanned_at);
