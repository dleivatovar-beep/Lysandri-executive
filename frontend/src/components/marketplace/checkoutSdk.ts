export type ScriptLoadStatus = 'IDLE' | 'LOADING' | 'LOADED' | 'FAILED';

let kryptonScriptStatus: ScriptLoadStatus = 'IDLE';
let kryptonScriptPromise: Promise<any> | null = null;
let activeSdkOperationPromise: Promise<any> = Promise.resolve();

export interface GlobalSdkOwnership {
  activeRequestedSessionId: string | null;
  activeOwnerSessionId: string | null;
  ownerInstanceId: string | null;
}

export const getGlobalSdkOwnership = (): GlobalSdkOwnership => {
  if (typeof window !== 'undefined') {
    if (!(window as any).__krSdkOwnership) {
      (window as any).__krSdkOwnership = {
        activeRequestedSessionId: null,
        activeOwnerSessionId: null,
        ownerInstanceId: null,
      };
    }
    return (window as any).__krSdkOwnership;
  }
  return { activeRequestedSessionId: null, activeOwnerSessionId: null, ownerInstanceId: null };
};

export const resetGlobalSdkOwnership = (): void => {
  const state = getGlobalSdkOwnership();
  state.activeRequestedSessionId = null;
  state.activeOwnerSessionId = null;
  state.ownerInstanceId = null;
};

export const getKryptonScriptStatus = (): ScriptLoadStatus => kryptonScriptStatus;

export const resetKryptonScriptLoader = (): void => {
  kryptonScriptStatus = 'IDLE';
  kryptonScriptPromise = null;
  document.getElementById('izipay-krypton-client-sdk')?.remove();
};

export const loadKryptonClientScript = (publicKey: string): Promise<any> => {
  if (typeof window === 'undefined') return Promise.resolve(null);
  if ((window as any).KR) {
    kryptonScriptStatus = 'LOADED';
    kryptonScriptPromise = null;
    return Promise.resolve((window as any).KR);
  }
  if (kryptonScriptStatus === 'LOADING' && kryptonScriptPromise) return kryptonScriptPromise;

  document.getElementById('izipay-krypton-client-sdk')?.remove();
  kryptonScriptStatus = 'LOADING';
  kryptonScriptPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.id = 'izipay-krypton-client-sdk';
    script.src = 'https://static.micuentaweb.pe/static/js/krypton-client/V4.0/stable/kr-payment-form.min.js';
    script.setAttribute('kr-public-key', publicKey);
    script.setAttribute('kr-post-url-success', '');
    script.setAttribute('kr-spa-mode', 'true');
    script.onload = () => {
      kryptonScriptStatus = 'LOADED';
      kryptonScriptPromise = null;
      resolve((window as any).KR);
    };
    script.onerror = () => {
      kryptonScriptStatus = 'FAILED';
      kryptonScriptPromise = null;
      script.remove();
      reject(new Error('Error de conexión al cargar la pasarela segura izipay. Por favor verifique su conexión e intente nuevamente.'));
    };
    document.head.appendChild(script);
  });
  return kryptonScriptPromise;
};

export function enqueueSdkOperation<T>(operation: () => Promise<T>): Promise<T> {
  const next = activeSdkOperationPromise.catch(() => {}).then(operation);
  activeSdkOperationPromise = next.then(() => {}, () => {});
  return next;
}
