INSERT INTO messages (
    conversation_id,
    sender_id,
    recipient_id,
    created_at,
    read_at
)
SELECT
    c,
    1 + ((c + r) % 50),
    r,
    TIMESTAMP '2024-01-01 00:00:00+00' + (n * INTERVAL '1 second'),
    CASE
        WHEN n % 5 = 0 THEN NULL
        ELSE TIMESTAMP '2024-01-02 00:00:00+00'
    END
FROM generate_series(1, 200) AS r,
     generate_series(1, 100) AS c,
     generate_series(1, 50) AS n;