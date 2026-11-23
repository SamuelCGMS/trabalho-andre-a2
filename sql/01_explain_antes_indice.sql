DROP INDEX IF EXISTS idx_pedido_cliente_data;
ANALYZE pedido;

EXPLAIN (ANALYZE, BUFFERS)
SELECT id, data_pedido, status, valor_total
FROM pedido
WHERE cliente_id = 150
ORDER BY data_pedido DESC, id DESC;
