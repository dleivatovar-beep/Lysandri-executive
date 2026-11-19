package com.lysandri.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DisponibilidadPagos {
    private final Environment environment;

    public DisponibilidadPagos(Environment environment) {
        this.environment = environment;
    }

    public boolean izipayDisponible() {
        return valida("shop-id", "IZIPAY_SHOP_ID", "test_shop")
                && valida("api-key", "IZIPAY_API_KEY", "test_password")
                && valida("public-key", "IZIPAY_PUBLIC_KEY", "test_public")
                && valida("sha256-key", "IZIPAY_SHA256_KEY", "test_hmac");
    }

    private boolean valida(String propiedad, String variable, String ejemplo) {
        String valor = environment.getProperty("izipay." + propiedad,
                environment.getProperty(variable, ""));
        return !valor.isBlank() && !valor.startsWith(ejemplo);
    }
}
