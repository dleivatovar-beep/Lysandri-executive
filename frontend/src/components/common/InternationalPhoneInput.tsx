import React, { useId } from 'react';
import {
  CountryPhoneConfig,
  SUPPORTED_COUNTRIES,
  getCountryByCode,
} from '../../utils/countries';

interface InternationalPhoneInputProps {
  id?: string;
  label?: string;
  countryCode: string;
  phoneNumber: string; // Dígitos locales o con formato
  onCountryChange: (country: CountryPhoneConfig) => void;
  onPhoneChange: (rawDigits: string, formattedWithDial: string) => void;
  disabled?: boolean;
}

export const InternationalPhoneInput: React.FC<InternationalPhoneInputProps> = ({
  id,
  label = 'Número de Celular',
  countryCode,
  phoneNumber,
  onCountryChange,
  onPhoneChange,
  disabled = false,
}) => {
  const generatedId = useId();
  const inputId = id || generatedId;
  const currentCountry = getCountryByCode(countryCode);

  const cleanDigits = phoneNumber.replace(/\D/g, '').slice(0, currentCountry.digitsLength);

  // Formato visual en tiempo real
  const formatDigits = (digits: string, country: CountryPhoneConfig): string => {
    if (country.code === 'PE' || country.code === 'CL' || country.code === 'EC' || country.code === 'ES') {
      if (digits.length <= 3) return digits;
      if (digits.length <= 6) return `${digits.slice(0, 3)} ${digits.slice(3)}`;
      return `${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}`;
    }
    if (country.code === 'CO') {
      if (digits.length <= 3) return digits;
      if (digits.length <= 6) return `${digits.slice(0, 3)} ${digits.slice(3)}`;
      return `${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}`;
    }
    if (country.code === 'MX' || country.code === 'AR' || country.code === 'US') {
      if (digits.length <= 2) return digits;
      if (digits.length <= 6) return `${digits.slice(0, 2)} ${digits.slice(2)}`;
      return `${digits.slice(0, 2)} ${digits.slice(2, 6)} ${digits.slice(6)}`;
    }
    if (digits.length <= 4) return digits;
    return `${digits.slice(0, 4)} ${digits.slice(4)}`;
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value.replace(/\D/g, '').slice(0, currentCountry.digitsLength);
    const formatted = `${currentCountry.dialCode} ${formatDigits(raw, currentCountry)}`;
    onPhoneChange(raw, formatted);
  };

  const handleCountrySelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newCountry = getCountryByCode(e.target.value);
    onCountryChange(newCountry);
    const raw = cleanDigits.slice(0, newCountry.digitsLength);
    const formatted = `${newCountry.dialCode} ${formatDigits(raw, newCountry)}`;
    onPhoneChange(raw, formatted);
  };

  const displayFormatted = formatDigits(cleanDigits, currentCountry);

  return (
    <div>
      <div className="mb-1.5 flex items-center justify-between">
        <label
          htmlFor={inputId}
          className="text-xs font-semibold text-slate-700 dark:text-slate-300"
        >
          {label}
        </label>
      </div>

      <div className="relative flex items-center rounded-xl border border-slate-200 bg-slate-50 transition-all focus-within:border-cyan-500/60 focus-within:bg-white focus-within:ring-2 focus-within:ring-cyan-500/15 dark:border-slate-800 dark:bg-slate-900/50">
        {/* Selector de País: Ej: Perú +51, Colombia +57 */}
        <div className="relative flex h-12 items-center border-r border-slate-200 dark:border-slate-800 bg-slate-100/60 dark:bg-slate-900/80 rounded-l-xl px-3 cursor-pointer shrink-0">
          <span className="font-semibold text-xs text-slate-800 dark:text-slate-200 whitespace-nowrap">
            {currentCountry.dialCode}
          </span>
          <span className="text-[10px] text-slate-400 pointer-events-none ml-1.5">▼</span>

          <select
            value={currentCountry.code}
            onChange={handleCountrySelect}
            disabled={disabled}
            aria-label="Seleccionar país del celular"
            className="absolute inset-0 opacity-0 w-full h-full cursor-pointer"
          >
            {SUPPORTED_COUNTRIES.map((country) => (
              <option key={country.code} value={country.code} className="dark:bg-slate-900 dark:text-white text-slate-900">
                {country.name} {country.dialCode}
              </option>
            ))}
          </select>
        </div>

        {/* Input del Número de Celular */}
        <input
          id={inputId}
          type="tel"
          inputMode="numeric"
          disabled={disabled}
          value={displayFormatted}
          placeholder={currentCountry.placeholder}
          maxLength={18}
          autoComplete="tel-national"
          onChange={handleInputChange}
          className="h-12 w-full rounded-r-xl bg-transparent px-3.5 text-xs font-mono font-bold tracking-wider text-slate-900 outline-none placeholder:font-normal placeholder:tracking-normal placeholder:text-slate-400 dark:text-white"
        />
      </div>
    </div>
  );
};
