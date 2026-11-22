package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;

public record ItemDTO(Long produtoId, Integer quantidade, BigDecimal precoUnitario) { }
