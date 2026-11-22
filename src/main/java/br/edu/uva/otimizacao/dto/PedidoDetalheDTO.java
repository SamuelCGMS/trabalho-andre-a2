package br.edu.uva.otimizacao.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoDetalheDTO(Long id, String cliente, LocalDateTime dataPedido, String status,
                               BigDecimal valorTotal, int qtdItens, List<ItemDTO> itens) { }
