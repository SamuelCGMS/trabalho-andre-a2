CREATE INDEX idx_pedido_cliente_data ON pedido (cliente_id, data_pedido DESC, id DESC);
ANALYZE pedido;

SELECT pg_size_pretty(pg_relation_size('idx_pedido_cliente_data')) AS tamanho_indice,
       pg_size_pretty(pg_relation_size('pedido'))                 AS tamanho_tabela;
