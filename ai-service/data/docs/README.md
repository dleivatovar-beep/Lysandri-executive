# Directorio de Manuales Técnicos y Playbooks para RAG

Coloca en este directorio todos los archivos `.pdf` que deseas indexar en la base de datos vectorial PostgreSQL con `pgvector`.

### Ejemplos recomendados:
* `Manual_Torno_CNC.pdf`
* `Guia_Seguridad_Industrial.pdf`
* `Playbook_Arquitectura_Cloud.pdf`

### Ejecución de la ingesta:
```bash
# Dentro de ai-service/
python ingest.py
```
El script leerá cada PDF, extraerá el texto por página, dividirá el contenido en fragmentos de 500 caracteres con 50 de solapamiento, calculará los embeddings de 384 dimensiones (`all-MiniLM-L6-v2`) y los insertará en la tabla `documento_chunks`.
