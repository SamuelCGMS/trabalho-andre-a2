package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;

public record CategoriaEstatisticasDTO(Long categoriaId, String categoriaNome, Long totalProdutos,
                                       BigDecimal precoMedio, Long unidadesVendidas, BigDecimal receitaTotal) { }
