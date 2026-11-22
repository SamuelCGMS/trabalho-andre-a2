package br.edu.uva.otimizacao.dto;

import br.edu.uva.otimizacao.entity.Produto;
import java.math.BigDecimal;

public record ProdutoDTO(Long id, String nome, BigDecimal preco, Integer estoque, Long categoriaId, String categoriaNome) {
    public static ProdutoDTO de(Produto p) {
        return new ProdutoDTO(p.getId(), p.getNome(), p.getPreco(), p.getEstoque(),
                p.getCategoria().getId(), p.getCategoria().getNome());
    }
}
