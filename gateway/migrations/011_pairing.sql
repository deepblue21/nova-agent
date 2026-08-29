-- 011_pairing.sql — tek kullanımlık telefon eşleme kodları.
--
-- Amaç: kullanıcının telefona elle Base URL + API anahtarı yazmasını ortadan
-- kaldırmak. PC bir kod üretir (QR olarak gösterilir), telefon kodu takas eder
-- ve karşılığında YENİ bir API anahtarı alır.
--
-- Değişmez: düz metin sır asla saklanmaz. api_keys ile aynı ilke — burada da
-- yalnız kodun SHA-256 özeti tutulur. Anahtar, kod takas edilirken o an
-- üretilir; hiçbir zaman veritabanında düz metin olarak durmaz.

CREATE TABLE IF NOT EXISTS pairing_codes (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code_hash    text NOT NULL UNIQUE,              -- sha-256 hex (normalize edilmiş kodun)
  user_id      uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  label        text NOT NULL DEFAULT 'phone',     -- üretilen anahtarın etiketi
  created_at   timestamptz NOT NULL DEFAULT now(),
  expires_at   timestamptz NOT NULL,              -- kısa ömür (varsayılan 5 dk)
  claimed_at   timestamptz,                       -- tek kullanımlık: dolunca bir daha kullanılamaz
  claimed_key  uuid REFERENCES api_keys(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS pairing_codes_expires_idx ON pairing_codes (expires_at);
CREATE INDEX IF NOT EXISTS pairing_codes_user_idx    ON pairing_codes (user_id, created_at DESC);
