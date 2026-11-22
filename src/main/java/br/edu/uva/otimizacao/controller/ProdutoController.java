package br.edu.uva.otimizacao.controller;

import br.edu.uva.otimizacao.dto.AtualizarProdutoRequest;
import br.edu.uva.otimizacao.dto.ProdutoDTO;
import br.edu.uva.otimizacao.dto.RespostaCache;
import br.edu.uva.otimizacao.service.CacheService;
import br.edu.uva.otimizacao.service.ProdutoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/produtos")
public class ProdutoController {

    private final ProdutoService service;
    private final CacheService cache;
    private final long ttlPadraoSegundos;

    public ProdutoController(ProdutoService service, CacheService cache,
                             @Value("${app.cache.ttl-produto-segundos:60}") long ttlPadraoSegundos) {
        this.service = service;
        this.cache = cache;
        this.ttlPadraoSegundos = ttlPadraoSegundos;
    }

    /** Cenário A: sempre consulta o banco. */
    @GetMapping("/{id}/sem-cache")
    public ResponseEntity<RespostaCache<ProdutoDTO>> semCache(@PathVariable Long id) {
        RespostaCache<ProdutoDTO> r = cache.semCache(() -> service.buscar(id));
        return ResponseEntity.ok().header("X-Cache", "BYPASS").body(r);
    }

    /** Consulta o Redis primeiro. O parâmetro ttl define a duração da chave quando ela é gravada. */
    @GetMapping("/{id}/com-cache")
    public ResponseEntity<RespostaCache<ProdutoDTO>> comCache(@PathVariable Long id,
                                                               @RequestParam(required = false) Long ttl) {
        Duration duracao = cache.ttl(ttl, ttlPadraoSegundos);
        RespostaCache<ProdutoDTO> r = cache.comCache(cache.chaveProduto(id), ProdutoDTO.class, duracao,
                () -> service.buscar(id));
        return ResponseEntity.ok().header("X-Cache", r.cacheHit() ? "HIT" : "MISS").body(r);
    }

    /**
     * Por padrão, invalida o cache do produto e das estatísticas da categoria.
     * Use invalidarCache=false para demonstrar como uma resposta antiga pode persistir até o TTL expirar.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProdutoDTO> atualizar(@PathVariable Long id,
                                                @RequestBody AtualizarProdutoRequest req,
                                                @RequestParam(defaultValue = "true") boolean invalidarCache) {
        ProdutoDTO atualizado = cache.comEscrita(() -> service.atualizar(id, req, invalidarCache));
        return ResponseEntity.ok().header("X-Cache-Invalidado", String.valueOf(invalidarCache)).body(atualizado);
    }
}
