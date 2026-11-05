import assert from 'node:assert';
import test from 'node:test';

// Import built storeService from dist or directly test logic
test('Pruebas unitarias de storeService: no simular estados ni fabricar respuestas', async (t) => {
  const { storeService } = await import('./src/services/storeService.ts');

  await t.test('obtenerCuentasBancarias y obtenerOrdenPorCodigo estan definidos', () => {
    assert.strictEqual(typeof storeService.obtenerCuentasBancarias, 'function');
    assert.strictEqual(typeof storeService.obtenerOrdenPorCodigo, 'function');
    assert.strictEqual(typeof storeService.confirmarPagoManual, 'function');
    assert.strictEqual(typeof storeService.createCheckoutSession, 'function');
  });

  await t.test('createCheckoutSession propaga error de red sin inventar orden ni sesion', async () => {
    let errorLanzado = false;
    try {
      // Intentar llamar contra endpoint local no disponible
      await storeService.createCheckoutSession({
        programaIds: [999],
        email: 'test@empresa.com',
        nombreCompleto: 'Test User',
        tipoComprobante: 'BOLETA',
        numeroDocumento: '12345678',
        metodoPago: 'TRANSFERENCIA',
      });
    } catch (err) {
      errorLanzado = true;
      assert.ok(err.message, 'El error debe contener mensaje descriptivo');
      assert.ok(
        !err.sessionId,
        'No debe fabricar sessionId ficticio'
      );
    }
    assert.ok(errorLanzado, 'createCheckoutSession debe lanzar excepción cuando el backend no está disponible');
  });

  await t.test('confirmarPagoManual propaga error de red sin simular orden PAGADO ni matricula', async () => {
    let errorLanzado = false;
    try {
      await storeService.confirmarPagoManual(9999, {
        numeroOperacion: 'OP-TEST-1',
        bancoDestino: 'BCP',
        cuentaDestino: '193-000',
        montoVerificado: 899.0,
        monedaVerificada: 'USD',
        fechaAbono: '2026-10-28T12:00:00Z',
      });
    } catch (err) {
      errorLanzado = true;
      assert.ok(err.message, 'Debe lanzar excepción de red o backend');
    }
    assert.ok(errorLanzado, 'confirmarPagoManual debe fallar si el servidor no responde');
  });

  await t.test('listarOrdenes no retorna ordenes falsas cableadas ante error', async () => {
    let errorLanzado = false;
    try {
      await storeService.listarOrdenes();
    } catch (err) {
      errorLanzado = true;
      assert.ok(err.message, 'Debe propagar error de red');
    }
    assert.ok(errorLanzado, 'listarOrdenes no debe retornar ordenes mock ficticias');
  });
});

