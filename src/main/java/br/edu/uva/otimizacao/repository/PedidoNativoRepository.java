package br.edu.uva.otimizacao.repository;

import br.edu.uva.otimizacao.dto.PedidoResumoDTO;
import br.edu.uva.otimizacao.dto.PedidoSimplesDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Consultas SQL NATIVAS (especificas do PostgreSQL) - usadas na comparacao com JPQL. */
@Repository
public class PedidoNativoRepository {

    private static final String PEDIDOS_DO_CLIENTE = """
            SELECT id, data_pedido, status, valor_total
            FROM pedido
            WHERE cliente_id = :clienteId
            ORDER BY data_pedido DESC, id DESC
            """;

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<PedidoResumoDTO> resumos(int limit) {
        List<Object[]> linhas = em.createNativeQuery("""
                SELECT p.id, c.nome, p.data_pedido, p.status, p.valor_total, COUNT(i.id)
                FROM (SELECT id, cliente_id, data_pedido, status, valor_total FROM pedido ORDER BY id LIMIT :limit) p
                JOIN cliente c ON c.id = p.cliente_id
                LEFT JOIN item_pedido i ON i.pedido_id = p.id
                GROUP BY p.id, c.nome, p.data_pedido, p.status, p.valor_total
                ORDER BY p.id
                """).setParameter("limit", limit).getResultList();

        List<PedidoResumoDTO> saida = new ArrayList<>(linhas.size());
        for (Object[] r : linhas) {
            saida.add(new PedidoResumoDTO(
                    ((Number) r[0]).longValue(), (String) r[1], paraDataHora(r[2]), (String) r[3],
                    paraBigDecimal(r[4]), ((Number) r[5]).longValue()));
        }
        return saida;
    }

    @SuppressWarnings("unchecked")
    public List<PedidoSimplesDTO> doCliente(long clienteId) {
        List<Object[]> linhas = em.createNativeQuery(PEDIDOS_DO_CLIENTE)
                .setParameter("clienteId", clienteId).getResultList();
        return linhas.stream().map(r -> new PedidoSimplesDTO(((Number) r[0]).longValue(),
                paraDataHora(r[1]), (String) r[2], paraBigDecimal(r[3]))).toList();
    }

    /** Examina o mesmo SQL executado pela rota de listagem. */
    @SuppressWarnings("unchecked")
    public List<String> explainPedidosDoCliente(long clienteId) {
        List<Object> linhas = em.createNativeQuery("EXPLAIN (ANALYZE, BUFFERS) " + PEDIDOS_DO_CLIENTE)
                .setParameter("clienteId", clienteId).getResultList();
        List<String> plano = new ArrayList<>();
        for (Object l : linhas) plano.add(String.valueOf(l));
        return plano;
    }

    private static LocalDateTime paraDataHora(Object o) {
        if (o instanceof Timestamp t) return t.toLocalDateTime();
        if (o instanceof LocalDateTime d) return d;
        return o == null ? null : LocalDateTime.parse(o.toString().replace(' ', 'T'));
    }

    private static BigDecimal paraBigDecimal(Object o) {
        return o == null ? null : (o instanceof BigDecimal b ? b : new BigDecimal(o.toString()));
    }
}
