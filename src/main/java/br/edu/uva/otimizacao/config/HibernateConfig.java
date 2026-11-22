package br.edu.uva.otimizacao.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateConfig {

    /** Registra o contador de queries no Hibernate. */
    @Bean
    public HibernatePropertiesCustomizer statementInspectorCustomizer() {
        return props -> props.put(AvailableSettings.STATEMENT_INSPECTOR, new QueryCountInspector());
    }
}
