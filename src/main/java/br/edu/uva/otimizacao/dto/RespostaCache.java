package br.edu.uva.otimizacao.dto;

public record RespostaCache<T>(
        String origem,              // "CACHE (hit)", "BANCO (cache miss)" ou "BANCO (sem cache)"
        boolean cacheHit,
        long consultasSql,          // quantas queries SQL esta requisicao executou
        double tempoServicoMs,      // tempo gasto dentro do servico
        Long ttlRestanteSegundos,   // quanto falta para a chave expirar (null se sem cache)
        T dados) { }
