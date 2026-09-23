# Guía de Jira — Equipo B

**Para:** Rances, que crea el sitio y el espacio, y los siete para el uso diario · **Hace falta para:** la entrega del viernes 2 de octubre

El curso pide llevar el proyecto en **Jira**. Esta guía deja listo, en unos 30 minutos:

- las **5 épicas** del proyecto;
- las **8 historias de usuario** con sus criterios de aceptación;
- las **16 tareas**: B1 a B4, F1 a F3, H1 y las de coordinación pendientes, cada una con el enlace a su guía;
- el **sprint** con todo repartido entre los siete.

Todo sale de [`jira-backlog.csv`](jira-backlog.csv), que se importa de una vez.

**Jira lleva el estado de cada tarea. El [plan de trabajo](../PLAN-DE-TRABAJO.md) lleva el detalle técnico de cómo hacerla.** No se duplica: si el detalle se copia a Jira, en dos semanas los dos van a decir cosas distintas y nadie va a saber cuál manda.

> Jira cambió algunos nombres: a los proyectos ahora los llama **espacios** (*spaces*) y a los *issues*, **elementos de trabajo** (*work items*). Si un menú no coincide con esta guía, busca la palabra en inglés que va entre paréntesis.

## 1. Crear la cuenta y el sitio (Rances, una vez)

1. Entra a https://www.atlassian.com/software/jira/free y regístrate con tu correo. El plan **Free** llega hasta 10 personas, no vence y no pide tarjeta. Somos siete, así que cabe.
2. Cuando pida el nombre del sitio, usa algo como `teambsoft-uis`. La dirección queda `https://teambsoft-uis.atlassian.net`.
3. Al terminar, Jira puede crear solo un espacio de ejemplo con tarjetas `SCRUM-1` y `SCRUM-2`. Ese queda *team-managed* y no sirve para lo del paso 4: mándalo a la papelera (**··· → Configuración del espacio → ··· → Mover a la papelera**).

## 2. Crear el espacio (Rances, una vez)

1. En la barra lateral, **+** junto a **Espacio** → plantilla **Scrum** → **Usar plantilla**.
2. Tipo: **gestionado por la empresa** (*company-managed*). **Esto es lo más importante del paso:** solo así la importación mete cada historia dentro de su épica. En los *team-managed* Jira no lo permite, y tocaría acomodar 24 tarjetas a mano.
3. Nombre: `Catalogo - Equipo B`. Clave: **`CAT`**. Con esa clave las tarjetas se llaman `CAT-1`, `CAT-2`, y así.
4. En **Vamos a configurar tu espacio**, deja apagado *Empezar con actividades de ejemplo*. En **Estados**, agrega **En revisión** justo **después** de *En curso*.

> **Si eliges otra clave distinta de `CAT`, avísame:** la clave va en los nombres de rama y en los mensajes de commit, así que hay que actualizar la [guía de git](GUIA-GIT.md) y el [plan de trabajo](../PLAN-DE-TRABAJO.md).

## 3. Invitar a los seis (Rances, una vez)

1. Al crear el espacio, Jira muestra un campo para invitar: escribe el correo de cada uno.
2. En **Configuración del espacio → Personas** (*Space settings → People*), agrégalos con el rol **Administrador**, para que puedan mover, editar y crear tarjetas. En un grupo de siete no vale la pena andar pidiendo permisos.
3. Ellos aceptan desde el correo.

| Integrante | Tarea |
|---|---|
| Hector Franco | B2 |
| Jhon Velandia | B3 |
| Cristian Rivera | B4 |
| Juan Diego Tellez | F1 |
| Roger Hernandez | F2 |
| Carlos Beltrán | F3 |

## 4. Importar el backlog (Rances, una vez)

El archivo es [`docs/guias/jira-backlog.csv`](jira-backlog.csv): **29 tarjetas** — 5 épicas, 8 historias y 16 tareas.

Cada una lleva hasta tres etiquetas: el sprint (`sprint-1`), el responsable (`rances`, `hector`, `jhon`, `cristian`, `juandiego`, `roger`, `carlos`) y el frente (`backend`, `frontend`, `hostapp`, `coordinacion`, `documentacion`).

Pasos:

1. **Configuración (⚙) → Sistema** (*Settings → System*) → **Importación de sistema externo** (*External system import*) → **CSV**.
2. **Configurar espacio:** en *Use settings from an existing project* elige **Catalogo - Equipo B**. Revisa que la clave sea `CAT`.
3. **Subir CSV:** `jira-backlog.csv`, codificación **UTF-8**, delimitador **coma**. No hace falta archivo de configuración.
4. **Asociar campos:**

   | Columna del CSV | Campo de Jira |
   |---|---|
   | `Issue ID` | *Work item id* |
   | `Issue Type` | *Work Type* |
   | `Summary` | *Resumen* |
   | `Parent` | **Principal** (así se llama en español el campo *Parent*) |
   | `Priority` | *Prioridad* |
   | `Description` | *Descripción* |
   | `Labels` (las tres columnas) | *Etiquetas* |

5. **Asociar valores:** `Epic` → *Epic*, `Story` → *Story*, `Task` → *Task*.
6. **Mover usuarios:** dirá que no hay usuarios en el CSV. Es lo esperado: los responsables se asignan en el paso 5, por etiqueta y en bloque.
7. **Revisar detalles:** 1 espacio, 3 tipos de actividad y **29 actividades**. Importa.

Al terminar sale un aviso amarillo de que Jira reindexa por unas horas y las búsquedas pueden no mostrar todo. Es normal: mientras tanto trabaja desde el **Backlog**.

**Para comprobar que quedó bien:** en el backlog, `Historia 2 - Listar productos` debe mostrar la épica **Historias de usuario**, y `B2 - Escritura de productos` la épica **Backend - Microservicio de Catalogo**. Si las historias quedaron sueltas, sin épica, el espacio se creó *team-managed*: bórralo y vuelve al paso 2.

**Si algo sale mal:**

- Si las tarjetas quedan en otro estado (por ejemplo *En revisión*), en el Backlog usa **Select all → Cambiar estado → Por hacer**.
- Si la importación falla por el campo *Principal*, borra lo importado y vuelve a importar sin relacionar esa columna; después mete cada tarjeta en su épica en bloque, filtrando por su etiqueta `epica-*`.
- Si Jira no te deja borrar una tarjeta, agrégate al rol **Administrador** del espacio: ser dueño del sitio no da ese permiso dentro del espacio.

## 5. El sprint y los responsables (Rances, una vez)

**El sprint.** Jira ya creó uno vacío. En su menú **•••** → **Editar sprint**:

| Nombre | Fechas | Objetivo |
|---|---|---|
| Sprint 1 | 23 de septiembre al 2 de octubre | CRUD completo, frontend del módulo, Host App e integración: la segunda entrega |

Arrastra todas las tarjetas al sprint y dale **Iniciar sprint**.

**Los responsables, en bloque.** No las asignes una por una:

1. En el Backlog, filtra por la etiqueta de una persona (por ejemplo `hector`).
2. Selecciona todas (**Select all**) → **··· → Cambio masivo** (*Bulk change*) → **Editar** → campo **Persona asignada** → esa persona.
3. Repite con las siete etiquetas.

**Las que ya están hechas.** Pásalas de una vez a *Listo*: `B1 - Base del proyecto`, `Historia 6 - Listar categorias`, `Contrato de servicio v2.3`, `Contrato del Host App v1.0` y `Guias paso a paso`. Así el tablero refleja la realidad desde el primer día y no parece que no hubieran empezado.

## 6. Cómo se usa cada día (los siete)

Son tres momentos, y ya:

1. **Cuando empiezas una tarea:** arrastra su tarjeta a **En curso**. Anota su clave (`CAT-12`, por ejemplo): la vas a necesitar en el paso siguiente.
2. **Cuando abres el pull request:** pásala a **En revisión**.
3. **Cuando se une el PR:** pásala a **Listo**.

Si una tarea se atasca, **escribe un comentario en la tarjeta** diciendo qué la trabó. Es mejor que contarlo en WhatsApp, donde se pierde: en la sustentación el profesor puede abrir la tarjeta y ver la historia completa.

## 7. La clave del ticket en git

Esto es lo que amarra el tablero con el código. **La clave de la tarjeta va en tres sitios:**

| Dónde | Cómo se ve |
|---|---|
| El nombre de la rama | `CAT-12-b2-escritura` |
| El mensaje del commit | `CAT-12 Agrega POST /productos con validaciones` |
| El título del pull request | `CAT-12 — B2: POST y PUT de productos` |

Sin la clave, el tablero y el repositorio quedan contando dos historias distintas, y en la sustentación no hay forma de mostrar que la Historia 2 se resolvió con tal código. Con la clave, se abre la tarjeta y ahí está todo.

Para copiarla rápido: en el tablero, la clave sale a la izquierda del título de cada tarjeta.

## 8. Qué guardar para la entrega

Antes de la sustentación, toma capturas de:

- El **tablero** con las tarjetas repartidas entre los siete.
- El **backlog** mostrando las épicas con sus historias adentro.
- Una **tarjeta abierta** que tenga su rama y su pull request enlazados.
- El **informe del sprint** (*Sprint report*), en **Informes** (*Reports*), que muestra qué se completó.

Esas cuatro son la evidencia de que el proyecto se llevó con una metodología y no a las carreras.

## 9. Si algo falla

| Síntoma | Causa | Qué hacer |
|---|---|---|
| Las historias quedan sin épica | El espacio se creó *team-managed* | Bórralo y créalo *company-managed* (paso 2) |
| No existe el estado *En revisión* | No se agregó al crear el espacio | **Configuración del espacio → Estados** |
| La importación no reconoce el campo *Parent* | En español se llama **Principal** | Paso 4, tabla de campos |
| Se importaron 58 tarjetas en vez de 29 | Se importó dos veces | Borra todo con un cambio masivo y repite |
| Las búsquedas no muestran las tarjetas nuevas | Jira está reindexando | Espera, y mientras tanto usa el Backlog |
| Alguien no puede mover tarjetas | No quedó con rol de Administrador | **Configuración del espacio → Personas** |

---

_Última actualización: 2026-09-23_
