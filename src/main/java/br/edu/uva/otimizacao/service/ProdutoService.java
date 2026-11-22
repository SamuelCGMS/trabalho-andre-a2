package br.edu.uva.otimizacao.service;

import br.edu.uva.otimizacao.dto.AtualizarProdutoRequest;
import br.edu.uva.otimizacao.dto.ProdutoDTO;
import br.edu.uva.otimizacao.entity.Produto;
import br.edu.uva.otimizacao.repository.ProdutoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class ProdutoService {
    private static final BigDecimal MAIOR_PRECO = new BigDecimal("9999999999.99");

    private final ProdutoRepository repo;
    private final CacheService cache;

    public ProdutoService(ProdutoRepository repo, CacheService cache) {
        this.repo = repo;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ProdutoDTO buscar(Long id) {
        return repo.buscarComCategoria(id).map(ProdutoDTO::de).orElseThrow(() -> naoEncontrado(id));
    }

    @Transactional
    public ProdutoDTO atualizar(Long id, AtualizarProdutoRequest req, boolean invalidarCache) {
        validar(req);
        Produto p = repo.buscarComCategoria(id).orElseThrow(() -> naoEncontrado(id));
        if (req.nome() != null) p.setNome(req.nome());
        if (req.preco() != null) p.setPreco(req.preco().setScale(2));
        if (req.estoque() != null) p.setEstoque(req.estoque());
        if (invalidarCache) {
            // A rota mantém o bloqueio de escrita até o commit. Se o Redis falhar, a transação é revertida.
            cache.invalidar(cache.chaveProduto(id), cache.chaveCategoriaStats(p.getCategoria().getId()));
        }
        return ProdutoDTO.de(p);
    }

    private static void validar(AtualizarProdutoRequest req) {
        if (req.nome() != null && (req.nome().isBlank() || req.nome().length() > 150)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um nome com 1 a 150 caracteres.");
        }
        BigDecimal preco = req.preco();
        if (preco != null && (preco.signum() < 0 || preco.compareTo(MAIOR_PRECO) > 0
                || preco.stripTrailingZeros().scale() > 2)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um preço válido com até duas casas decimais.");
        }
        if (req.estoque() != null && req.estoque() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O estoque deve ser maior ou igual a zero.");
        }
    }

    private static ResponseStatusException naoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado: " + id);
    }
}
