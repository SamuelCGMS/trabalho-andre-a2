package br.edu.uva.otimizacao.controller;

import br.edu.uva.otimizacao.service.CacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Endpoints de apoio aos experimentos (laboratorio academico - NAO usar assim em producao). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final String INDICE = "idx_pedido_cliente_data";

    private final CacheService cache;
    private final JdbcTemplate jdbc;
    private final int poolMaximo;

    public AdminController(CacheService cache, JdbcTemplate jdbc,
                           @Value("${spring.datasource.hikari.maximum-pool-size}") int poolMaximo) {
        this.cache = cache;
        this.jdbc = jdbc;
        this.poolMaximo = poolMaximo;
    }

    @GetMapping("/ambiente")
    public Map<String, Object> ambiente() {
        return Map.of("java", Runtime.version().toString(),
                "springBoot", org.springframework.boot.SpringBootVersion.getVersion(),
                "hibernate", org.hibernate.Version.getVersionString(),
                "postgresql", jdbc.queryForObject("SHOW server_version", String.class),
                "poolMaximo", poolMaximo);
    }

    @PostMapping("/cache/limpar")
    public Map<String, Object> limparCache() {
        return Map.of("chavesRemovidas", cache.limparTudo());
    }

    @GetMapping("/cache/estatisticas")
    public Map<String, Object> estatisticasCache() {
        return cache.estatisticas();
    }

    @PostMapping("/indice")
    public Map<String, Object> criarIndice() {
        jdbc.execute("CREATE INDEX IF NOT EXISTS " + INDICE + " ON pedido (cliente_id, data_pedido DESC, id DESC)");
        jdbc.execute("ANALYZE pedido");
        Long bytes = jdbc.queryForObject("SELECT pg_relation_size(to_regclass(?))", Long.class, INDICE);
        return Map.of("indice", INDICE, "estado", "CRIADO", "bytes", bytes);
    }

    @DeleteMapping("/indice")
    public Map<String, String> removerIndice() {
        jdbc.execute("DROP INDEX IF EXISTS " + INDICE);
        jdbc.execute("ANALYZE pedido");
        return Map.of("indice", INDICE, "estado", "REMOVIDO");
    }

    /** Quantidade de registros por tabela (para registrar no relatorio). */
    @GetMapping("/contagens")
    public Map<String, Long> contagens() {
        Map<String, Long> mapa = new LinkedHashMap<>();
        for (String t : new String[]{"categoria", "cliente", "produto", "pedido", "item_pedido"}) {
            mapa.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class));
        }
        return mapa;
    }
}
