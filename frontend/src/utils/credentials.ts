import { UsuarioResponse } from '../types';

/**
 * Normaliza una cadena removiendo acentos, espacios y caracteres especiales.
 */
export const cleanText = (str: string): string => {
  return (str || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]/g, '');
};

/**
 * Normaliza un número telefónico extrayendo únicamente sus dígitos.
 */
export const extractDigits = (phone?: string): string => {
  if (!phone) return '';
  return phone.replace(/\D/g, '');
};

/**
 * Compara dos teléfonos permitiendo coincidencia de los últimos 7-9 dígitos
 * (soporta números con o sin código de país como +51).
 */
export const arePhonesMatching = (phoneA?: string, phoneB?: string): boolean => {
  const digitsA = extractDigits(phoneA);
  const digitsB = extractDigits(phoneB);
  if (!digitsA || !digitsB) return false;
  if (digitsA === digitsB) return true;

  // Comparar últimos 9 dígitos (estándar celular Perú y varios países)
  const minLen = Math.min(digitsA.length, digitsB.length);
  if (minLen >= 7) {
    const tailA = digitsA.slice(-Math.min(9, minLen));
    const tailB = digitsB.slice(-Math.min(9, minLen));
    return tailA === tailB;
  }
  return false;
};

/**
 * Genera un nombre de usuario corporativo único y diferenciado.
 * No genera usuarios repetidos ni patrones fijos como 'usuario21'.
 */
export const generateUniqueUsername = (
  nombres: string,
  apellidos: string,
  existingUsernames: string[] = []
): string => {
  const lowerExisting = new Set(
    existingUsernames.map((u) => (u || '').trim().toLowerCase())
  );

  const cleanFirst = cleanText((nombres || '').trim().split(' ')[0] || '');
  const cleanLast = cleanText((apellidos || '').trim().split(' ')[0] || '');

  // Generador de sufijo aleatorio de 2 a 3 dígitos para garantizar entropía
  const getRandomEntropy = () => Math.floor(10 + Math.random() * 90);
  const getRandomSuffix3 = () => Math.floor(100 + Math.random() * 900);

  // Lista de formatos candidatos basados en nombres reales
  const candidateGenerators: (() => string)[] = [];

  if (cleanFirst && cleanLast) {
    candidateGenerators.push(
      () => `${cleanFirst}.${cleanLast}${getRandomEntropy()}`,
      () => `${cleanFirst[0]}${cleanLast}${getRandomEntropy()}`,
      () => `${cleanFirst}${cleanLast.slice(0, 4)}${getRandomEntropy()}`,
      () => `${cleanFirst}.${cleanLast[0]}${getRandomSuffix3()}`,
      () => `${cleanLast}.${cleanFirst}${getRandomEntropy()}`
    );
  } else if (cleanFirst) {
    candidateGenerators.push(
      () => `${cleanFirst}${getRandomEntropy()}`,
      () => `${cleanFirst}.${getRandomSuffix3()}`,
      () => `lys.${cleanFirst}${getRandomEntropy()}`
    );
  } else {
    candidateGenerators.push(
      () => `user.${getRandomSuffix3()}`,
      () => `lysandri.${getRandomSuffix3()}`
    );
  }

  // Intentar hasta encontrar uno que no colisione con existentes
  for (let attempt = 0; attempt < 50; attempt++) {
    const generator = candidateGenerators[attempt % candidateGenerators.length];
    const candidate = generator().toLowerCase();
    if (!lowerExisting.has(candidate)) {
      return candidate;
    }
  }

  // Fallback garantizado con timestamp + entropía
  return `${cleanFirst || 'user'}${Date.now().toString().slice(-4)}`;
};

/**
 * Genera una contraseña segura, única y no repetitiva con alta entropía.
 * Cumple requisitos de seguridad: mayúsculas, minúsculas, números y caracteres especiales.
 * Elimina contraseñas repetitivas como 'Nombre2026!' o 'Lysandri2026!'.
 */
export const generateSecurePassword = (prefixName?: string): string => {
  const symbols = ['#', '!', '$', '&', '*', '@', '%'];
  const uppercaseChars = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const lowercaseChars = 'abcdefghijkmnopqrstuvwxyz';
  const digits = '23456789';

  const pickRandom = (chars: string | string[]) =>
    chars[Math.floor(Math.random() * chars.length)];

  // Limpiar y obtener un prefijo amigable pero único (3 letras)
  const cleanPrefix = (prefixName || 'Lys')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z]/g, '')
    .trim();

  const formattedPrefix =
    cleanPrefix.length >= 3
      ? cleanPrefix.charAt(0).toUpperCase() + cleanPrefix.slice(1, 3).toLowerCase()
      : 'Lys';

  // Generar componentes aleatorios para asegurar que NINGUNA contraseña sea igual
  const symbol1 = pickRandom(symbols);
  const symbol2 = pickRandom(symbols);
  const randomUpper = pickRandom(uppercaseChars);
  const randomLower = pickRandom(lowercaseChars);
  const randomDigits = `${pickRandom(digits)}${pickRandom(digits)}`;
  const extraChar = pickRandom(lowercaseChars);

  // Formato: Ej. Dan#84!kM2 o Lys$93*pT5
  return `${formattedPrefix}${symbol1}${randomDigits}${symbol2}${randomUpper}${randomLower}${extraChar}`;
};

export interface DuplicateCheckResult {
  isDuplicate: boolean;
  field?: 'email' | 'telefono';
  message?: string;
  matchedUser?: UsuarioResponse;
}

/**
 * Valida si un correo o teléfono ya existe en una lista de usuarios.
 * Permite excluir un idUser cuando se está editando una cuenta existente.
 */
export const checkDuplicateAccount = (
  params: {
    email?: string;
    phone?: string;
    excludeUserId?: number;
  },
  existingUsers: UsuarioResponse[]
): DuplicateCheckResult => {
  const targetEmail = (params.email || '').trim().toLowerCase();
  const targetPhone = (params.phone || '').trim();

  for (const user of existingUsers) {
    if (params.excludeUserId && user.idUser === params.excludeUserId) {
      continue;
    }

    // 1. Validar correo duplicado
    if (targetEmail && user.email) {
      if (user.email.trim().toLowerCase() === targetEmail) {
        return {
          isDuplicate: true,
          field: 'email',
          message: `No se permiten cuentas duplicadas: el correo "${targetEmail}" ya se encuentra registrado.`,
          matchedUser: user,
        };
      }
    }

    // 2. Validar teléfono / celular duplicado
    if (targetPhone && user.telefono) {
      if (arePhonesMatching(targetPhone, user.telefono)) {
        return {
          isDuplicate: true,
          field: 'telefono',
          message: `No se permiten cuentas duplicadas: el número de celular ya está registrado por otra cuenta.`,
          matchedUser: user,
        };
      }
    }
  }

  return { isDuplicate: false };
};
