package br.edu.uva.otimizacao.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "item_pedido")
public class ItemPedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // O lado "pedido" da relacao e mapeado nas entidades PedidoLazy/PedidoEager (@JoinColumn pedido_id).
    @Column(name = "produto_id")
    private Long produtoId;

    @Column(name = "quantidade")
    private Integer quantidade;

    @Column(name = "preco_unitario")
    private BigDecimal precoUnitario;

    protected ItemPedido() { }

    public Long getId() { return id; }
    public Long getProdutoId() { return produtoId; }
    public Integer getQuantidade() { return quantidade; }
    public BigDecimal getPrecoUnitario() { return precoUnitario; }
}
