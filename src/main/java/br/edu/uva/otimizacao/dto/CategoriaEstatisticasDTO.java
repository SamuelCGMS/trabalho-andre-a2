package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;

/** Resultado de uma agregacao pesada (varre ~300 mil itens): excelente candidato a cache. */
public record CategoriaEstatisticasDTO(Long categoriaId, String categoriaNome, Long totalProdutos,
                                       BigDecimal precoMedio, Long unidadesVendidas, BigDecimal receitaTotal) { }
