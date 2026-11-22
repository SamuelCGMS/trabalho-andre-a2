-- PASSO 2: criar o índice para filtrar por cliente e ordenar por data e ID decrescentes.
-- Rodar: docker compose exec -T postgres psql -U a2 -d a2db -f /sql/02_criar_indice.sql
CREATE INDEX idx_pedido_cliente_data ON pedido (cliente_id, data_pedido DESC, id DESC);
ANALYZE pedido;

-- Tamanho do índice (custo de armazenamento):
SELECT pg_size_pretty(pg_relation_size('idx_pedido_cliente_data')) AS tamanho_indice,
       pg_size_pretty(pg_relation_size('pedido'))                 AS tamanho_tabela;
