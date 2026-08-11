CREATE TABLE service_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(30) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  unique_code VARCHAR(20) UNIQUE NOT NULL,
  metadata JSONB,
  created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_service_requests_client_id ON service_requests(client_id);
CREATE INDEX idx_service_requests_unique_code ON service_requests(unique_code);
CREATE INDEX idx_service_requests_status ON service_requests(status);
