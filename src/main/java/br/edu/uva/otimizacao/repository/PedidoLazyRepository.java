package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.dto.PedidoResumoDTO;
import br.edu.uva.otimizacao.entity.PedidoLazy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PedidoLazyRepository extends JpaRepository<PedidoLazy, Long> {

    // ---- Versao LAZY (origem do problema N+1) ----
    @Query("select p from PedidoLazy p order by p.id")
    List<PedidoLazy> primeiros(Pageable pageable);

    // ---- Otimizacao 1: JOIN FETCH em 2 passos (evita paginacao em memoria com colecao) ----
    @Query("select p.id from PedidoLazy p order by p.id")
    List<Long> primeirosIds(Pageable pageable);

    @Query("select p from PedidoLazy p join fetch p.cliente left join fetch p.itens "
            + "where p.id in :ids order by p.id")
    List<PedidoLazy> comJoinFetch(@Param("ids") List<Long> ids);

    // ---- Otimizacao 2: projecao DTO (1 query, so as colunas necessarias, contagem no banco) ----
    @Query("select new br.edu.uva.otimizacao.dto.PedidoResumoDTO(p.id, c.nome, p.dataPedido, p.status, "
            + "p.valorTotal, count(i.id)) "
            + "from PedidoLazy p join p.cliente c left join p.itens i "
            + "group by p.id, c.nome, p.dataPedido, p.status, p.valorTotal order by p.id")
    List<PedidoResumoDTO> resumos(Pageable pageable);
}
