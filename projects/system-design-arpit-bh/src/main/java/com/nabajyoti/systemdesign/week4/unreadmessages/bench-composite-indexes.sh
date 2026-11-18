#!/usr/bin/env bash
set -euo pipefail

export PGPASSWORD=password
PSQL="docker exec unread-postgres psql -U admin -d pgdb -v ON_ERROR_STOP=1 -qAt"
OUT="$(dirname "$0")/composite-index-bench.txt"
: > "$OUT"

run_sql() {
  $PSQL -c "$1"
}

median_of_five() {
  printf '%s\n' "$@" | sort -n | sed -n '3p'
}

explain_once() {
  local sql="$1"
  $PSQL -c "EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) ${sql}"
}

bench_query() {
  local label="$1"
  local sql="$2"

  echo "--- $label ---" | tee -a "$OUT"
  echo "warmup plan:" | tee -a "$OUT"
  explain_once "$sql" | tee -a "$OUT"

  times=()
  for i in 1 2 3 4 5; do
    plan="$(explain_once "$sql")"
    ms="$(printf '%s\n' "$plan" | awk '/Execution Time/ { print $(NF-1) }')"
    times+=("$ms")
    echo "run $i execution_time_ms=$ms" | tee -a "$OUT"
  done

  echo "median_execution_time_ms=$(median_of_five "${times[@]}")" | tee -a "$OUT"
  echo | tee -a "$OUT"
}

BADGE="SELECT COUNT(*) FROM messages
WHERE recipient_id = 10 AND conversation_id = 1 AND read_at IS NULL"

RECIPIENT="SELECT COUNT(*) FROM messages
WHERE recipient_id = 10 AND read_at IS NULL"

run_sql "
  DROP INDEX IF EXISTS idx_messages_recipient_id;
  DROP INDEX IF EXISTS idx_messages_conversation_id;
  DROP INDEX IF EXISTS idx_messages_read_at;
  DROP INDEX IF EXISTS idx_messages_rcp_conv_read;
  DROP INDEX IF EXISTS idx_messages_conv_rcp_read;
  DROP INDEX IF EXISTS idx_messages_read_rcp_conv;
"

bench_index() {
  local name="$1"
  local ddl="$2"

  echo "===== $name =====" | tee -a "$OUT"
  run_sql "DROP INDEX IF EXISTS idx_messages_rcp_conv_read;
           DROP INDEX IF EXISTS idx_messages_conv_rcp_read;
           DROP INDEX IF EXISTS idx_messages_read_rcp_conv;"
  run_sql "$ddl"
  run_sql "ANALYZE messages;"

  echo "indexes:" | tee -a "$OUT"
  run_sql "
    SELECT indexrelid::regclass || ' ' || pg_size_pretty(pg_relation_size(indexrelid))
    FROM pg_index
    JOIN pg_class ON pg_class.oid = pg_index.indrelid
    WHERE pg_class.relname = 'messages'
    ORDER BY 1;
  " | tee -a "$OUT"

  bench_query "badge query (all three predicates)" "$BADGE"
  bench_query "recipient-wide query (no conversation_id)" "$RECIPIENT"
}

bench_index "composite (recipient_id, conversation_id, read_at)" \
  "CREATE INDEX idx_messages_rcp_conv_read
   ON messages (recipient_id, conversation_id, read_at);"

bench_index "composite (conversation_id, recipient_id, read_at)" \
  "CREATE INDEX idx_messages_conv_rcp_read
   ON messages (conversation_id, recipient_id, read_at);"

bench_index "composite (read_at, recipient_id, conversation_id)" \
  "CREATE INDEX idx_messages_read_rcp_conv
   ON messages (read_at, recipient_id, conversation_id);"

run_sql "
  DROP INDEX IF EXISTS idx_messages_rcp_conv_read;
  DROP INDEX IF EXISTS idx_messages_conv_rcp_read;
  DROP INDEX IF EXISTS idx_messages_read_rcp_conv;
"

echo "wrote $OUT"