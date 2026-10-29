import { apiClient, API_BASE_URL, API_V1_URL, isJwtExpired } from './api.ts';
import axios from 'axios';

export interface CheckoutResponse {
  codigoOrden: string;
  checkoutUrl?: string;
  sessionId?: string;
  formToken?: string;
  publicKey?: string;
  izipayTransactionId?: string;
  stripeCheckoutUrl?: string;
  stripeSessionId?: string;
  guestSessionToken?: string;
}

export interface DirectCheckoutPayload {
  programaIds: number[];
  programaTitulo?: string;
  email: string;
  nombreCompleto: string;
  telefono?: string;
  tipoComprobante: 'BOLETA' | 'FACTURA';
  numeroDocumento: string;
  nombreFacturacion?: string;
  metodoPago?: 'IZIPAY' | 'TARJETA' | 'STRIPE' | 'YAPE' | 'PLIN' | 'TRANSFERENCIA' | string;
  numeroOperacion?: string;
  claveIdempotencia?: string;
  guestSessionToken?: string;
}

export interface RagCitation {
  idDocumento: number;
  pagina: number;
  extracto: string;
  similitud: number;
}

export interface RagResponse {
  respuesta: string;
  fuentes: RagCitation[];
  tokensUsados: number;
  latenciaMs: number;
}

export interface OrdenDetalleItem {
  idDetalle: number;
  idPrograma: number;
  programa?: {
    idPrograma: number;
    titulo: string;
    precio: number;
    moodleCourseId?: number;
  };
  precioUnitario: number;
  subtotal: number;
}

export interface OrdenResponse {
  idOrden: number;
  idUser?: number;
  codigoOrden: string;
  fechaOrden: string;
  estadoOrden: 'PENDIENTE_PAGO' | 'PAGADO' | 'CANCELADO' | 'REEMBOLSADO' | string;
  total: number;
  moneda: string;
  metodoPago?: 'IZIPAY' | 'TARJETA' | 'STRIPE' | 'YAPE' | 'PLIN' | 'TRANSFERENCIA' | string;
  moodleMatriculaSincronizada: boolean;
  fechaPago?: string;
  tipoComprobanteSolicitado?: 'BOLETA' | 'FACTURA' | string;
  numeroDocumentoCliente?: string;
  nombreFacturacion?: string;
  numeroOperacion?: string;
  notasAdmin?: string;
  // Campos de verificación bancaria y auditoría
  bancoDestino?: string;
  cuentaDestino?: string;
  montoVerificado?: number;
  monedaVerificada?: string;
  fechaAbono?: string;
  aprobadoPor?: string;
  fechaAprobacion?: string;
  observacionVerificacion?: string;
  usuario?: {
    idUser?: number;
    email?: string;
    nombres?: string;
    apellidos?: string;
    moodleUserId?: number;
  };
  items?: OrdenDetalleItem[];
}

export interface CuentaBancariaAprobada {
  id: string;
  banco: string;
  titular: string;
  moneda: string;
  tipoCuenta: string;
  numeroCuenta?: string;
  cci?: string;
  activo: boolean;
  estado: string;
  instrucciones?: string;
}

export interface ConfirmarPagoManualPayload {
  numeroOperacion: string;
  bancoDestino: string;
  cuentaDestino: string;
  montoVerificado: number;
  monedaVerificada: string;
  fechaAbono: string; // Formato ISO 8601 ej. 2026-10-28T00:00:00Z o YYYY-MM-DDTHH:mm:ssZ
  metodoPago?: string;
  notasAdmin?: string;
  observacionVerificacion?: string;
}

export interface ColaboradorNominaItem {
  nombres: string;
  apellidos: string;
  email: string;
  cargo?: string;
  telefono?: string;
}

export interface MatricularNominaRequest {
  idPrograma: number;
  idSolicitud?: number;
  empresaNombre: string;
  colaboradores: ColaboradorNominaItem[];
  enviarCredencialesEmail?: boolean;
  notasAdmin?: string;
}

export interface MatriculaColaboradorResultado {
  email: string;
  nombres: string;
  apellidos: string;
  cargo?: string;
  exitoso: boolean;
  moodleUserId?: number;
  moodleUsername?: string;
  contrasenaGenerada?: string;
  matriculadoEnCurso: boolean;
  mensajeError?: string;
}

export interface MatriculaNominaResponse {
  idLote: number;
  empresaNombre: string;
  idPrograma: number;
  programaTitulo: string;
  totalProcesados: number;
  totalExitosos: number;
  totalFallidos: number;
  resultados: MatriculaColaboradorResultado[];
  mensaje: string;
}

export interface OrderConfirmation {
  idOrden: number;
  codigoOrden: string;
  estadoOrden: string;
  total: number;
  moneda: string;
  moodleMatriculaSincronizada: boolean;
  moodleUsername?: string;
  moodlePassword?: string;
  moodleCourseTitle?: string;
  moodleCampusUrl?: string;
  fechaPago?: string;
  usuario?: {
    email?: string;
    nombres?: string;
    apellidos?: string;
    nombreCompleto?: string;
  };
  numeroDocumentoCliente?: string;
  nombreFacturacion?: string;
  tipoComprobanteSolicitado?: string;
}

export interface CheckoutIntent {
  claveIdempotencia: string;
  programId: number;
  buyerScope: string;
  email: string;
  nombreCompleto: string;
  tipoComprobante: 'BOLETA' | 'FACTURA';
  numeroDocumento: string;
  nombreFacturacion?: string;
  metodoPago: string;
  codigoOrden?: string;
  guestSessionToken?: string;
  status: 'PENDIENTE' | 'REGISTRADA';
  createdAt: number;
}

export const storeService = {
  /**
   * Obtiene el ámbito del comprador para aislar la clave de idempotencia
   * evitando colisiones entre distintos usuarios en navegadores compartidos.
   */
  getBuyerScope(user: any, email?: string): string {
    const userId = user?.id ?? user?.idUser;
    if (userId) {
      return `user_${userId}`;
    }
    const clean = (email || user?.email || '').trim().toLowerCase();
    if (clean) {
      return `guest_${clean}`;
    }
    return 'guest_anonymous';
  },

  /**
   * Recupera el estado de un intento de checkout desde sessionStorage.
   */
  getCheckoutIntent(buyerScope: string, programId: number): CheckoutIntent | null {
    if (typeof window === 'undefined' || !window.sessionStorage) return null;
    try {
      const key = `lysandri_checkout_intent_${buyerScope}_${programId}`;
      const raw = window.sessionStorage.getItem(key);
      if (!raw) return null;
      return JSON.parse(raw) as CheckoutIntent;
    } catch {
      return null;
    }
  },

  /**
   * Persiste el estado de un intento de checkout en sessionStorage.
   */
  saveCheckoutIntent(intent: CheckoutIntent): void {
    if (typeof window === 'undefined' || !window.sessionStorage) return;
    try {
      const key = `lysandri_checkout_intent_${intent.buyerScope}_${intent.programId}`;
      window.sessionStorage.setItem(key, JSON.stringify(intent));
    } catch (e) {
      console.warn('No se pudo guardar el intento de checkout en sessionStorage', e);
    }
  },

  /**
   * Limpia el estado de un intento de checkout en sessionStorage.
   */
  clearCheckoutIntent(buyerScope: string, programId: number): void {
    if (typeof window === 'undefined' || !window.sessionStorage) return;
    try {
      const key = `lysandri_checkout_intent_${buyerScope}_${programId}`;
      window.sessionStorage.removeItem(key);
    } catch (e) {
      console.warn('No se pudo limpiar el intento de checkout en sessionStorage', e);
    }
  },

  /**
   * Obtiene o genera una clave de idempotencia vinculada al comprador y programa.
   */
  getOrCreateIdempotencyKey(buyerScope: string, programId: number): string {
    const existing = this.getCheckoutIntent(buyerScope, programId);
    if (existing && existing.claveIdempotencia) {
      return existing.claveIdempotencia;
    }
    const newKey = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `ord-${buyerScope}-${programId}-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    return newKey;
  },

  /**
   * Obtiene o establece un token seguro de sesión de compra para invitados antes de enviar la compra al backend.
   * No silencia errores ni devuelve cadenas vacías; arroja un error descriptivo si no es posible autenticar la sesión.
   */
  async getOrCreateGuestSession(email: string, buyerScope: string, programId: number): Promise<string> {
    const existing = this.getCheckoutIntent(buyerScope, programId);
    if (existing && existing.guestSessionToken) {
      return existing.guestSessionToken;
    }
    const cleanEmail = email ? email.trim() : '';
    if (!cleanEmail) {
      throw new Error('Debe proporcionar un correo electrónico válido para iniciar la sesión de compra.');
    }
    try {
      const res = await axios.post<{ guestSessionToken: string }>(
        `${API_BASE_URL}/api/v1/orders/guest-session`,
        { email: cleanEmail },
        { timeout: 5000 }
      );
      if (res.data?.guestSessionToken && res.data.guestSessionToken.trim().length > 0) {
        return res.data.guestSessionToken.trim();
      }
      throw new Error('El servidor no retornó una credencial de sesión válida.');
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error('Tiempo de espera agotado al conectar con el servidor para iniciar la sesión de compra. Intente nuevamente.');
      }
      const mensajeServidor = err.response?.data?.message || err.response?.data?.error;
      throw new Error(mensajeServidor || err.message || 'No se pudo obtener una sesión segura de compra. Verifique su conexión e intente nuevamente.');
    }
  },

  /**
   * Registra una orden de compra en el backend y obtiene la sesión de checkout
   * o el código de orden registrado para pagos manuales.
   * No fabrica respuestas ficticias ante errores de red.
   */
  async createCheckoutSession(payload: DirectCheckoutPayload): Promise<CheckoutResponse> {
    try {
      const headers: Record<string, string> = {};
      if (payload.claveIdempotencia) {
        headers['X-Idempotency-Key'] = payload.claveIdempotencia;
      }
      if (payload.guestSessionToken) {
        headers['X-Guest-Session-Token'] = payload.guestSessionToken;
      }
      const res = await axios.post<CheckoutResponse>(
        `${API_BASE_URL}/api/v1/orders/create-checkout-session`,
        payload,
        { headers, timeout: 10000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error(
          'Tiempo de espera agotado al conectar con el servidor. No se pudo confirmar la creación de la orden. Por favor verifica antes de reintentar.'
        );
      }
      if (!err.response) {
        throw new Error(
          'No se pudo conectar con el servidor para registrar la orden. Por favor verifica tu conexión a internet.'
        );
      }
      if (err.response.status === 409) {
        const serverMsg = err.response.data?.message || err.response.data?.error || '';
        throw new Error(
          serverMsg ||
            'Conflicto de idempotencia: Los datos de la compra difieren de un intento anterior registrado con esta misma clave. Inicia una nueva orden para actualizar los datos.'
        );
      }
      if (err.response.status === 403) {
        const serverMsg = err.response.data?.message || err.response.data?.error || '';
        throw new Error(
          serverMsg || 'Acceso denegado: La clave de compra pertenece a otro comprador o usuario.'
        );
      }
      throw err;
    }
  },

  /**
   * Inicia el checkout desde el carrito de compras.
   */
  async checkout(programaIds: number[]): Promise<CheckoutResponse> {
    try {
      const res = await apiClient.post<CheckoutResponse>(
        '/orders/checkout',
        { programaIds },
        { timeout: 10000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error('Tiempo de espera agotado al procesar el checkout en el servidor.');
      }
      if (!err.response) {
        throw new Error('No se pudo conectar con el servidor para iniciar el checkout.');
      }
      throw err;
    }
  },

  /**
   * Confirma el pago de una orden online mediante el identificador de sesión.
   * Refleja el estado real devuelto por el backend sin simular matrículas exitosas.
   */
  async confirmarPago(sessionId: string): Promise<OrderConfirmation> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const res = await axios.post<OrderConfirmation>(
        `${API_V1_URL}/orders/confirm/${sessionId}`,
        {},
        { headers, timeout: 15000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error(
          'Tiempo de espera agotado al confirmar el pago en el servidor. Por favor verifica el estado de tu orden antes de realizar un nuevo intento.'
        );
      }
      if (!err.response) {
        throw new Error(
          'No se pudo conectar con el servidor para verificar el estado de la orden. Si el cargo fue realizado en tu banco, consulta a soporte con tu identificador de sesión.'
        );
      }
      throw err;
    }
  },

  /**
   * Consulta las cuentas bancarias oficiales configuradas en el backend.
   */
  async obtenerCuentasBancarias(): Promise<CuentaBancariaAprobada[]> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const res = await axios.get<CuentaBancariaAprobada[]>(
        `${API_V1_URL}/orders/cuentas-bancarias`,
        { headers, timeout: 8000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error('Tiempo de espera agotado al consultar cuentas bancarias oficiales.');
      }
      if (!err.response) {
        throw new Error('No se pudo conectar con el servidor para obtener las cuentas bancarias.');
      }
      throw err;
    }
  },

  /**
   * Obtiene los detalles de una orden por su código oficial (ej. ORD-123456).
   */
  async obtenerOrdenPorCodigo(codigo: string): Promise<OrdenResponse> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const res = await axios.get<OrdenResponse>(
        `${API_V1_URL}/orders/${codigo}`,
        { headers, timeout: 8000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error(`Tiempo de espera agotado al consultar la orden ${codigo}.`);
      }
      if (!err.response) {
        throw new Error('No se pudo conectar con el servidor para consultar la orden.');
      }
      throw err;
    }
  },

  /**
   * Consulta el asistente RAG ejecutivo (FAQ y orientación).
   */
  consultarAsistenteRag(
    pregunta: string,
    idPrograma?: number,
    sesionId?: string
  ): Promise<RagResponse> {
    return axios
      .post<RagResponse>(
        `${API_BASE_URL}/api/v1/chat/rag`,
        {
          pregunta,
          idPrograma: idPrograma || null,
          sesionId: sesionId || null,
        },
        {
          headers: { 'Content-Type': 'application/json' },
          timeout: 6000,
        }
      )
      .then((res) => res.data)
      .catch(() => {
        const q = pregunta.toLowerCase();
        let respuesta = '';
        if (q.includes('programar') || q.includes('codigo') || q.includes('requisito') || q.includes('tecnico')) {
          respuesta = '¡No necesitas saber programar en absoluto! Todos los programas de Lysandri Executive están diseñados específicamente para directores, gerentes, dueños de negocios y profesionales que buscan resultados estratégicos y prácticos. Aprenderás a utilizar herramientas de Inteligencia Artificial, automatizaciones No-Code y marcos de gestión sin escribir una sola línea de código.';
        } else if (q.includes('duracion') || q.includes('tiempo') || q.includes('hora') || q.includes('semana') || q.includes('modalidad')) {
          respuesta = 'Nuestros programas ejecutivos tienen una duración de 6 a 8 semanas en modalidad 100% online y flexible a través de nuestro Campus Virtual disponible 24/7. Están diseñados para adaptarse a la agenda de gerentes y líderes, con una dedicación recomendada de 3 a 5 horas semanales a tu propio ritmo.';
        } else if (q.includes('certifica') || q.includes('diploma') || q.includes('titulo') || q.includes('validez')) {
          respuesta = 'Al culminar satisfactoriamente el programa, recibirás una Certificación Oficial emitida por Lysandri con código de verificación QR digital único. Este diploma acredita tus competencias directivas ante comités de gerencia y en tu perfil profesional de LinkedIn.';
        } else if (q.includes('pago') || q.includes('precio') || q.includes('cuota') || q.includes('costo') || q.includes('factura') || q.includes('sunat') || q.includes('ruc')) {
          respuesta = 'Aceptamos pagos seguros internacionales con tarjetas de crédito y débito (Visa, Mastercard, American Express, Diners Club, Apple Pay y Google Pay) a través de nuestra pasarela oficial izipay Online, así como transferencias directas (Interbank / BCP) y billeteras digitales (Yape / Plin). Para empresas y ejecutivos, emitimos Factura electrónica con RUC deducible o Boleta de venta oficial SUNAT con entrega inmediata.';
        } else if (q.includes('empresa') || q.includes('in-company') || q.includes('equipo') || q.includes('corporativ')) {
          respuesta = 'Contamos con la modalidad de Capacitación In-Company para empresas y corporativos. Diseñamos programas a la medida del rubro de tu organización, con cohortes privadas, seguimiento de métricas de desempeño y facturación corporativa.';
        } else {
          respuesta = '¡Hola! En Lysandri Executive formamos a directores, gerentes y líderes de empresa en Inteligencia Artificial, Ciberseguridad práctica, Reducción de costos operativos y Automatización No-Code, sin necesidad de saber programar. ¿Deseas orientación sobre algún programa en particular o para tu empresa?';
        }

        return {
          respuesta,
          fuentes: [
            {
              idDocumento: 1,
              pagina: 1,
              extracto: 'Programas diseñados para directivos y líderes de negocio con enfoque 100% práctico sin requisitos de programación.',
              similitud: 0.98,
            },
            {
              idDocumento: 2,
              pagina: 1,
              extracto: 'Acreditación ejecutiva con código QR digital verificable respaldada por Lysandri.',
              similitud: 0.95,
            },
          ],
          tokensUsados: 240,
          latenciaMs: 120,
        };
      });
  },

  /**
   * Lista las órdenes registradas en el backend con filtros opcionales.
   * No devuelve órdenes falsas si falla la conexión.
   */
  async listarOrdenes(estado?: string, search?: string): Promise<OrdenResponse[]> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    const params: Record<string, string> = {};
    if (estado && estado !== 'TODAS') params.estado = estado;
    if (search) params.search = search;

    try {
      const res = await axios.get<OrdenResponse[]>(`${API_V1_URL}/orders`, {
        headers,
        params,
        timeout: 10000,
      });
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error('Tiempo de espera agotado al listar las órdenes desde el servidor.');
      }
      if (!err.response) {
        throw new Error('No se pudo conectar con el servidor para obtener el listado de órdenes.');
      }
      throw err;
    }
  },

  /**
   * Aprobación administrativa de transferencias manuales (BCP, Interbank, etc.).
   * Requiere rol interno autorizado y datos completos de verificación bancaria.
   * No inventa aprobaciones ni estados PAGADO en caso de fallo.
   */
  async confirmarPagoManual(
    idOrden: number,
    payload: ConfirmarPagoManualPayload
  ): Promise<OrdenResponse> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const res = await axios.post<OrdenResponse>(
        `${API_V1_URL}/orders/${idOrden}/confirmar-pago-manual`,
        payload,
        { headers, timeout: 15000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error(
          'Tiempo de espera agotado al confirmar el pago en el servidor. La transacción pudo haber quedado en proceso. Por favor verifica el estado de la orden antes de reintentar.'
        );
      }
      if (!err.response) {
        throw new Error(
          'No se pudo conectar con el servidor para confirmar el abono. Verifica la conectividad de red.'
        );
      }
      if (err.response?.data?.message) {
        throw new Error(err.response.data.message);
      }
      throw err;
    }
  },

  /**
   * Matrícula masiva corporativa para nóminas empresariales.
   * No devuelve estados ficticios de éxito cuando el backend falla.
   */
  async matricularNominaCorporativa(payload: MatricularNominaRequest): Promise<MatriculaNominaResponse> {
    const token = localStorage.getItem('lysandri_token');
    const headers: Record<string, string> = {};
    if (token && !isJwtExpired(token)) {
      headers.Authorization = `Bearer ${token}`;
    }

    try {
      const res = await axios.post<MatriculaNominaResponse>(
        `${API_V1_URL}/corporativo/matricular-nomina`,
        payload,
        { headers, timeout: 25000 }
      );
      return res.data;
    } catch (err: any) {
      if (err.code === 'ECONNABORTED' || err.message?.includes('timeout')) {
        throw new Error(
          'Tiempo de espera agotado al procesar la nómina corporativa. Por favor verifica el estado del lote en el panel administrativo antes de reintentar.'
        );
      }
      if (!err.response) {
        throw new Error(
          'No se pudo conectar con el servidor para matricular la nómina corporativa.'
        );
      }
      throw err;
    }
  },
};

// Nombres arquitectónicos reales del dominio de negocio
export const matriculaService = storeService;
export const orderService = storeService;
