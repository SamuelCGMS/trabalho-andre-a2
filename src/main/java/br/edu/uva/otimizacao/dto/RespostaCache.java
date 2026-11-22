package br.edu.uva.otimizacao.dto;

public record RespostaCache<T>(
        String origem,              // "CACHE (hit)", "BANCO (cache miss)" ou "BANCO (sem cache)"
        boolean cacheHit,
        long consultasSql,          // comandos SQL interceptados pelo Hibernate
        double tempoServicoMs,      // tempo gasto dentro do serviço
        Long ttlRestanteSegundos,   // quanto falta para a chave expirar (null se sem cache)
        T dados) { }
