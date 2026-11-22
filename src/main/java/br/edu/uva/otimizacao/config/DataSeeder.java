package br.edu.uva.otimizacao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Popula o banco automaticamente na primeira execucao (se a tabela pedido estiver vazia).
 * Usa generate_series do PostgreSQL: gera centenas de milhares de linhas em segundos.
 * Dados deterministicos (sem random): todo mundo obtem o mesmo conjunto.
 */
@Component
@ConditionalOnProperty(name = "app.seed.habilitado", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final int clientes;
    private final int produtos;
    private final int pedidos;

    public DataSeeder(JdbcTemplate jdbc, TransactionTemplate tx,
                      @Value("${app.seed.clientes:5000}") int clientes,
                      @Value("${app.seed.produtos:10000}") int produtos,
                      @Value("${app.seed.pedidos:100000}") int pedidos) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.clientes = clientes;
        this.produtos = produtos;
        this.pedidos = pedidos;
    }

    @Override
    public void run(String... args) {
        Long existentes = jdbc.queryForObject("SELECT COUNT(*) FROM pedido", Long.class);
        if (existentes != null && existentes > 0) {
            log.info("Seed ignorado: banco ja possui {} pedidos.", existentes);
            return;
        }
        log.info("Populando banco: {} clientes, {} produtos, {} pedidos (+ ~3 itens por pedido)...",
                clientes, produtos, pedidos);
        long inicio = System.currentTimeMillis();

        // Tudo em UMA transacao: se falhar no meio, nada fica pela metade.
        tx.executeWithoutResult(status -> {
            exec("""
                INSERT INTO categoria (id, nome)
                SELECT g, 'Categoria ' || g FROM generate_series(1, 20) g
                """);

            exec("""
                INSERT INTO cliente (id, nome, email, cidade)
                SELECT g, 'Cliente ' || g, 'cliente' || g || '@exemplo.com',
                       (ARRAY['Rio de Janeiro','Sao Paulo','Belo Horizonte','Curitiba',
                              'Porto Alegre','Salvador','Recife','Fortaleza'])[1 + (g % 8)]
                FROM generate_series(1, {CLIENTES}) g
                """);

            exec("""
                INSERT INTO produto (id, nome, preco, estoque, categoria_id)
                SELECT g, 'Produto ' || g,
                       round(5 + ((g::bigint * 37) % 99500) / 100.0, 2),
                       10 + (g * 13) % 990,
                       1 + (g % 20)
                FROM generate_series(1, {PRODUTOS}) g
                """);

            // Pedidos espalhados por ~4 anos, clientes distribuidos de forma uniforme
            exec("""
                INSERT INTO pedido (id, cliente_id, data_pedido, status, valor_total)
                SELECT g,
                       1 + ((g::bigint * 7919) % {CLIENTES}),
                       TIMESTAMP '2023-01-01 00:00:00'
                           + (((g::bigint * 104729) % 1460) * INTERVAL '1 day')
                           + (((g::bigint * 37) % 86400) * INTERVAL '1 second'),
                       (ARRAY['NOVO','PAGO','ENVIADO','ENTREGUE','CANCELADO'])[1 + (g % 5)],
                       0
                FROM generate_series(1, {PEDIDOS}) g
                """);

            // De 1 a 5 itens por pedido (media 3)
            exec("""
                INSERT INTO item_pedido (pedido_id, produto_id, quantidade, preco_unitario)
                SELECT p.id, pr.id, 1 + ((p.id + n) % 5), pr.preco
                FROM pedido p
                CROSS JOIN LATERAL generate_series(1, 1 + (p.id % 5)) n
                JOIN produto pr ON pr.id = 1 + ((p.id * 31 + n * 977) % {PRODUTOS})
                """);

            exec("""
                UPDATE pedido p SET valor_total = s.total
                FROM (SELECT pedido_id, SUM(quantidade * preco_unitario) AS total
                      FROM item_pedido GROUP BY pedido_id) s
                WHERE s.pedido_id = p.id
                """);

            // Ajusta as sequences (ids foram inseridos manualmente)
            for (String t : new String[]{"categoria", "cliente", "produto", "pedido"}) {
                jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('" + t + "','id'), (SELECT MAX(id) FROM " + t + "))",
                        Long.class);
            }
        });

        jdbc.execute("ANALYZE");   // estatisticas atualizadas para o planejador de consultas
        log.info("Carga concluida em {} ms.", System.currentTimeMillis() - inicio);
    }

    private void exec(String sql) {
        jdbc.update(sql.replace("{CLIENTES}", String.valueOf(clientes))
                       .replace("{PRODUTOS}", String.valueOf(produtos))
                       .replace("{PEDIDOS}", String.valueOf(pedidos)));
    }
}
