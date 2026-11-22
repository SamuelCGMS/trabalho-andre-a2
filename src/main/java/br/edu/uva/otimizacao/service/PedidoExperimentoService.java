package br.edu.uva.otimizacao.service;

import br.edu.uva.otimizacao.config.QueryCountInspector;
import br.edu.uva.otimizacao.dto.*;
import br.edu.uva.otimizacao.entity.PedidoBase;
import br.edu.uva.otimizacao.entity.PedidoEager;
import br.edu.uva.otimizacao.entity.PedidoLazy;
import br.edu.uva.otimizacao.repository.PedidoEagerRepository;
import br.edu.uva.otimizacao.repository.PedidoLazyRepository;
import br.edu.uva.otimizacao.repository.PedidoNativoRepository;
import org.hibernate.Hibernate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cada metodo e uma ESTRATEGIA de carregamento. O mapeamento para DTO acontece DENTRO da transacao
 * (a sessao do Hibernate precisa estar aberta para que o LAZY consiga buscar dados sob demanda).
 */
@Service
public class PedidoExperimentoService {

    private final PedidoLazyRepository lazyRepo;
    private final PedidoEagerRepository eagerRepo;
    private final PedidoNativoRepository nativoRepo;

    public PedidoExperimentoService(PedidoLazyRepository lazyRepo, PedidoEagerRepository eagerRepo,
                                    PedidoNativoRepository nativoRepo) {
        this.lazyRepo = lazyRepo;
        this.eagerRepo = eagerRepo;
        this.nativoRepo = nativoRepo;
    }

    /** LAZY: 1 query dos pedidos + 1 por cliente + 1 por colecao de itens (problema N+1). */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoDetalheDTO> lazy(int limit, boolean incluirItens) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<PedidoLazy> pedidos = lazyRepo.primeiros(PageRequest.of(0, limit));
        return detalhes("LAZY", limit, t0, pedidos, incluirItens);
    }

    /** EAGER: o Hibernate carrega cliente e itens SEMPRE, mesmo quando o endpoint nao usa os itens. */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoDetalheDTO> eager(int limit, boolean incluirItens) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<PedidoEager> pedidos = eagerRepo.primeiros(PageRequest.of(0, limit));
        return detalhes("EAGER", limit, t0, pedidos, incluirItens);
    }

    /** OTIMIZADA 1: JOIN FETCH (2 queries: ids da pagina + pedidos com cliente e itens). */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoDetalheDTO> joinFetch(int limit) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<Long> ids = lazyRepo.primeirosIds(PageRequest.of(0, limit));
        List<PedidoLazy> pedidos = ids.isEmpty() ? List.of() : lazyRepo.comJoinFetch(ids);
        return detalhes("JOIN FETCH", limit, t0, pedidos, true);
    }

    /** OTIMIZADA 2: projecao DTO via JPQL (1 query, sem hidratar entidades). */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoResumoDTO> dto(int limit) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<PedidoResumoDTO> dados = lazyRepo.resumos(PageRequest.of(0, limit));
        return new ResultadoExperimento<>("DTO (JPQL)", limit, QueryCountInspector.total(), ms(t0),
                dados.size(), 0, dados);
    }

    /** OTIMIZADA 3: SQL nativo (1 query, mesma ideia do DTO, porem presa ao PostgreSQL). */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoResumoDTO> dtoNativo(int limit) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<PedidoResumoDTO> dados = nativoRepo.resumos(limit);
        return new ResultadoExperimento<>("DTO (SQL nativo)", limit, QueryCountInspector.total(), ms(t0),
                dados.size(), 0, dados);
    }

    /** Consulta do experimento de INDICE (WHERE cliente_id ORDER BY data_pedido DESC). */
    @Transactional(readOnly = true)
    public ResultadoExperimento<PedidoSimplesDTO> doCliente(Long clienteId) {
        long t0 = System.nanoTime();
        QueryCountInspector.reset();
        List<PedidoSimplesDTO> dados = nativoRepo.doCliente(clienteId);
        return new ResultadoExperimento<>("PEDIDOS DO CLIENTE", dados.size(), QueryCountInspector.total(), ms(t0),
                dados.size(), 0, dados);
    }

    // ---------------- mapeamento ----------------
    private ResultadoExperimento<PedidoDetalheDTO> detalhes(String estrategia, int limit, long t0,
                                                            List<? extends PedidoBase> pedidos, boolean incluirItens) {
        // O acesso a getCliente()/getItens() e o que DISPARA as queries adicionais no modo LAZY.
        List<PedidoDetalheDTO> dados = pedidos.stream().map(p -> mapear(p, incluirItens)).toList();
        long itens = pedidos.stream()
                .map(PedidoBase::getItens)
                .filter(Hibernate::isInitialized)
                .mapToLong(List::size)
                .sum();
        return new ResultadoExperimento<>(estrategia, limit, QueryCountInspector.total(), ms(t0),
                dados.size(), itens, dados);
    }

    private PedidoDetalheDTO mapear(PedidoBase p, boolean incluirItens) {
        List<ItemDTO> itens = null;
        int qtd = 0;
        if (incluirItens) {
            itens = p.getItens().stream()
                    .map(i -> new ItemDTO(i.getProdutoId(), i.getQuantidade(), i.getPrecoUnitario()))
                    .toList();
            qtd = itens.size();
        }
        return new PedidoDetalheDTO(p.getId(), p.getCliente().getNome(), p.getDataPedido(), p.getStatus(),
                p.getValorTotal(), qtd, itens);
    }

    private static double ms(long inicioNanos) {
        return Math.round((System.nanoTime() - inicioNanos) / 1_000.0) / 1000.0;
    }
}
