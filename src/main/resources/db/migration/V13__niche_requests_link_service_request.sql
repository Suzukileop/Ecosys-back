ALTER TABLE niche_requests
  ADD COLUMN IF NOT EXISTS service_request_id UUID REFERENCES service_requests(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_niche_requests_service_request_id ON niche_requests(service_request_id);
