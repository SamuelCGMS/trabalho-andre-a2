package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    /** Uma única query (JOIN com categoria) - evita um SELECT extra ao ler o nome da categoria. */
    @Query("select p from Produto p join fetch p.categoria where p.id = :id")
    Optional<Produto> buscarComCategoria(@Param("id") Long id);
}
