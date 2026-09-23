# Guía de git — cómo trabajar en equipo

Paso a paso para trabajar en el repositorio sin pisar el trabajo de los demás: qué hacer al empezar el día, cómo subir tus cambios, cómo entregar tu tarea y qué hacer cuando git se queja.

**Orden de lectura para empezar:**

1. [Guía de inicio](GUIA-INICIO.md): instalar las herramientas y clonar el repo.
2. [Plan de trabajo](../PLAN-DE-TRABAJO.md): qué te toca y en qué rama.
3. Esta guía: cómo trabajar con git día a día.

Todos los comandos son para **PowerShell** (la terminal de VS Code en Windows) y se ejecutan desde la carpeta del repo, por ejemplo `C:\dev\teambsoft-backend`.

## Conceptos en un minuto

| Palabra | Qué es |
|---|---|
| **Commit** | Una "foto" de tus cambios con un mensaje. El historial es una cadena de commits |
| **Rama (branch)** | Una línea de trabajo paralela. Trabajas en la tuya sin tocar la de los demás |
| **`main`** | La rama principal: la versión del proyecto que siempre debe funcionar |
| **`origin`** | La copia del repo que está en GitHub |
| **Push** | Subir tus commits a GitHub |
| **Pull** | Traer a tu computador los commits que hay en GitHub |
| **Pull request (PR)** | La solicitud, en GitHub, para unir tu rama a `main`. Otro integrante la revisa antes |
| **Merge** | Unir los cambios de una rama con otra |
| **Conflicto** | Cuando dos personas cambiaron las mismas líneas y git no sabe cuál versión dejar |

## Las 5 reglas

1. **Nunca trabajes en `main`.** Cada tarea tiene su propia rama (ver el [plan](../PLAN-DE-TRABAJO.md)).
2. **Trae lo nuevo de `main` al empezar el día y antes de subir** (`git pull origin main`).
3. **Haz commits pequeños**, con mensajes que digan qué hiciste.
4. **Antes de subir, comprueba que compila y que pasan las pruebas.**
5. **Si git dice algo que no entiendes, detente y pregunta.** Nunca uses `--force`.

## El ciclo de trabajo

```mermaid
graph TD
    A[Empiezo a trabajar] --> B[Me paro en mi rama]
    B --> C[Traigo lo nuevo de main]
    C --> D[Programo]
    D --> E[Guardo un commit]
    E --> F{¿Voy a subir?}
    F -- todavía no --> D
    F -- sí --> G[Traigo lo nuevo de main otra vez]
    G --> H[Pruebo: compila y pasan las pruebas]
    H --> I[git push]
    I --> J{¿Terminé la tarea?}
    J -- no --> D
    J -- sí --> K[Abro el pull request]
```

## 0. Configuración única (una vez por computador)

Si seguiste la [guía de inicio](GUIA-INICIO.md), ya lo hiciste. Si no, además del nombre y el correo, ejecuta:

```powershell
git config --global pull.rebase false
git config --global core.editor "code --wait"
```

- **`pull.rebase false`:** cuando tu rama y la de GitHub se hayan separado, git las une con un commit de unión (*merge*). Sin esto, git se detiene con el error `Need to specify how to reconcile divergent branches`.
- **`core.editor "code --wait"`:** cuando git necesite un mensaje (por ejemplo, al unir ramas), abre una pestaña en VS Code en lugar de Vim, un editor de terminal que confunde bastante. Revisa el mensaje, **cierra la pestaña** y git continúa.

## 1. Empezar una tarea (una vez por tarea)

Espera a que tu tarea esté desbloqueada (ver "Orden de trabajo" en el plan). Luego, cambiando `b2-escritura` por el nombre de tu rama:

```powershell
git switch main
git pull
git switch -c b2-escritura
git push -u origin b2-escritura
```

| Comando | Qué hace |
|---|---|
| `git switch main` | Te pasa a la rama `main` |
| `git pull` | Actualiza tu `main` con lo último de GitHub, para que tu rama nazca con todo lo que ya está terminado |
| `git switch -c b2-escritura` | Crea tu rama y te pasa a ella |
| `git push -u origin b2-escritura` | Crea tu rama también en GitHub y deja enlazadas las dos. De aquí en adelante basta con `git push` |

Para saber en qué rama estás: `git status` lo dice en la primera línea (`On branch ...`). VS Code también la muestra abajo a la izquierda.

## 2. Al empezar cada día

```powershell
git switch b2-escritura
git status
git pull origin main
```

1. **`git switch`** asegura que estás en **tu** rama.
2. **`git status`** debe decir `nothing to commit, working tree clean`. Si muestra archivos modificados, son cambios que no guardaste la última vez: haz commit primero (paso 3).
3. **`git pull origin main`** trae lo que tus compañeros ya unieron a `main` y lo mezcla con tu rama. Si nadie unió nada, dice `Already up to date`. Si se abre una pestaña `MERGE_MSG` en VS Code, es el mensaje del commit de unión: ciérrala.

Después levanta el entorno:

```powershell
docker compose up -d
```

## 3. Mientras trabajas: guardar commits

Cada vez que termines algo pequeño que funcione (un DTO, un método, una validación), guarda un commit:

```powershell
git status
git add .
git commit -m "Agrega validaciones de ProductoRequest"
```

- **Revisa `git status` antes de `git add .`:** solo deben aparecer archivos que quieres guardar. Si aparece `target/`, `.vscode/` o algo que no reconoces, pregunta antes.
- **Un buen mensaje** dice qué cambió: `Agrega POST /productos`, `Corrige validación de stock negativo`. **Uno malo** no dice nada: `cambios`, `avance`, `asdf`.
- **Hacer commit no sube nada.** Los commits quedan en tu computador hasta que hagas push.

## 4. Subir tus cambios

Sube al menos una vez al día. Así queda un respaldo y tus compañeros ven tu avance.

```powershell
git pull origin main
.\mvnw.cmd test
git push
```

1. **`git pull origin main`:** traes lo nuevo de `main` **antes** de subir. Si hay un conflicto, aparece ahora, en tu rama, y lo resuelves tú (sección 7), en lugar de aparecer después en el pull request.
2. **`.\mvnw.cmd test`:** comprueba que lo nuevo de `main` y lo tuyo, juntos, compilan y pasan las pruebas. Mongo tiene que estar encendido.
3. **`git push`:** sube tus commits a tu rama en GitHub. **No toca `main`.**

## 5. Terminar la tarea: el pull request

**Antes de abrirlo**, revisa la "Definición de terminado" del [plan](../PLAN-DE-TRABAJO.md): compila, pasan las pruebas, lo probaste contra el contrato y marcaste tu casilla en el README. Luego haz el paso 4 una última vez.

**Abrir el PR:**

1. Entra a https://github.com/rancesra/teambsoft-backend. Arriba aparece un aviso con tu rama y el botón **Compare & pull request**. Si no aparece, ve a la pestaña **Pull requests → New pull request**.
2. Revisa que diga **base: `main` ← compare: `tu-rama`**.
3. **Título:** la tarea y qué entrega; por ejemplo, `B2: POST y PUT de productos`.
4. **Descripción:** qué hiciste, cómo probarlo y cualquier cosa que el revisor deba saber.
5. En **Reviewers**, a la derecha, elige quién lo va a revisar.
6. **Create pull request.**

**Si el revisor pide cambios:** hazlos en tu misma rama, guárdalos con commit y haz `git push`. El PR se actualiza solo; no abras otro.

**Cuando esté aprobado:** **Merge pull request → Confirm merge**, y después **Delete branch**. Avisa en el grupo que tu tarea entró a `main`, para que tus compañeros hagan `git pull origin main` en sus ramas.

**Para tu siguiente tarea**, vuelve al paso 1: siempre se empieza desde un `main` actualizado.

## 6. Revisar el PR de un compañero

Revisar no es un trámite: es la forma de que todos entiendan el código que van a usar.

1. En el PR, abre la pestaña **Files changed** y lee los cambios. Puedes comentar una línea con el **+** que aparece a su lado.
2. Si quieres probarlo en tu computador, primero guarda tus propios cambios con commit y luego:
   ```powershell
   git fetch
   git switch b2-escritura
   ```
   Cuando termines de probar, vuelve a tu rama con `git switch <tu-rama>`.
3. Botón **Review changes**: **Approve** si está bien, o **Request changes** con un comentario que explique qué corregir.

**Quién revisa a quién (sugerencia):** B2 y B3 se revisan entre sí. B1 lo revisan B2 y B3, porque van a construir encima. B4 lo revisa Rances. En el frontend, F1, F2 y F3 se revisan entre ellos.

## 7. Conflictos

Un conflicto ocurre cuando tú y otra persona cambiaron **las mismas líneas** de un archivo. Es normal, no es un error tuyo, y se resuelve en minutos.

**Cuándo lo verás:** al hacer `git pull origin main`, git dice algo como:

```
CONFLICT (content): Merge conflict in src/main/java/.../ProductoService.java
Automatic merge failed; fix conflicts and then commit the result.
```

**Cómo resolverlo:**

1. Abre el archivo en VS Code. Verás bloques así:
   ```
   <<<<<<< HEAD
       (tu versión)
   =======
       (la versión que viene de main)
   >>>>>>> ...
   ```
2. Encima de cada bloque, VS Code muestra tres opciones: **Accept Current Change** (lo tuyo), **Accept Incoming Change** (lo de `main`) y **Accept Both Changes** (los dos). Cuando cada uno agregó un método distinto, casi siempre la respuesta es **Accept Both Changes**.
3. Revisa que el archivo quede bien (sin `<<<<<<<`, `=======` ni `>>>>>>>`) y que compile.
4. Guarda el archivo y termina la unión:
   ```powershell
   git add .
   git commit --no-edit
   ```
   `--no-edit` usa el mensaje automático del commit de unión.
5. Sigue con normalidad: prueba y `git push`.

Si no estás seguro de qué versión dejar, **no adivines**: pregúntale a quien escribió la otra parte.

## 8. Cuando git se queja

| Mensaje o situación | Qué pasó | Qué hacer |
|---|---|---|
| `Your local changes to the following files would be overwritten` | Tienes cambios sin guardar y git no quiere perderlos | Guárdalos con `git add .` y `git commit -m "..."`, y repite el comando |
| `Updates were rejected because the remote contains work that you do not have locally` | Tu rama en GitHub tiene commits que tú no tienes (por ejemplo, porque subiste desde otro computador) | `git pull` y luego `git push` |
| `Need to specify how to reconcile divergent branches` | Falta la configuración única | `git config --global pull.rebase false` y repite |
| `CONFLICT (content): Merge conflict in ...` | Dos personas cambiaron las mismas líneas | Sección 7 |
| `not a git repository` | Estás fuera de la carpeta del repo | `cd C:\dev\teambsoft-backend` |
| La terminal muestra una pantalla extraña con `~` a la izquierda | Es Vim: falta la configuración única del editor | Escribe `:wq` y presiona Enter para salir |
| Hiciste commits en `main` por error | Trabajaste en la rama equivocada | Ver abajo |

**Si hiciste commits en `main` por error y todavía no los subiste**, y aún no habías creado tu rama de tarea:

```powershell
git status
git branch b2-escritura
git reset --hard origin/main
git switch b2-escritura
```

1. `git status` tiene que decir `working tree clean`. Si no, haz commit primero: el comando del paso 3 borra lo que no esté guardado.
2. `git branch b2-escritura` crea tu rama de tarea con esos commits adentro.
3. `git reset --hard origin/main` devuelve tu `main` a como está en GitHub. Tus commits no se pierden: siguen en la rama del paso 2.
4. `git switch b2-escritura` te pasa a tu rama para seguir trabajando ahí.

Si tu rama de tarea ya existía, o si ya subiste los commits a `main`, **no hagas nada todavía y pide ayuda**: también tiene arreglo, pero es distinto.

---

_Última actualización: 2026-09-11_
