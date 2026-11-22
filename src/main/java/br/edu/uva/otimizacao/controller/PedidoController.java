package br.edu.uva.otimizacao.controller;

import br.edu.uva.otimizacao.dto.PedidoDetalheDTO;
import br.edu.uva.otimizacao.dto.PedidoResumoDTO;
import br.edu.uva.otimizacao.dto.PedidoSimplesDTO;
import br.edu.uva.otimizacao.dto.ResultadoExperimento;
import br.edu.uva.otimizacao.repository.PedidoNativoRepository;
import br.edu.uva.otimizacao.service.PedidoExperimentoService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final int LIMITE_MAXIMO = 2000;

    private final PedidoExperimentoService service;
    private final PedidoNativoRepository nativo;

    public PedidoController(PedidoExperimentoService service, PedidoNativoRepository nativo) {
        this.service = service;
        this.nativo = nativo;
    }

    // Estratégias para consultar os pedidos e comparar o custo de cada uma.

    @GetMapping("/lazy")
    public ResultadoExperimento<PedidoDetalheDTO> lazy(@RequestParam(defaultValue = "50") int limit,
                                                       @RequestParam(defaultValue = "true") boolean incluirItens) {
        return service.lazy(limitar(limit), incluirItens);
    }

    @GetMapping("/eager")
    public ResultadoExperimento<PedidoDetalheDTO> eager(@RequestParam(defaultValue = "50") int limit,
                                                        @RequestParam(defaultValue = "true") boolean incluirItens) {
        return service.eager(limitar(limit), incluirItens);
    }

    @GetMapping("/join-fetch")
    public ResultadoExperimento<PedidoDetalheDTO> joinFetch(@RequestParam(defaultValue = "50") int limit) {
        return service.joinFetch(limitar(limit));
    }

    @GetMapping("/dto")
    public ResultadoExperimento<PedidoResumoDTO> dto(@RequestParam(defaultValue = "50") int limit) {
        return service.dto(limitar(limit));
    }

    @GetMapping("/dto-nativo")
    public ResultadoExperimento<PedidoResumoDTO> dtoNativo(@RequestParam(defaultValue = "50") int limit) {
        return service.dtoNativo(limitar(limit));
    }

    // Consulta usada para medir o efeito do índice composto.

    @GetMapping("/cliente/{clienteId}")
    public ResultadoExperimento<PedidoSimplesDTO> doCliente(@PathVariable Long clienteId) {
        return service.doCliente(clienteId);
    }

    /** Executa EXPLAIN (ANALYZE, BUFFERS) da consulta acima e devolve o plano de execução. */
    @GetMapping("/cliente/{clienteId}/explain")
    public List<String> explain(@PathVariable Long clienteId) {
        return nativo.explainPedidosDoCliente(clienteId);
    }

    private static int limitar(int limit) {
        return Math.max(1, Math.min(limit, LIMITE_MAXIMO));
    }
}
