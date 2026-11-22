package br.edu.uva.otimizacao.dto;

import java.util.List;

public record ResultadoExperimento<T>(
        String estrategia,
        int limit,
        long consultasSql,          // comandos interceptados pelo Hibernate
        double tempoServicoMs,
        int registrosRetornados,
        long itensHidratados,       // itens em coleções inicializadas, inclusive os omitidos do JSON
        List<T> dados) { }
