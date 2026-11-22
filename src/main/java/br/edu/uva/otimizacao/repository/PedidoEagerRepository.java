package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.entity.PedidoEager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PedidoEagerRepository extends JpaRepository<PedidoEager, Long> {

    /** JPQL: o Hibernate dispara a query principal e DEPOIS selects adicionais para os EAGER. */
    @Query("select p from PedidoEager p order by p.id")
    List<PedidoEager> primeiros(Pageable pageable);
}
