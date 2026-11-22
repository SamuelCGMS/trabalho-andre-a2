package br.edu.uva.otimizacao.service;

import br.edu.uva.otimizacao.config.QueryCountInspector;
import br.edu.uva.otimizacao.dto.RespostaCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

@Service
public class CacheService {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String prefixo;
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final ReentrantReadWriteLock acesso = new ReentrantReadWriteLock(true);

    public CacheService(StringRedisTemplate redis, ObjectMapper mapper, @Value("${app.cache.prefixo}") String prefixo) {
        this.redis = redis;
        this.mapper = mapper;
        this.prefixo = prefixo;
    }

    public String chaveProduto(Long id) {
        return prefixo + ":produto:" + id;
    }

    public String chaveCategoriaStats(Long id) {
        return prefixo + ":categoria-stats:" + id;
    }

    public Duration ttl(Long segundos, long padrao) {
        long ttl = segundos == null ? padrao : segundos;
        if (ttl < 1 || ttl > 3600) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TTL deve estar entre 1 e 3600 segundos.");
        }
        return Duration.ofSeconds(ttl);
    }

    public <T> RespostaCache<T> comCache(String chave, Class<T> tipo, Duration ttl, Supplier<T> carregarDoBanco) {
        long inicio = System.nanoTime();
        QueryCountInspector.reset();
        // Uma leitura pode preencher o cache. A atualização aguarda esse preenchimento antes de invalidar.
        var leitura = acesso.readLock();
        leitura.lock();
        try {
            String json = noRedis(() -> redis.opsForValue().get(chave));
            T valor = desserializar(json, tipo);
            boolean hit = valor != null;
            if (hit) {
                hits.incrementAndGet();
            } else {
                misses.incrementAndGet();
                valor = carregarDoBanco.get();
                String conteudo = serializar(valor);
                noRedis(() -> {
                    redis.opsForValue().set(chave, conteudo, ttl);
                    return null;
                });
            }
            Long restante = noRedis(() -> redis.getExpire(chave, TimeUnit.SECONDS));
            return new RespostaCache<>(hit ? "CACHE (hit)" : "BANCO (cache miss)", hit,
                    QueryCountInspector.total(), ms(inicio), restante, valor);
        } finally {
            leitura.unlock();
        }
    }

    public <T> RespostaCache<T> semCache(Supplier<T> carregarDoBanco) {
        long inicio = System.nanoTime();
        QueryCountInspector.reset();
        T valor = carregarDoBanco.get();
        return new RespostaCache<>("BANCO (sem cache)", false, QueryCountInspector.total(), ms(inicio), null, valor);
    }

    /** Protege a transação de atualização até o commit em uma instância da API. */
    public <T> T comEscrita(Supplier<T> alterar) {
        var escrita = acesso.writeLock();
        escrita.lock();
        try {
            return alterar.get();
        } finally {
            escrita.unlock();
        }
    }

    public void invalidar(String... chaves) {
        noRedis(() -> redis.delete(List.of(chaves)));
    }

    public long limparTudo() {
        return comEscrita(() -> noRedis(() -> {
            List<String> chaves = chaves();
            long removidas = chaves.isEmpty() ? 0 : redis.delete(chaves);
            hits.set(0);
            misses.set(0);
            return removidas;
        }));
    }

    public Map<String, Object> estatisticas() {
        long h = hits.get();
        long m = misses.get();
        double taxa = h + m == 0 ? 0 : 100.0 * h / (h + m);
        return Map.of("hits", h, "misses", m, "taxaDeHit", taxa,
                "chavesNoRedis", noRedis(() -> chaves().size()));
    }

    private List<String> chaves() {
        var opcoes = ScanOptions.scanOptions().match(prefixo + ":*").count(256).build();
        List<String> resultado = new ArrayList<>();
        try (Cursor<String> chaves = redis.scan(opcoes)) {
            chaves.forEachRemaining(resultado::add);
        }
        return resultado;
    }

    private <T> T desserializar(String json, Class<T> tipo) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, tipo);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String serializar(Object valor) {
        try {
            return mapper.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o valor do cache", e);
        }
    }

    private <T> T noRedis(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cache indisponível.", e);
        }
    }

    private static double ms(long inicioNanos) {
        return Math.round((System.nanoTime() - inicioNanos) / 1_000.0) / 1000.0;
    }
}
