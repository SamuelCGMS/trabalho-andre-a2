-- PASSO 2: criar o indice composto (filtro por cliente + ordenacao por data decrescente).
-- Rodar: docker compose exec -T postgres psql -U a2 -d a2db -f /sql/02_criar_indice.sql
CREATE INDEX idx_pedido_cliente_data ON pedido (cliente_id, data_pedido DESC, id DESC);
ANALYZE pedido;

-- Tamanho do indice (custo de armazenamento):
SELECT pg_size_pretty(pg_relation_size('idx_pedido_cliente_data')) AS tamanho_indice,
       pg_size_pretty(pg_relation_size('pedido'))                 AS tamanho_tabela;
