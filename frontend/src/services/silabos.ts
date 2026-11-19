import indice from '../../../ai-service/data/docs/Cursos/indice.json';

const documentos = import.meta.glob<string>('../../../ai-service/data/docs/Cursos/*.md', {
  query: '?raw', import: 'default', eager: true,
});

export function obtenerSilabo(idPrograma: number | undefined) {
  const registro = indice.find((item) => item.idPrograma === idPrograma);
  if (!registro) return null;
  const texto = documentos[`../../../ai-service/data/docs/Cursos/${registro.archivo}`];
  if (!texto) return null;
  const secciones = texto.replace(/\r\n/g, '\n').split(/^## /m).slice(1).map((bloque) => {
    const [titulo, ...lineas] = bloque.split('\n');
    return { titulo: titulo.trim(), contenido: lineas.join('\n').trim() };
  });
  return {
    enRevision: registro.estado === 'PROPUESTA',
    objetivo: secciones.find((seccion) => seccion.titulo === 'Objetivo propuesto')?.contenido,
    actividad: secciones.find((seccion) => seccion.titulo === 'Actividad y evaluación propuestas')?.contenido,
    modulos: secciones.filter((seccion) => /^Módulo \d+:/.test(seccion.titulo)).map((seccion) => {
      const [dedicacion, ...contenido] = seccion.contenido.split('\n');
      return {
        titulo: seccion.titulo,
        duracion: dedicacion.replace(/^Dedicación propuesta:\s*/, '').replace(/\.$/, ''),
        contenido: contenido.join(' ').trim(),
      };
    }),
  };
}
