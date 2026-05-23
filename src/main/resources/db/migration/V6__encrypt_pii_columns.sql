-- ============================================================
-- Expande colunas de PII que passarao a armazenar ciphertext (AES-GCM base64).
-- O ciphertext e cerca de 2x o tamanho do plaintext + overhead de IV/tag.
-- ============================================================

ALTER TABLE users
    ALTER COLUMN full_name TYPE VARCHAR(512);

ALTER TABLE audit_log
    ALTER COLUMN actor_email TYPE VARCHAR(512);
