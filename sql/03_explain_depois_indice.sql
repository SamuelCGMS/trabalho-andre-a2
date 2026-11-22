-- PASSO 3: plano de execucao COM o indice.
-- Rodar: docker compose exec -T postgres psql -U a2 -d a2db -f /sql/03_explain_depois_indice.sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, data_pedido, status, valor_total
FROM pedido
WHERE cliente_id = 150
ORDER BY data_pedido DESC, id DESC;
-- Um Bitmap Heap Scan pode exigir Sort, mesmo usando o indice do experimento.
