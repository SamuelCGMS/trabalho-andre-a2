package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;

/** Campos nulos sao ignorados (atualizacao parcial). */
public record AtualizarProdutoRequest(String nome, BigDecimal preco, Integer estoque) { }
