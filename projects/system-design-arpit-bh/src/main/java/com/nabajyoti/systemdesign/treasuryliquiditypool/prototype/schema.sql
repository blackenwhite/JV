CREATE TABLE pools (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    currency_code  varchar(3) NOT NULL
        CHECK (currency_code ~ '^[A-Z]{3}$'),
    UNIQUE (currency_code)
);

CREATE TABLE intents (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caller_id        text NOT NULL,
    caller_key       text NOT NULL,
    operation        text NOT NULL
        CHECK (operation IN ('fund', 'reserve', 'execute', 'release')),
    request_payload  jsonb NOT NULL,
    request_hash     bytea NOT NULL,
    status           text NOT NULL
        CHECK (status IN ('pending', 'done', 'rejected')),
    result_payload   jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    finalized_at     timestamptz,
    UNIQUE (caller_id, caller_key)
);

CREATE INDEX intents_pending_oldest_idx
    ON intents (created_at, id)
    WHERE status = 'pending';

CREATE TABLE reservations (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_intent_id bigint NOT NULL UNIQUE REFERENCES intents (id),
    source_pool_id   bigint NOT NULL REFERENCES pools (id),
    source_cents     bigint NOT NULL CHECK (source_cents > 0),
    dest_pool_id     bigint NOT NULL REFERENCES pools (id),
    dest_cents       bigint NOT NULL CHECK (dest_cents > 0),
    expires_at       timestamptz NOT NULL,
    status           text NOT NULL
        CHECK (status IN ('RESERVED', 'EXECUTED', 'RELEASED')),
    CHECK (source_pool_id <> dest_pool_id)
);

CREATE INDEX reservations_open_source_idx
    ON reservations (source_pool_id, expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX reservations_dest_pool_idx
    ON reservations (dest_pool_id);

CREATE TABLE swaps (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reservation_id   bigint NOT NULL UNIQUE REFERENCES reservations (id),
    execute_intent_id bigint NOT NULL UNIQUE REFERENCES intents (id),
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE postings (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_id     bigint NOT NULL REFERENCES pools (id),
    cents       bigint NOT NULL CHECK (cents <> 0),
    swap_id     bigint REFERENCES swaps (id),
    intent_id   bigint NOT NULL REFERENCES intents (id),
    created_at  timestamptz NOT NULL DEFAULT now(),
    CHECK (swap_id IS NOT NULL OR cents > 0)
);

CREATE INDEX postings_pool_idx ON postings (pool_id);
CREATE INDEX postings_intent_idx ON postings (intent_id);
CREATE INDEX postings_swap_idx ON postings (swap_id);

CREATE TABLE posted_totals (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pool_id       bigint NOT NULL REFERENCES pools (id),
    intent_id     bigint NOT NULL REFERENCES intents (id),
    posted_cents  bigint NOT NULL CHECK (posted_cents >= 0),
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX posted_totals_newest_idx
    ON posted_totals (pool_id, id DESC);