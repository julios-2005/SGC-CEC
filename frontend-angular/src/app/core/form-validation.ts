export const DOCUMENT_PATTERN = /^[A-Za-z0-9]{5,20}$/;
export const EMAIL_PATTERN = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/;

/** Mismas reglas que TelefonoUtils: +, prefijo 00 y número local ecuatoriano. */
export function isValidPhone(value: unknown): boolean {
  const clean = String(value || '').trim().replace(/[^\d+]/g, '');
  const normalized = clean.startsWith('+') ? clean : clean.startsWith('00') ? `+${clean.slice(2)}` : `+593${clean.startsWith('0') ? clean.slice(1) : clean}`;
  return /^\+[1-9]\d{7,14}$/.test(normalized);
}

export function fileValidationMessage(file?: File | null, imagesOnly = false): string {
  if (!file) return '';
  if (file.size === 0) return 'El archivo está vacío.';
  if (file.size > 5 * 1024 * 1024) return 'El archivo supera el tamaño máximo de 5MB.';
  const extension = imagesOnly ? /\.(jpe?g|png)$/i : /\.(jpe?g|png|pdf)$/i;
  if (!extension.test(file.name)) {
    return imagesOnly ? 'La foto debe ser JPG o PNG.' : 'El documento debe ser JPG, PNG o PDF.';
  }
  return '';
}

export function dateRangeMessage(from: string, to: string): string {
  return from && to && from > to ? 'La fecha desde no puede ser posterior a la fecha hasta.' : '';
}

export function personValidationMessage(form: Record<string, any>): string {
  if (!DOCUMENT_PATTERN.test(String(form['cedula'] || '').trim())) return 'Ingrese una cédula de 10 dígitos o un pasaporte de 5 a 20 caracteres.';
  if (!String(form['nombres'] || '').trim() || !String(form['apellidos'] || '').trim()) return 'Los nombres y apellidos son obligatorios.';
  if (String(form['telefono'] || '').trim() && !isValidPhone(form['telefono'])) return 'Ingrese un número de WhatsApp válido, incluyendo el código de país.';
  if (form['correo'] && !EMAIL_PATTERN.test(String(form['correo']).trim())) return 'Ingrese un correo electrónico válido.';
  return '';
}
