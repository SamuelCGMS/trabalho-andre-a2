package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.dto.CategoriaEstatisticasDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Repository
public class CategoriaEstatisticasRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Para calcular as vendas da categoria, a consulta cruza produtos, pedidos e itens.
     * Na base de teste, os itens somam cerca de 300 mil linhas. O cache evita repetir essa agregação.
     */
    @SuppressWarnings("unchecked")
    public Optional<CategoriaEstatisticasDTO> calcular(Long categoriaId) {
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT c.id, c.nome,
                       p.total_produtos, COALESCE(p.preco_medio, 0) AS preco_medio,
                       COALESCE(v.qtd, 0) AS unidades,
                       COALESCE(v.receita, 0) AS receita
                FROM categoria c
                CROSS JOIN (SELECT COUNT(*) AS total_produtos, AVG(preco) AS preco_medio
                            FROM produto WHERE categoria_id = :id) p
                CROSS JOIN (SELECT SUM(i.quantidade) AS qtd, SUM(i.quantidade * i.preco_unitario) AS receita
                            FROM item_pedido i
                            JOIN produto pr ON pr.id = i.produto_id
                            JOIN pedido o ON o.id = i.pedido_id
                            WHERE pr.categoria_id = :id
                              AND o.status IN ('PAGO', 'ENVIADO', 'ENTREGUE')) v
                WHERE c.id = :id
                """).setParameter("id", categoriaId).getResultList();

        if (linhas.isEmpty()) return Optional.empty();
        Object[] r = linhas.get(0);
        return Optional.of(new CategoriaEstatisticasDTO(
                ((Number) r[0]).longValue(), (String) r[1], ((Number) r[2]).longValue(),
                dinheiro(r[3]), ((Number) r[4]).longValue(), dinheiro(r[5])));
    }

    private static BigDecimal dinheiro(Object o) {
        return new BigDecimal(String.valueOf(o)).setScale(2, RoundingMode.HALF_UP);
    }
}
