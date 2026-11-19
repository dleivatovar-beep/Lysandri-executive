package com.lysandri.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lysandri.adapters.in.web.ComprasPruebaController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.assertThat;

class PruebasConfiguracionTest {
    ApplicationContextRunner contexto(String... perfiles) {
        return new ApplicationContextRunner().withInitializer(c->c.getEnvironment().setActiveProfiles(perfiles))
            .withUserConfiguration(ComprasPruebaService.class,ComprasPruebaController.class,
                CobrosPruebaService.class,EmpresasPruebaService.class,com.lysandri.adapters.in.web.DemostracionController.class)
            .withBean(ObjectMapper.class,()->new ObjectMapper().findAndRegisterModules())
            .withBean(JdbcTemplate.class,()->new JdbcTemplate(new DriverManagerDataSource()))
            .withBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(new DriverManagerDataSource()));
    }
    @Test void produccionNoCargaElModuloAunqueSeHabiliteLaPropiedad() {
        for(String produccion:new String[]{"prod","production"}) contexto(produccion,"test","sandbox").withPropertyValues("purchase.sandbox.enabled=true").run(c->{
                assertThat(c).doesNotHaveBean(CobrosPruebaService.class)
                        .doesNotHaveBean(EmpresasPruebaService.class)
                        .doesNotHaveBean(com.lysandri.adapters.in.web.DemostracionController.class);
                assertThat(c).doesNotHaveBean(ComprasPruebaService.class);assertThat(c).doesNotHaveBean(ComprasPruebaController.class);
            });
    }
    @Test void perfilDePruebaSinHabilitacionNoExponeEndpoints() {
        contexto("test","sandbox").run(c->assertThat(c).doesNotHaveBean(ComprasPruebaController.class));
    }
    @Test void desarrolloConSandboxYPropiedadHabilitadaCargaElModulo() {
        contexto("original-dev","sandbox").withPropertyValues("purchase.sandbox.enabled=true").run(c->{
            assertThat(c).hasSingleBean(CobrosPruebaService.class)
                    .hasSingleBean(EmpresasPruebaService.class)
                    .hasSingleBean(com.lysandri.adapters.in.web.DemostracionController.class);
            assertThat(c).hasSingleBean(ComprasPruebaService.class);assertThat(c).hasSingleBean(ComprasPruebaController.class);
        });
    }
}
