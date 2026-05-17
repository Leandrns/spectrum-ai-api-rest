-- ============================================================
-- Spectrum AI - Latência da chamada ao Gemini
-- Persiste o tempo (em ms) que o provider levou para responder
-- a chamada de geração da ficha técnica, exposto na API como
-- métrica de performance da IA.
-- ============================================================

ALTER TABLE searches
    ADD COLUMN ai_latency_ms BIGINT;
