# Guía de inicio — backend del Catálogo (Windows)

Pasos para dejar tu computador listo y empezar a trabajar desde donde está el proyecto. Todos los comandos se ejecutan en **PowerShell**, la terminal por defecto de Windows y de VS Code.

> Esta guía es para el repositorio del **backend**. Si te toca frontend (F1, F2 o F3), tu repositorio es [teambsoft-frontend](https://github.com/rancesra/teambsoft-frontend) y tiene su propia guía de inicio: aquí se instala JDK y Docker, allá Node.js.

## 0. Acceso al repositorio

1. Necesitas una cuenta de GitHub.
2. Acepta la invitación de colaborador que te llegó por correo, o entra a https://github.com/rancesra/teambsoft-backend/invitations. Sin aceptarla puedes descargar el repo, pero no subir cambios.

## 1. Instalar las herramientas

| Herramienta | Para qué |
|---|---|
| Git | Control de versiones |
| JDK 21 | Compilar y ejecutar el backend |
| VS Code | Editor |
| Docker Desktop | Correr MongoDB (y más adelante RabbitMQ, Eureka y Kong) |

No hace falta instalar Maven: el proyecto trae el Maven Wrapper (`mvnw.cmd`), que lo descarga solo.

Instala en este orden:

1. **Git:** https://git-scm.com/downloads/win
   Deja las opciones por defecto. Dos de ellas importan:
   - *Checkout Windows-style, commit Unix-style line endings*: evita problemas de finales de línea con compañeros que usan Mac.
   - *Git Credential Manager*: es lo que te pedirá iniciar sesión en GitHub la primera vez que subas cambios.

2. **JDK 21 (Temurin):** https://adoptium.net/temurin/releases/?version=21
   Descarga el `.msi` para Windows x64. En la pantalla de componentes, **"Set JAVA_HOME variable"** viene desactivado: haz clic en su ícono y elige *Will be installed on local hard drive*.

3. **VS Code:** https://code.visualstudio.com/download
   Deja marcada la opción **Add to PATH**.

4. **Docker Desktop:** https://www.docker.com/products/docker-desktop/
   Deja marcada la opción de usar **WSL 2**. Al terminar te pedirá reiniciar. Después ábrelo una vez para que termine de configurarse; si te pide actualizar WSL, acepta. Si dice que la virtualización no está activada, hay que activarla en la BIOS; busca cómo hacerlo para tu modelo de computador.

### Extensiones de VS Code

Abre Extensiones (`Ctrl+Shift+X`) e instala:

- **Extension Pack for Java**
- **Spring Boot Extension Pack**

### Verificar

Cierra VS Code y cualquier terminal abierta, y vuelve a abrirlos para que reconozcan lo que instalaste. En PowerShell:

```powershell
git --version
java -version
docker --version
```

`java -version` debe mostrar la versión **21**.

## 2. Configurar Git (una sola vez)

Usa el correo registrado en tu cuenta de GitHub, para que tus commits aparezcan a tu nombre:

```powershell
git config --global user.name "Tu Nombre"
git config --global user.email "tu-correo@ejemplo.com"
```

Y dos ajustes que evitan problemas al trabajar en equipo (la [guía de git](GUIA-GIT.md) explica para qué sirve cada uno):

```powershell
git config --global pull.rebase false
git config --global core.editor "code --wait"
```

No tienes que iniciar sesión en GitHub ahora. La primera vez que hagas `git push` se abrirá el navegador para que autorices tu cuenta. Usa la misma cuenta con la que aceptaste la invitación.

## 3. Clonar el repositorio

*Clonar* es descargar el repositorio con todo su historial. Hazlo en una carpeta **fuera de OneDrive**: en muchos Windows, el Escritorio y Documentos se sincronizan con OneDrive, y eso bloquea archivos mientras Git o Maven trabajan. Por ejemplo, en `C:\dev`:

```powershell
mkdir C:\dev
cd C:\dev
git clone https://github.com/rancesra/teambsoft-backend.git
cd teambsoft-backend
code .
```

`code .` abre la carpeta del proyecto en VS Code.

## 4. Levantar MongoDB

Abre Docker Desktop y espera a que diga que está corriendo. Luego, en VS Code, abre una terminal (menú **Terminal → New Terminal**) y ejecuta:

```powershell
docker compose up -d
docker compose ps
```

`docker compose up -d` levanta MongoDB 7.0 en un contenedor, en segundo plano. La primera vez descarga la imagen de Mongo, que pesa unos cientos de MB. `docker compose ps` debe mostrar el contenedor `catalogo-mongo` con estado `Up`.

## 5. Verificar que el backend funciona

Con Mongo encendido, desde la carpeta del proyecto:

```powershell
.\mvnw.cmd test
```

El `.\` es obligatorio en PowerShell: significa "el archivo que está en esta carpeta". La primera vez tarda unos minutos, porque descarga Maven y las librerías. Debe terminar con `BUILD SUCCESS` y sin pruebas fallidas (`Failures: 0, Errors: 0`). Las pruebas necesitan Mongo porque, al arrancar, la aplicación guarda las categorías en la base de datos.

Luego arranca el servicio:

```powershell
.\mvnw.cmd spring-boot:run
```

Abre http://localhost:8080/actuator/health en el navegador. Debe decir `"status":"UP"`, y dentro de `components`, `mongo` también debe estar en `UP`. En http://localhost:8080/categorias deben aparecer las tres categorías.

Para detener el servicio presiona `Ctrl + C`. Para apagar Mongo:

```powershell
docker compose down
```

Los datos se conservan. **No uses `docker compose down -v`**: la `-v` borra el volumen con todos los datos de Mongo.

## 6. Antes de programar

1. Lee el [contrato](docs/CONTRATO-CATALOGO.md). Es el acuerdo con los otros equipos y no se cambia sin consultarlo.
2. Busca tu tarea en el [plan de trabajo](PLAN-DE-TRABAJO.md).
3. Lee la [guía de git](GUIA-GIT.md): cómo crear tu rama, qué hacer cada día y cómo entregar tu tarea con un pull request.