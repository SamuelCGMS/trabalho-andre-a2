package br.edu.uva.otimizacao.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Campos comuns. As subclasses mapeiam a MESMA tabela "pedido" mudando apenas o FetchType. */
@MappedSuperclass
public abstract class PedidoBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_pedido")
    private LocalDateTime dataPedido;

    @Column(name = "status")
    private String status;

    @Column(name = "valor_total")
    private BigDecimal valorTotal;

    public Long getId() { return id; }
    public LocalDateTime getDataPedido() { return dataPedido; }
    public String getStatus() { return status; }
    public BigDecimal getValorTotal() { return valorTotal; }

    public abstract Cliente getCliente();
    public abstract List<ItemPedido> getItens();
}
