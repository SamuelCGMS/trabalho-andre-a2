package br.edu.uva.otimizacao.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Conta os comandos SQL interceptados pelo Hibernate na thread da operação medida.
 */
public class QueryCountInspector implements StatementInspector {

    private static final ThreadLocal<int[]> CONTADOR = ThreadLocal.withInitial(() -> new int[1]);

    public static void reset() {
        CONTADOR.get()[0] = 0;
    }

    public static long total() {
        return CONTADOR.get()[0];
    }

    @Override
    public String inspect(String sql) {
        CONTADOR.get()[0]++;
        return sql;
    }
}
