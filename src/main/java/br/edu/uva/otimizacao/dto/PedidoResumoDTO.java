package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Resumo do pedido com a contagem de itens calculada no banco. */
public record PedidoResumoDTO(Long id, String cliente, LocalDateTime dataPedido, String status,
                              BigDecimal valorTotal, Long qtdItens) { }
