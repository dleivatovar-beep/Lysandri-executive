# Lysandri Executive - Client Web Application

Plataforma empresarial de Marketplace de activos tecnológicos y Asistente IA RAG para la alta directiva (C-Suite).

## Stack Técnico
- **Framework**: React 18+ (Vite)
- **Lenguaje**: TypeScript (Strict mode)
- **Estilos**: Tailwind CSS (Tema oscuro corporativo)
- **Iconos**: Lucide React
- **HTTP Client**: Axios

---

## Guía de Instalación y Ejecución Rápida

### 1. Requisitos Previos
Asegúrate de tener instalado **Node.js** (v18.0.0 o superior):
- Descargar desde: [https://nodejs.org](https://nodejs.org)
- Verificar versión en terminal:
  ```bash
  node -v
  npm -v
  ```

---

### 2. Pasos para Clonar e Instalar

```bash
# 1. Obtener los últimos cambios de la rama develop
git checkout develop
git pull origin develop

# 2. Instalar dependencias del frontend (desde la raíz o dentro de /frontend)
npm install --prefix frontend
```

---

### 3. Ejecutar el Servidor de Desarrollo

Puedes iniciar el servidor dev directamente desde la raíz o dentro de `frontend/`:

```bash
# Desde la raíz del repositorio:
npm run dev

# O ingresando a la carpeta frontend:
cd frontend
npm run dev
```

La aplicación estará disponible en: **`http://localhost:3000`**

---

### 4. Comandos Utilitarios

- **Compilar para Producción (Verificación TypeScript & Vite)**:
  ```bash
  npm run build --prefix frontend
  ```
- **Vista Previa de Producción**:
  ```bash
  cd frontend
  npm run preview
  ```
