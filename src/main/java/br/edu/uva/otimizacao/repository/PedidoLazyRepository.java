package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.dto.PedidoResumoDTO;
import br.edu.uva.otimizacao.entity.PedidoLazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoLazyRepository extends JpaRepository<PedidoLazy, Long> {

    @Query("select p from PedidoLazy p order by p.id")
    List<PedidoLazy> primeiros(Pageable pageable);

    // Paginar só os IDs permite usar JOIN FETCH nos itens sem paginar em memória.
    @Query("select p.id from PedidoLazy p order by p.id")
    List<Long> primeirosIds(Pageable pageable);

    @Query("select p from PedidoLazy p join fetch p.cliente left join fetch p.itens "
            + "where p.id in :ids order by p.id")
    List<PedidoLazy> comJoinFetch(@Param("ids") List<Long> ids);

    // O banco conta os itens na mesma consulta que monta o resumo.
    @Query("select new br.edu.uva.otimizacao.dto.PedidoResumoDTO(p.id, c.nome, p.dataPedido, p.status, "
            + "p.valorTotal, count(i.id)) "
            + "from PedidoLazy p join p.cliente c left join p.itens i "
            + "group by p.id, c.nome, p.dataPedido, p.status, p.valorTotal order by p.id")
    List<PedidoResumoDTO> resumos(Pageable pageable);
}
