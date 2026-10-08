export interface CountryPhoneConfig {
  code: string;
  name: string;
  flag?: string;
  dialCode: string;
  digitsLength: number;
  placeholder: string;
  example: string;
}

export const SUPPORTED_COUNTRIES: CountryPhoneConfig[] = [
  {
    code: 'PE',
    name: 'Perú',
    dialCode: '+51',
    digitsLength: 9,
    placeholder: '987 654 321',
    example: '9 dígitos (inicia con 9)',
  },
  {
    code: 'CO',
    name: 'Colombia',
    dialCode: '+57',
    digitsLength: 10,
    placeholder: '300 123 4567',
    example: '10 dígitos (ej. 300...)',
  },
  {
    code: 'MX',
    name: 'México',
    dialCode: '+52',
    digitsLength: 10,
    placeholder: '55 1234 5678',
    example: '10 dígitos',
  },
  {
    code: 'CL',
    name: 'Chile',
    dialCode: '+56',
    digitsLength: 9,
    placeholder: '9 1234 5678',
    example: '9 dígitos (inicia con 9)',
  },
  {
    code: 'AR',
    name: 'Argentina',
    dialCode: '+54',
    digitsLength: 10,
    placeholder: '11 2345 6789',
    example: '10 dígitos',
  },
  {
    code: 'EC',
    name: 'Ecuador',
    dialCode: '+593',
    digitsLength: 9,
    placeholder: '99 123 4567',
    example: '9 dígitos (inicia con 9)',
  },
  {
    code: 'BO',
    name: 'Bolivia',
    dialCode: '+591',
    digitsLength: 8,
    placeholder: '7123 4567',
    example: '8 dígitos',
  },
  {
    code: 'ES',
    name: 'España',
    dialCode: '+34',
    digitsLength: 9,
    placeholder: '612 345 678',
    example: '9 dígitos (inicia con 6 o 7)',
  },
  {
    code: 'US',
    name: 'Estados Unidos',
    dialCode: '+1',
    digitsLength: 10,
    placeholder: '202 555 0123',
    example: '10 dígitos',
  },
  {
    code: 'PA',
    name: 'Panamá',
    dialCode: '+507',
    digitsLength: 8,
    placeholder: '6123 4567',
    example: '8 dígitos',
  },
  {
    code: 'CR',
    name: 'Costa Rica',
    dialCode: '+506',
    digitsLength: 8,
    placeholder: '8123 4567',
    example: '8 dígitos',
  },
];

export const getCountryByCode = (code: string): CountryPhoneConfig => {
  return SUPPORTED_COUNTRIES.find((c) => c.code === code) || SUPPORTED_COUNTRIES[0];
};

export const getCountryByDialCode = (dialCode: string): CountryPhoneConfig => {
  return SUPPORTED_COUNTRIES.find((c) => c.dialCode === dialCode) || SUPPORTED_COUNTRIES[0];
};

export const parsePhoneString = (
  rawPhone?: string
): { country: CountryPhoneConfig; localDigits: string } => {
  if (!rawPhone) {
    return { country: SUPPORTED_COUNTRIES[0], localDigits: '' };
  }

  const clean = rawPhone.trim();

  // Buscar coincidencia con prefijos soportados
  for (const country of SUPPORTED_COUNTRIES) {
    if (clean.startsWith(country.dialCode)) {
      const rest = clean.slice(country.dialCode.length).replace(/\D/g, '');
      return { country, localDigits: rest };
    }
  }

  // Buscar por dial code sin '+'
  for (const country of SUPPORTED_COUNTRIES) {
    const rawDial = country.dialCode.replace('+', '');
    const onlyDigits = clean.replace(/\D/g, '');
    if (onlyDigits.startsWith(rawDial) && onlyDigits.length > rawDial.length) {
      return { country, localDigits: onlyDigits.slice(rawDial.length) };
    }
  }

  const digits = clean.replace(/\D/g, '');
  return { country: SUPPORTED_COUNTRIES[0], localDigits: digits };
};

export const formatInternationalPhoneDisplay = (rawPhone?: string): { text: string; isDniWarning?: boolean } => {
  if (!rawPhone || !rawPhone.trim()) {
    return { text: 'Sin registrar' };
  }

  const clean = rawPhone.trim();
  const pureDigits = clean.replace(/\D/g, '');

  // Si tiene exactamente 8 dígitos y no tiene prefijo internacional explícito, es probable que se haya ingresado un DNI
  if (pureDigits.length === 8 && !clean.startsWith('+')) {
    return {
      text: clean,
      isDniWarning: true,
    };
  }

  const { country, localDigits } = parsePhoneString(clean);

  // Formatear visualmente
  let formattedNumber = localDigits;
  if (country.code === 'PE' && localDigits.length === 9) {
    formattedNumber = `${localDigits.slice(0, 3)} ${localDigits.slice(3, 6)} ${localDigits.slice(6)}`;
  } else if (country.code === 'CO' && localDigits.length === 10) {
    formattedNumber = `${localDigits.slice(0, 3)} ${localDigits.slice(3, 6)} ${localDigits.slice(6)}`;
  } else if (country.code === 'MX' && localDigits.length === 10) {
    formattedNumber = `${localDigits.slice(0, 2)} ${localDigits.slice(2, 6)} ${localDigits.slice(6)}`;
  } else if (country.code === 'CL' && localDigits.length === 9) {
    formattedNumber = `${localDigits.slice(0, 1)} ${localDigits.slice(1, 5)} ${localDigits.slice(5)}`;
  } else if (country.code === 'US' && localDigits.length === 10) {
    formattedNumber = `(${localDigits.slice(0, 3)}) ${localDigits.slice(3, 6)}-${localDigits.slice(6)}`;
  } else if (localDigits.length > 4) {
    formattedNumber = `${localDigits.slice(0, 3)} ${localDigits.slice(3)}`;
  }

  return {
    text: `${country.dialCode} ${formattedNumber}`,
  };
};
