package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoSimplesDTO(Long id, LocalDateTime dataPedido, String status, BigDecimal valorTotal) { }
