CREATE SEQUENCE short_url_id_seq MINVALUE 0 START WITH 0 MAXVALUE 14776335 NO CYCLE;

CREATE TABLE short_url (
    id          BIGINT      PRIMARY KEY,
    code        VARCHAR(4)  COLLATE "C" NOT NULL,
    target_url  TEXT        NOT NULL,
    url_hash    BYTEA       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_short_url_code     UNIQUE (code),
    CONSTRAINT uq_short_url_url_hash UNIQUE (url_hash),
    CONSTRAINT ck_short_url_code     CHECK (code ~ '^[0-9A-Za-z]{4}$'),
    CONSTRAINT ck_short_url_url_len  CHECK (char_length(target_url) <= 2048)
);
