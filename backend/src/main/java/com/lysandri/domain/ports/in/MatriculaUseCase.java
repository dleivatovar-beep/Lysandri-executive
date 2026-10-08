package com.lysandri.domain.ports.in;

/**
 * Puerto de entrada del dominio de negocio: Gestión de Matrícula Ejecutiva y Emisión de Órdenes.
 * Proporciona nombres reales del negocio corporativo de Lysandri Executive.
 */
public interface MatriculaUseCase extends BuyCourseUseCase {
    // Hereda las operaciones de orquestación de matrícula corporativa,
    // provisioning en campus virtual Moodle y facturación electrónica SUNAT.
}
