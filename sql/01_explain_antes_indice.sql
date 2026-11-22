-- PASSO 1: plano de execução SEM o índice do experimento.
-- Rodar: docker compose exec -T postgres psql -U a2 -d a2db -f /sql/01_explain_antes_indice.sql
DROP INDEX IF EXISTS idx_pedido_cliente_data;
ANALYZE pedido;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, data_pedido, status, valor_total
FROM pedido
WHERE cliente_id = 150
ORDER BY data_pedido DESC, id DESC;
-- Esperado: Seq Scan on pedido (lê a tabela inteira) + Sort.
