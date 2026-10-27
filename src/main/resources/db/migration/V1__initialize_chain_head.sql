-- Foundation only: event storage and append transactions belong to milestone 2.
CREATE TABLE chain_head (
    chain_id INTEGER PRIMARY KEY CHECK (chain_id = 1),
    last_sequence BIGINT NOT NULL CHECK (last_sequence >= 0),
    last_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(last_hash) = 64)
);

INSERT INTO chain_head (chain_id, last_sequence, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
