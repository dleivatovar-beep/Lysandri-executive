export const formatosDocumento = '.md,.txt,.pdf,.xlsx';
const maxCaracteres = 100_000;
const maxBytesTexto = maxCaracteres * 4 + 3;
const maxBytesDigital = 5 * 1024 * 1024;
const errorLongitud = 'El documento supera los 100 000 caracteres de texto.';

async function leerPdf(bytes: ArrayBuffer) {
  const [{ getDocument, GlobalWorkerOptions, InvalidPDFException, PasswordException }, { default: workerUrl }] = await Promise.all([
    import('pdfjs-dist'),
    import('pdfjs-dist/build/pdf.worker.min.mjs?url'),
  ]);
  GlobalWorkerOptions.workerSrc = workerUrl;
  const tarea = getDocument({
    data: new Uint8Array(bytes),
    useWasm: false,
    stopAtErrors: true,
  });
  try {
    const pdf = await tarea.promise;
    if (pdf.numPages > 200) throw new Error('El PDF supera el límite de 200 páginas.');
    const paginas: string[] = [];
    let total = 0;
    for (let numero = 1; numero <= pdf.numPages; numero++) {
      const pagina = await pdf.getPage(numero);
      const lector = pagina.streamTextContent().getReader();
      const partes: string[] = [];
      try {
        let bloque = await lector.read();
        while (!bloque.done) {
          for (const item of bloque.value.items) {
            if (typeof item.str !== 'string') continue;
            const parte = item.str + (item.hasEOL ? '\n' : ' ');
            total += parte.length;
            if (total > maxCaracteres) throw new Error(errorLongitud);
            partes.push(parte);
          }
          bloque = await lector.read();
        }
      } finally {
        await lector.cancel();
        pagina.cleanup();
      }
      const texto = partes.join('').trim();
      if (texto) paginas.push(`## Página ${numero}\n${texto}`);
    }
    if (!paginas.length) throw new Error('El PDF no contiene texto seleccionable. Si es un escaneo, aplícale OCR antes de subirlo.');
    return paginas.join('\n\n');
  } catch (error) {
    if (error instanceof PasswordException) throw new Error('El PDF está protegido. Guarda una copia sin contraseña y vuelve a subirla.');
    if (error instanceof InvalidPDFException) throw new Error('No se pudo leer el PDF. Comprueba que el archivo se abra correctamente.');
    throw error;
  } finally {
    await tarea.destroy();
  }
}

async function leerExcel(bytes: ArrayBuffer) {
  const { default: readExcelFile } = await import('read-excel-file/browser');
  const hojas = await readExcelFile(bytes, { parseNumber: numero => numero }).catch(() => {
    throw new Error('No se pudo leer el Excel. Comprueba que sea un archivo .xlsx válido y sin contraseña.');
  });
  let total = 0;
  const secciones: string[] = [];
  for (const hoja of hojas) {
    const lineas: string[] = [];
    let encabezados: string[] | undefined;
    for (const fila of hoja.data) {
      const celdas = fila.map(valor =>
        valor instanceof Date ? valor.toISOString().slice(0, 10) : String(valor ?? '').trim());
      const presentes = celdas.filter(Boolean);
      if (!presentes.length) {
        encabezados = undefined;
        continue;
      }
      const linea = encabezados
        ? celdas.flatMap((valor, indice) => valor ? [encabezados?.[indice] ? `${encabezados[indice]}: ${valor}` : valor] : []).join(' | ')
        : presentes.join(' | ');
      if (!encabezados && presentes.length > 1 && fila.every(v => v === null || typeof v === 'string')) {
        encabezados = celdas;
      }
      total += linea.length + 1;
      if (total > maxCaracteres) throw new Error(errorLongitud);
      lineas.push(linea);
    }
    if (lineas.length) secciones.push(`## Hoja ${hoja.sheet}\n${lineas.join('\n')}`);
  }
  if (!secciones.length) throw new Error('El archivo Excel no contiene celdas con datos para indexar.');
  return secciones.join('\n\n');
}

export async function leerDocumento(archivo: File): Promise<string> {
  const extension = /\.(md|txt|pdf|xlsx)$/i.exec(archivo.name)?.[1].toLowerCase();
  if (!extension) throw new Error('Selecciona un archivo .md, .txt, .pdf o .xlsx.');
  const digital = extension === 'pdf' || extension === 'xlsx';
  if (archivo.size > (digital ? maxBytesDigital : maxBytesTexto)) {
    throw new Error(digital ? 'El archivo supera el límite de 5 MB.' : errorLongitud);
  }

  const bytes = await archivo.arrayBuffer();
  let contenido: string;
  if (extension === 'pdf') contenido = await leerPdf(bytes);
  else if (extension === 'xlsx') contenido = await leerExcel(bytes);
  else {
    try {
      contenido = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    } catch {
      throw new Error('Guarda el archivo con codificación UTF-8 y vuelve a seleccionarlo.');
    }
  }
  contenido = contenido.replace(/\r\n?/g, '\n').trim();
  if (!contenido || contenido.includes('\0')) throw new Error('El archivo debe contener texto válido.');
  if (contenido.length > maxCaracteres) throw new Error(errorLongitud);
  return contenido;
}
