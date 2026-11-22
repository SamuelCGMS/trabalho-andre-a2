package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.entity.PedidoEager;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PedidoEagerRepository extends JpaRepository<PedidoEager, Long> {

    /** Nesta consulta, o Hibernate carrega as relações EAGER com SELECTs adicionais. */
    @Query("select p from PedidoEager p order by p.id")
    List<PedidoEager> primeiros(Pageable pageable);
}
