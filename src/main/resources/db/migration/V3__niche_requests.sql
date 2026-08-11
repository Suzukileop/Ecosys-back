CREATE TABLE niche_requests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  client_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  agent_id UUID REFERENCES users(id) ON DELETE SET NULL,
  niche_theme VARCHAR(200) NOT NULL,
  description TEXT,
  ref_model_id VARCHAR(20),
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  proposed_model_id VARCHAR(20),
  notes TEXT,
  deadline TIMESTAMP,
  created_at TIMESTAMP DEFAULT now(),
  updated_at TIMESTAMP
);
CREATE INDEX idx_niche_requests_client_id ON niche_requests(client_id);
CREATE INDEX idx_niche_requests_agent_id ON niche_requests(agent_id);
CREATE INDEX idx_niche_requests_status ON niche_requests(status);
