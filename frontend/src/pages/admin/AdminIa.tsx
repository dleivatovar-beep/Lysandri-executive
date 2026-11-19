import React, { useRef, useState } from 'react';
import { CheckCircle2, FileText, Loader2, Upload } from 'lucide-react';
import { academicService } from '../../services/academicService';
import { apiClient } from '../../services/api';
import { getApiErrorMessage } from '../../services/authService';
import { formatosDocumento, leerDocumento } from '../../services/leerDocumento';

interface ResultadoCarga {
  idDocumento: number;
  fragmentos: number;
  version: string;
  titulo: string;
  programa: string;
}

const normalizar = (texto: string) => texto.normalize('NFD')
  .replace(/\p{M}/gu, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

export const AdminIa: React.FC<React.PropsWithChildren> = ({ children }) => {
  const [ocupado, setOcupado] = useState(false);
  const [error, setError] = useState('');
  const [resultado, setResultado] = useState<ResultadoCarga | null>(null);
  const [nombreArchivo, setNombreArchivo] = useState('');
  const [arrastrando, setArrastrando] = useState(false);
  const selector = useRef<HTMLInputElement>(null);
  const nivelesArrastre = useRef(0);
  const cargaActiva = useRef(false);

  const cargar = async (archivo: File) => {
    if (cargaActiva.current) return;

    cargaActiva.current = true;
    setNombreArchivo(archivo.name);
    setOcupado(true);
    setError('');
    setResultado(null);
    try {
      const contenido = await leerDocumento(archivo);
      const nombre = archivo.name.replace(/\.[^.]+$/, '').trim();
      const encabezado = contenido.match(/^#[ \t]+(.+)$/m)?.[1]?.trim();
      const marcaArchivo = /(?:[ _-]+)(?:versi[oó]n[-_ ]*|v)(\d+(?:\.\d+)*)$/i;
      const version = contenido.match(/^[ \t]*(?:#{1,6}[ \t]+)?versi[oó]n[ \t]*:[ \t]*v?(\d+(?:\.\d+)*)[ \t]*$/im)?.[1]
        ?? nombre.match(marcaArchivo)?.[1] ?? '1';
      const titulo = ((encabezado || nombre).replace(marcaArchivo, '').trim() || 'Documento').slice(0, 200);
      const programas = await academicService.getAllProgramsAdmin();
      // Un curso se vincula cuando su nombre completo coincide de forma inequívoca.
      const referencias = [nombre, encabezado, contenido.match(/^[ \t]*programa[ \t]*:[ \t]*(.+)$/im)?.[1]]
        .filter((texto): texto is string => Boolean(texto))
        .map(texto => ` ${normalizar(texto)} `);
      const coincidencias = programas.filter(programa => {
        const nombrePrograma = normalizar(programa.titulo || programa.tituloPrograma || '');
        return nombrePrograma && referencias.some(referencia => referencia.includes(` ${nombrePrograma} `));
      });
      const programa = coincidencias.length === 1 ? coincidencias[0] : undefined;
      const respuesta = await apiClient.post<ResultadoCarga>('/admin/conocimiento', {
        titulo,
        version: version.slice(0, 40),
        estado: 'VIGENTE',
        idPrograma: programa?.idPrograma ?? null,
        contenido,
      }, { timeout: 900_000 });
      setResultado({ ...respuesta.data, titulo, programa: programa?.titulo || programa?.tituloPrograma || 'Conocimiento general' });
    } catch (fallo) {
      setError(getApiErrorMessage(fallo));
    } finally {
      cargaActiva.current = false;
      setOcupado(false);
    }
  };

  const recibirArchivo = (event: React.DragEvent<HTMLElement>) => {
    event.preventDefault();
    nivelesArrastre.current = 0;
    setArrastrando(false);
    if (cargaActiva.current) return;
    const archivos = event.dataTransfer.files;
    if (archivos.length > 1) {
      setNombreArchivo('');
      setResultado(null);
      setError('Elige un archivo a la vez.');
    } else if (archivos[0]) {
      void cargar(archivos[0]);
    }
  };

  return (
    <section className="lx-admin-page">
      <header className="lx-admin-pagehead">
        <div className="lx-admin-pagehead-copy">
          <h1>IA</h1>
          <div><p>Agrega documentos para que el asistente pueda consultarlos al responder.</p></div>
        </div>
      </header>
      <div className="grid items-start gap-6 xl:grid-cols-[minmax(16rem,24rem)_minmax(0,1fr)]">
        <section className="min-w-0 overflow-hidden rounded-xl border border-[var(--admin-line)] bg-[var(--admin-surface)]"
          aria-labelledby="titulo-carga" onDrop={recibirArchivo}
          onDragEnter={event => {
            if (!event.dataTransfer.types.includes('Files')) return;
            event.preventDefault();
            nivelesArrastre.current += 1;
            if (!cargaActiva.current) setArrastrando(true);
          }}
          onDragOver={event => {
            if (!event.dataTransfer.types.includes('Files')) return;
            event.preventDefault();
            event.dataTransfer.dropEffect = cargaActiva.current ? 'none' : 'copy';
          }}
          onDragLeave={() => {
            nivelesArrastre.current = Math.max(0, nivelesArrastre.current - 1);
            if (nivelesArrastre.current === 0) setArrastrando(false);
          }}>
          <header className="flex min-h-[5.25rem] items-center gap-3 border-b border-[var(--admin-line)] bg-[var(--admin-canvas)] px-5">
            <FileText className="h-5 w-5 shrink-0 text-[var(--admin-cyan)]" aria-hidden="true" />
            <h2 id="titulo-carga" className="text-base font-semibold">Cargar documento</h2>
          </header>
          <div className="p-5">
            <div className={`flex flex-col items-center gap-3 rounded-lg border border-dashed px-3 py-7 text-center transition-colors ${arrastrando ? 'border-[var(--admin-cyan)] bg-[var(--admin-surface-raised)]' : 'border-[var(--admin-line)] bg-[var(--admin-canvas)]'}`}>
              <span className="grid h-12 w-12 place-items-center rounded-xl bg-[var(--admin-surface-raised)] text-[var(--admin-cyan)]">
                <Upload className="h-6 w-6" aria-hidden="true" />
              </span>
              <p className="text-sm font-semibold">Arrastra un archivo aquí</p>
              <input ref={selector} type="file" accept={formatosDocumento} disabled={ocupado} hidden aria-label="Archivo para indexar"
                onChange={event => {
                  const archivo = event.currentTarget.files?.[0];
                  event.currentTarget.value = '';
                  if (archivo) void cargar(archivo);
                }} />
              <button type="button" className="lx-admin-primary" disabled={ocupado}
                onClick={() => selector.current?.click()}>
                Elegir archivo
              </button>
            </div>
            <p className="mt-3 text-xs leading-relaxed text-[var(--admin-muted)]">
              La carga empieza automáticamente. Usa documentos que puedan consultar los usuarios del asistente.
            </p>
            {(ocupado || resultado) && (
              <div role="status" className="mt-4 flex items-start gap-2.5 rounded-lg border border-[var(--admin-line)] px-3 py-3 text-sm">
                {ocupado
                  ? <Loader2 className="mt-0.5 h-4 w-4 shrink-0 text-[var(--admin-cyan)] motion-safe:animate-spin" aria-hidden="true" />
                  : <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700 dark:text-emerald-300" aria-hidden="true" />}
                <div className="min-w-0 break-words">
                  <p className="font-semibold">{resultado?.titulo ?? nombreArchivo}</p>
                  {resultado ? (
                    <>
                      <p className="text-xs text-emerald-700 dark:text-emerald-300">Documento subido</p>
                      <p className="mt-1 text-xs text-[var(--admin-muted)]">
                        {resultado.programa} · Versión {resultado.version} · {resultado.fragmentos}{' '}
                        {resultado.fragmentos === 1 ? 'fragmento' : 'fragmentos'}
                      </p>
                    </>
                  ) : <p className="text-xs text-[var(--admin-muted)]">Indexando el documento…</p>}
                </div>
              </div>
            )}
            {error && (
              <div role="alert" className="mt-4 break-words rounded-lg border border-red-500/40 px-3 py-3 text-sm text-red-700 dark:text-red-300">
                <p className="font-semibold">{nombreArchivo || 'Error de carga'}</p>
                <p className="mt-1 text-xs">{error}</p>
              </div>
            )}
          </div>
        </section>
        <section className="min-w-0" aria-label="Probar el asistente">
          {children}
        </section>
      </div>
    </section>
  );
};
