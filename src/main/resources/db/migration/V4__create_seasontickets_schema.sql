CREATE TABLE season_tickets (
    id BIGSERIAL PRIMARY KEY,
    barcode VARCHAR(100) NOT NULL UNIQUE,
    club_id BIGINT NOT NULL REFERENCES clubs(id) ON DELETE CASCADE,
    season VARCHAR(20) NOT NULL,
    seat_id BIGINT NOT NULL REFERENCES seats(id) ON DELETE CASCADE,
    owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'UNCLAIMED',
    claimed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE match_entitlements (
    id BIGSERIAL PRIMARY KEY,
    season_ticket_id BIGINT NOT NULL REFERENCES season_tickets(id) ON DELETE CASCADE,
    match_id BIGINT NOT NULL REFERENCES matches(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL DEFAULT 'OWNER_HELD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_season_ticket_match UNIQUE (season_ticket_id, match_id)
);

CREATE INDEX idx_season_tickets_barcode ON season_tickets(barcode);
CREATE INDEX idx_season_tickets_owner ON season_tickets(owner_id);
CREATE INDEX idx_match_entitlements_ticket ON match_entitlements(season_ticket_id);
CREATE INDEX idx_match_entitlements_match ON match_entitlements(match_id);
