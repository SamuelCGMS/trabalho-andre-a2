package br.edu.uva.otimizacao.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedido")
public class PedidoEager extends PedidoBase {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id", insertable = false, updatable = false)
    @OrderBy("id ASC")
    private List<ItemPedido> itens = new ArrayList<>();

    protected PedidoEager() { }

    @Override public Cliente getCliente() { return cliente; }
    @Override public List<ItemPedido> getItens() { return itens; }
}
