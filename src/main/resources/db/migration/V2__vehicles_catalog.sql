-- ============================================================
-- Spectrum AI - Catálogo de veículos (autocomplete)
-- ============================================================

CREATE TABLE vehicles_catalog (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    brand       VARCHAR(100) NOT NULL,
    model       VARCHAR(100) NOT NULL,
    trim        VARCHAR(100),
    year_from   SMALLINT NOT NULL,
    year_to     SMALLINT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vehicles_year_range CHECK (year_from <= year_to)
);

CREATE INDEX idx_vehicles_brand_lower       ON vehicles_catalog (LOWER(brand));
CREATE INDEX idx_vehicles_brand_model_lower ON vehicles_catalog (LOWER(brand), LOWER(model));

-- Seed inicial mínimo. Pode ser substituído por carga ETL posteriormente.
INSERT INTO vehicles_catalog (brand, model, trim, year_from, year_to) VALUES
    ('Toyota',     'Corolla',     'XEi',          2020, 2026),
    ('Toyota',     'Corolla',     'GLi',          2020, 2026),
    ('Toyota',     'Corolla',     'Altis',        2020, 2026),
    ('Toyota',     'Corolla',     'Altis Premium',2022, 2026),
    ('Toyota',     'Corolla Cross','XR',          2021, 2026),
    ('Toyota',     'Corolla Cross','XRE',         2021, 2026),
    ('Toyota',     'Hilux',       'SR',           2020, 2026),
    ('Toyota',     'Hilux',       'SRX',          2020, 2026),
    ('Honda',      'Civic',       'EX',           2020, 2026),
    ('Honda',      'Civic',       'Touring',      2020, 2026),
    ('Honda',      'City',        'EX',           2020, 2026),
    ('Honda',      'HR-V',        'EXL',          2020, 2026),
    ('Honda',      'HR-V',        'Touring',      2022, 2026),
    ('Volkswagen', 'Golf',        'GTI',          2020, 2024),
    ('Volkswagen', 'Polo',        'Comfortline',  2020, 2026),
    ('Volkswagen', 'Polo',        'Highline',     2020, 2026),
    ('Volkswagen', 'T-Cross',     'Comfortline',  2020, 2026),
    ('Volkswagen', 'T-Cross',     'Highline',     2020, 2026),
    ('Chevrolet',  'Onix',        'LT',           2020, 2026),
    ('Chevrolet',  'Onix',        'Premier',      2020, 2026),
    ('Chevrolet',  'Tracker',     'LT',           2021, 2026),
    ('Chevrolet',  'Tracker',     'Premier',      2021, 2026),
    ('Hyundai',    'HB20',        'Comfort',      2020, 2026),
    ('Hyundai',    'HB20',        'Platinum',     2020, 2026),
    ('Hyundai',    'Creta',       'Limited',      2022, 2026),
    ('Fiat',       'Pulse',       'Drive',        2022, 2026),
    ('Fiat',       'Pulse',       'Impetus',      2022, 2026),
    ('Fiat',       'Strada',      'Freedom',      2021, 2026),
    ('Renault',    'Kwid',        'Zen',          2020, 2026),
    ('Renault',    'Duster',      'Iconic',       2021, 2026),
    ('Nissan',     'Kicks',       'Advance',      2021, 2026),
    ('Nissan',     'Kicks',       'Exclusive',    2021, 2026),
    ('Jeep',       'Compass',     'Longitude',    2020, 2026),
    ('Jeep',       'Compass',     'Limited',      2020, 2026),
    ('Jeep',       'Renegade',    'Sport',        2020, 2026),
    ('Jeep',       'Renegade',    'Longitude',    2020, 2026);
