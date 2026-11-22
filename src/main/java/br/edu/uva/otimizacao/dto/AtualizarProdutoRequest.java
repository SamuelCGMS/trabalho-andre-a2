package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;

/** Campos nulos são ignorados (atualização parcial). */
public record AtualizarProdutoRequest(String nome, BigDecimal preco, Integer estoque) { }
