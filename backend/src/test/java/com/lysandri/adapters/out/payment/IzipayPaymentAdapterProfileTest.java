package com.lysandri.adapters.out.payment;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IzipayPaymentAdapterProfileTest {

    @Test
    void cloneProfileUsesConfiguredIzipayAdapter() {
        try (AnnotationConfigApplicationContext context = contextWithProfiles("clone")) {
            assertInstanceOf(IzipayPaymentAdapter.class, context.getBean("izipayPaymentAdapter"));
            assertInstanceOf(StripePaymentAdapter.class, context.getBean("stripePaymentAdapter"));
        }
    }

    @Test
    void testProfileKeepsMockAdaptersForAutomatedTests() {
        try (AnnotationConfigApplicationContext context = contextWithProfiles("test")) {
            assertInstanceOf(MockIzipayPaymentAdapter.class, context.getBean("izipayPaymentAdapter"));
            assertInstanceOf(MockStripePaymentAdapter.class, context.getBean("stripePaymentAdapter"));
        }
    }

    private AnnotationConfigApplicationContext contextWithProfiles(String... profiles) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profiles);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "izipay.shop-id", "configured-test-shop",
                "izipay.api-key", "configured-test-api-key",
                "izipay.public-key", "configured-test-public-key",
                "izipay.sha256-key", "configured-test-sha-key"
        )));
        context.register(
                IzipayPaymentAdapter.class,
                MockIzipayPaymentAdapter.class,
                StripePaymentAdapter.class,
                MockStripePaymentAdapter.class
        );
        context.refresh();
        return context;
    }
}
