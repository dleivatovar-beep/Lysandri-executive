package com.lysandri.adapters.out.billing;

import com.lysandri.domain.ports.out.CorrelativoComprobantePort;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CorrelativoComprobanteRepositoryAdapter implements CorrelativoComprobantePort {

    private final EntityManager entityManager;

    @Override
    @Transactional
    public int reservarSiguiente(String serie) {
        Object result = entityManager.createNativeQuery("""
                INSERT INTO comprobante_correlativo (serie, ultimo_correlativo)
                VALUES (:serie, 1)
                ON CONFLICT (serie) DO UPDATE
                SET ultimo_correlativo = comprobante_correlativo.ultimo_correlativo + 1
                RETURNING ultimo_correlativo
                """)
                .setParameter("serie", serie)
                .getSingleResult();
        return ((Number) result).intValue();
    }
}
