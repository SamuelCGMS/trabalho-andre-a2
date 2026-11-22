-- Conferir volume de dados e indices existentes.
SELECT 'categoria' AS tabela, COUNT(*) FROM categoria
UNION ALL SELECT 'cliente', COUNT(*) FROM cliente
UNION ALL SELECT 'produto', COUNT(*) FROM produto
UNION ALL SELECT 'pedido', COUNT(*) FROM pedido
UNION ALL SELECT 'item_pedido', COUNT(*) FROM item_pedido;

SELECT tablename, indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname;
