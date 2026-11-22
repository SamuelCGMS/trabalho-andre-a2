package br.edu.uva.otimizacao.controller;

import br.edu.uva.otimizacao.dto.CategoriaEstatisticasDTO;
import br.edu.uva.otimizacao.dto.RespostaCache;
import br.edu.uva.otimizacao.service.CacheService;
import br.edu.uva.otimizacao.service.CategoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/** Segunda operacao cacheavel: agregacao PESADA (custo alto no banco, baixa volatilidade). */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService service;
    private final CacheService cache;
    private final long ttlPadraoSegundos;

    public CategoriaController(CategoriaService service, CacheService cache,
                               @Value("${app.cache.ttl-categoria-segundos:300}") long ttlPadraoSegundos) {
        this.service = service;
        this.cache = cache;
        this.ttlPadraoSegundos = ttlPadraoSegundos;
    }

    @GetMapping("/{id}/estatisticas/sem-cache")
    public ResponseEntity<RespostaCache<CategoriaEstatisticasDTO>> semCache(@PathVariable Long id) {
        return ResponseEntity.ok().header("X-Cache", "BYPASS").body(cache.semCache(() -> service.estatisticas(id)));
    }

    @GetMapping("/{id}/estatisticas/com-cache")
    public ResponseEntity<RespostaCache<CategoriaEstatisticasDTO>> comCache(@PathVariable Long id,
                                                                            @RequestParam(required = false) Long ttl) {
        Duration duracao = cache.ttl(ttl, ttlPadraoSegundos);
        RespostaCache<CategoriaEstatisticasDTO> r = cache.comCache(cache.chaveCategoriaStats(id),
                CategoriaEstatisticasDTO.class, duracao, () -> service.estatisticas(id));
        return ResponseEntity.ok().header("X-Cache", r.cacheHit() ? "HIT" : "MISS").body(r);
    }
}
