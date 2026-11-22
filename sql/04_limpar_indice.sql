-- Volta ao estado inicial (sem o indice do experimento).
DROP INDEX IF EXISTS idx_pedido_cliente_data;
ANALYZE pedido;
