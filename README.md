# Reservas del laboratorio

**Estado de la entrega local:** T1–T3 están cerradas y T4 documenta la evidencia disponible. La API permite consultar inventario y disponibilidad, crear reservas, cancelarlas sin borrar su historial y recuperar los datos desde SQLite. La suite actual tiene **8 pruebas**, todas aprobadas en la comprobación del padre del 2 de noviembre de 2026. La persistencia de reservas está comprobada entre dos contextos Spring que usan el mismo archivo, pero no entre dos procesos Java del sistema operativo.

El contrato completo está en [la especificación](docs/specification.md) y la cronología de tareas, decisiones y límites está en [el plan ODD](odd/tasks/lab-reservations.md).

## 1. Especificación breve

### Qué resuelve

La API local administra la reserva de cinco unidades físicas de laboratorio:

| ID | Tipo |
| --- | --- |
| `MIC-01` | microscopio digital |
| `MIC-02` | microscopio digital |
| `OSC-01` | osciloscopio |
| `MUL-01` | multímetro |
| `FUE-01` | fuente de alimentación |

El inventario es fijo y no tiene operaciones de alta, edición o baja. Cada reserva tiene un ID generado, unidad, solicitante ficticio, inicio, fin y estado `ACTIVA` o `CANCELADA`. La cancelación conserva el registro original y libera su intervalo.

### Reglas principales

- Los instantes se reciben en ISO 8601 con desplazamiento explícito, por ejemplo `2026-11-03T10:00:00Z` o `2026-11-03T07:00:00-03:00`, y se normalizan a UTC.
- Los intervalos son semiabiertos: `[inicio, fin)`. Por eso una reserva puede comenzar exactamente cuando termina otra.
- `inicio` debe ser estrictamente anterior a `fin`.
- Se admiten fechas pasadas; no hay duración ni anticipación máximas.
- Solo una reserva `ACTIVA` bloquea su unidad. Una `CANCELADA` queda en el historial, pero no bloquea.
- La superposición se evalúa por unidad: dos equipos distintos pueden reservarse en simultáneo.
- Un rechazo no debe crear filas, cambiar estados ni alterar la disponibilidad existente.
- El solicitante debe ser ficticio: esta solución no necesita ni debe almacenar datos reales de estudiantes.

### Operaciones HTTP

| Operación | Resultado esperado |
| --- | --- |
| `GET /api/equipos` | Las cinco unidades, sin duplicados. |
| `GET /api/equipos/{id}/disponibilidad?inicio=...&fin=...` | `{"disponible":true}` o `false` según las mismas reglas usadas al crear. |
| `POST /api/reservas` | Crea una reserva `ACTIVA` y devuelve su ID y datos. |
| `GET /api/reservas` | Lista reservas activas y canceladas. |
| `GET /api/reservas/{id}` | Recupera el registro por ID, cualquiera sea su estado. |
| `POST /api/reservas/{id}/cancelacion` | Conserva los datos y cambia `ACTIVA` a `CANCELADA`. |

Los errores distinguen entrada inválida (`400`), recurso desconocido (`404`) y conflicto de ocupación o estado (`409`). El detalle normativo y los criterios de aceptación `R1`–`R7` están en [docs/specification.md](docs/specification.md).

### Cobertura y límite conocido

`R1`–`R5` y `R7` están cubiertos por pruebas HTTP. `R6` está cubierto parcialmente por [ReservationRestartHttpTest](src/test/java/org/example/lab/ReservationRestartHttpTest.java): cierra un contexto Spring, abre otro sobre el mismo archivo SQLite y recupera una reserva activa y otra cancelada. Eso comprueba persistencia entre dos contextos dentro de una JVM; **no equivale** a detener un proceso Java y arrancar otro. El smoke de dos procesos quedó inconcluso y su resultado no se presenta como satisfactorio.

## 2. Solución y ejecución

### Tecnologías y entorno comprobado

| Componente | Versión o decisión |
| --- | --- |
| Sistema observado al documentar T4 | Windows 11, `amd64` |
| Java | `21.0.9` LTS |
| Maven | `3.9.12` |
| Spring Boot | `3.5.9` |
| SQLite JDBC | `3.49.1.0` |
| Persistencia | Archivo SQLite local |
| API | Spring Web + JDBC; JSON sobre HTTP |

Java y Maven se comprobaron en T4 con `java -version` y `mvn -version`. Las versiones de Spring Boot y SQLite JDBC están fijadas en [`pom.xml`](pom.xml).

### Estructura

```text
src/main/java/org/example/lab/
├── LabApplication.java
├── controller/   # rutas HTTP y forma de errores
├── service/      # reglas y casos de uso
├── repository/   # SQL, transacciones y persistencia
└── model/        # datos de equipos y reservas
src/main/resources/
├── application.properties
├── schema.sql
└── data.sql
src/test/java/org/example/lab/
├── InventoryHttpTest.java
├── ReservationHttpTest.java
├── ReservationLifecycleHttpTest.java
└── ReservationRestartHttpTest.java
```

La separación `controller`, `service`, `repository` y `model` responde a una decisión humana de T2, sin agregar capas que el proyecto no necesitaba. El inventario y las reservas comparten el mismo archivo. El esquema usa creación condicional y la carga inicial inserta cada equipo solo si aún no existe.

### Instalar, probar y arrancar

Desde la raíz del repositorio:

```sh
mvn -B test
mvn -B package
java -jar target/lab-reservations-0.1.0-SNAPSHOT.jar
```

La primera ejecución de Maven puede descargar dependencias desde Maven Central. La aplicación no necesita una cuenta del modelo ni el harness del agente para compilarse o ejecutarse.

Con el servidor activo, en otra terminal:

```sh
curl http://localhost:8080/api/equipos
```

Resultado esperado: HTTP `200` y un arreglo con los cinco pares ID/tipo de la tabla anterior.

### Ejemplo reproducible

Estos ejemplos suponen un `curl` real, como el de Git Bash:

```sh
curl 'http://localhost:8080/api/equipos/MIC-01/disponibilidad?inicio=2026-11-03T10:00:00Z&fin=2026-11-03T11:00:00Z'
# 200: {"disponible":true}

curl -X POST http://localhost:8080/api/reservas \
  -H 'Content-Type: application/json' \
  -d '{"equipoId":"MIC-01","solicitante":"Persona Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z"}'
# 200: {"id":"<UUID generado>","equipoId":"MIC-01","solicitante":"Persona Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z","estado":"ACTIVA"}

curl http://localhost:8080/api/reservas
# 200: arreglo que incluye la reserva anterior
```

En PowerShell, `curl` puede ser un alias de `Invoke-WebRequest`. Esta variante evita depender del escape de comillas de `curl.exe`:

```powershell
$body = @{
  equipoId = 'MIC-01'
  solicitante = 'Persona Demo'
  inicio = '2026-11-03T10:00:00Z'
  fin = '2026-11-03T11:00:00Z'
} | ConvertTo-Json

$reserva = Invoke-RestMethod -Uri 'http://localhost:8080/api/reservas' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$reserva
```

Para cancelar y recuperar el registro, usa el ID realmente devuelto:

```sh
ID='pega-aqui-el-uuid-generado'
curl -X POST "http://localhost:8080/api/reservas/$ID/cancelacion"
# 200: mismos datos e id; "estado":"CANCELADA"

curl "http://localhost:8080/api/reservas/$ID"
# 200: el registro cancelado sigue presente
```

Después de cancelar, el mismo intervalo queda disponible y puede recibir una reserva nueva con otro ID. Un ID inexistente devuelve `404 {"error":"Reserva desconocida"}`. Una segunda cancelación devuelve `409 {"error":"Reserva ya cancelada"}` sin cambiar el registro.

### Archivo SQLite, reinicio y limpieza

La configuración está en [`application.properties`](src/main/resources/application.properties). De forma predeterminada, la base es:

```text
./lab-reservations.sqlite
```

La ruta es relativa al directorio desde el cual se inicia Java. Para elegir otro archivo, define `LAB_DB_PATH`; la carpeta contenedora debe existir:

```powershell
$env:LAB_DB_PATH = './datos/lab-reservations.sqlite'
java -jar target/lab-reservations-0.1.0-SNAPSHOT.jar
```

Reiniciar el proceso con la misma ruta no debe borrar ni duplicar datos. **Reiniciar datos es otra operación:** con el servidor detenido, borrar el archivo SQLite —y cualquier archivo lateral asociado— elimina inventario cargado, reservas e historial; en el arranque siguiente se recrean el esquema y el inventario fijo. No borres la base para un reinicio normal.

Procedimiento manual pendiente para completar el límite de `R6`:

1. Arrancar el JAR con un `LAB_DB_PATH` conocido.
2. Crear una reserva activa y otra que luego se cancele; guardar ambos IDs ficticios.
3. Detener completamente el proceso Java.
4. Arrancar un proceso Java nuevo con el mismo `LAB_DB_PATH`.
5. Consultar ambos IDs y la disponibilidad: la activa debe bloquear y la cancelada no.

Este procedimiento está especificado, pero su ejecución entre dos procesos no terminó satisfactoriamente durante T3b. No se atribuye un resultado que no fue observado.

### Pruebas actuales y resultados reales

La suite actual contiene **8 métodos `@Test`** en cuatro archivos:

| Archivo | Pruebas | Qué comprueban |
| --- | ---: | --- |
| [InventoryHttpTest](src/test/java/org/example/lab/InventoryHttpTest.java) | 1 | Cinco unidades exactas, peticiones HTTP y dos inicios de contexto sobre el mismo SQLite. |
| [ReservationHttpTest](src/test/java/org/example/lab/ReservationHttpTest.java) | 2 | Creación, listado, disponibilidad, límites consecutivos, unidades distintas, zonas y rechazos sin mutación. |
| [ReservationLifecycleHttpTest](src/test/java/org/example/lab/ReservationLifecycleHttpTest.java) | 4 | Cancelación, liberación, historial, búsqueda, IDs desconocidos, doble cancelación y superposición. |
| [ReservationRestartHttpTest](src/test/java/org/example/lab/ReservationRestartHttpTest.java) | 1 | Activa y cancelada conservadas entre dos contextos con el mismo archivo. |

Resultado más reciente observado por el padre, sobre el árbol actual:

```text
Comando: mvn -B test
Fecha:   2026-11-02 05:53:11 -03
Salida:  0
Tests:   8; fallos: 0; errores: 0; omitidos: 0
Maven:   BUILD SUCCESS
```

Esta ejecución de T4 es una comprobación del **padre**, no un resultado histórico atribuido a los agentes que implementaron T1–T3.

Evidencia histórica conservada en el [Log del plan](odd/tasks/lab-reservations.md#log):

- T2: el escritor informó RED por `404` antes de implementar y luego GREEN con 1 prueba, además de `mvn -B package` satisfactorio. El padre repitió `mvn -B test` con 1 prueba y observó dos arranques manuales del JAR con HTTP `200` y los cinco IDs.
- T3a: el escritor informó RED por rutas ausentes y GREEN focalizado con 2 pruebas; también informó suite y package satisfactorios con 4 pruebas. El padre repitió la suite con salida `0`; una verificación independiente focalizada también salió `0`.
- T3b: el escritor informó 3 de 4 pruebas focalizadas fallando por rutas ausentes antes de implementar y luego 4 de 4 aprobadas; informó suite y package con 9 pruebas. El padre repitió la suite con salida `0`; el verificador observó la suite aislada y pruebas focalizadas satisfactorias.
- Después, por decisión humana, se retiraron dos pruebas dedicadas a concurrencia sin quitar la protección transaccional del código. `mvn -B -Dtest=ReservationHttpTest,ReservationLifecycleHttpTest test && mvn -B test` salió `0` y dejó las 8 pruebas actuales. El ajuste está en `5b63bfe`.

Los resultados informados por escritores están rotulados como **informes del agente**. Las repeticiones, smokes y límites están rotulados como **observaciones del padre o del verificador**; no se mezclan ambas fuentes.

### Limitaciones

- No hay autenticación, roles reales, notificaciones, pagos, integraciones externas, interfaz gráfica ni despliegue de producción.
- No hay endpoint para editar o borrar reservas; la cancelación conserva historial.
- No se hizo prueba de carga ni se afirma escalabilidad de producción.
- La protección transaccional SQLite se conserva, pero las pruebas específicas de carreras se retiraron por decisión humana; la suite actual se concentra en reglas funcionales.
- No se completó el smoke de reservas entre dos procesos Java.
- La aplicación usa solicitantes ficticios y no está diseñada para datos personales reales.

## 3. Preparación del agente

### Harness, función y proceso

El trabajo se realizó en **Pi/el Gentleman** con el flujo ODD (Organic Driven Development): el plan versionado delimita tareas, cada unidad tiene evidencia y las decisiones humanas cierran o mantienen abiertas las tareas. El harness coordinó dos funciones:

- **Padre/orquestador:** mantuvo el contexto, autorizaciones y alcance; repitió comprobaciones; distinguió informes delegados de resultados observados; y conservó las decisiones humanas.
- **Escritor delegado:** implementó unidades acotadas de T2, T3a y T3b y devolvió cambios y resultados de sus comandos. Sus informes no se trataron como comprobación independiente hasta que el padre o un verificador repitió la evidencia.

T1 terminó redactándose inline después de que varios intentos de escritor fallaran antes de usar herramientas. Esos intentos no modificaron archivos y no se cuentan como episodios de entrega. T4 es una sola unidad documental y solo modifica este README.

### Modelo: evidencia disponible y límite

El entorno de orquestación al iniciar T4 informó `PI_MODEL=gpt-6-sol`. Ese dato solo identifica el modelo visible en la sesión actual de T4: **no prueba qué modelo usaron los escritores de T1, T2 o T3**. No se encontró una configuración histórica versionada que permita atribuirles un ID, por lo que este README no inventa modelos anteriores ni les asigna el modelo actual.

La API resultante no depende del modelo ni de una cuenta de IA para ejecutarse.

### Instrucciones externas saneadas

Extracto de la instrucción aplicada a esta unidad T4, sin rutas personales ni configuración privada:

> «Complete T4 documentation only.»
>
> «Write only README.md, keeping four numbered sections, Spanish project prose.»
>
> «Do not invent prompts, historical model IDs, acceptance, or results.»
>
> «Do not edit task plan or source. No commit.»

Además se aplicaron dos guías del harness: diseño documental para priorizar lectura rápida y evidencia verificable, y unidades de trabajo para mantener este cambio como un bloque documental acotado. El extracto anterior corresponde a T4; no se presenta como una reconstrucción de los prompts históricos de implementación.

### Contexto incluido y excluido

**Incluido para T4:**

- [especificación `R1`–`R7`](docs/specification.md);
- [plan y Log `L1`–`L20`](odd/tasks/lab-reservations.md#log);
- README previo, `pom.xml`, configuración y las cuatro clases de prueba actuales;
- commits de entrega `6b3b91e` (T1), `b206a9d` (T2), `99cf479` (T3a), `95b3139` (T3b) y `5b63bfe` (ajuste de alcance);
- versiones locales de Java/Maven y el resultado de pruebas comunicado por el padre.

**Excluido deliberadamente:**

- secretos, credenciales, tokens, archivos `.env` y configuración privada del usuario;
- conversaciones privadas completas o supuestas transcripciones de prompts históricos;
- datos reales de estudiantes;
- archivos no relacionados, incluida la carpeta local `.idea/`;
- publicación, push, pull request y cualquier aceptación de entrega no concedida.

### Herramientas y permisos de esta unidad

El agente pudo leer archivos del repositorio, inspeccionar historial Git, consultar versiones del entorno, editar `README.md` y ejecutar comprobaciones documentales. El permiso de escritura quedó limitado a `README.md`. No tuvo autorización para modificar código, especificación, plan, dependencias, configuración del proyecto, archivos de IDE ni historial Git; tampoco para hacer commit, push o publicar.

### Adaptación específica basada en evidencia

La documentación se adaptó a dos decisiones registradas, en vez de conservar una narrativa genérica:

1. Tras `L17`–`L18`, las pruebas de concurrencia dejaron de formar parte del alcance actual. Por eso el README enumera ocho pruebas funcionales, no diez, y aclara que `BEGIN IMMEDIATE` permanece en el código sin presentar los probes históricos de carrera como cobertura vigente.
2. Tras `L16`, se separó explícitamente “dos contextos Spring en una JVM” de “dos procesos Java”. La primera comprobación es evidencia positiva enlazada; el smoke de procesos queda como limitación, no como éxito implícito.

Estas adaptaciones se pueden rastrear al [Log](odd/tasks/lab-reservations.md#log), a los tests actuales y al commit `5b63bfe`.

### Privacidad y publicación

Los ejemplos usan `Persona Demo` y fechas fijas; no contienen personas reales, credenciales ni bases de datos. Este README resume decisiones con citas breves, no publica conversaciones completas. El trabajo permanece en una rama local: no se hizo push, no se abrió pull request y no hay autorización para publicar el repositorio. Se eligió preparar una cadena de revisión desde una rama de seguimiento por el tamaño del cambio; esos cortes aún no son PRs. La preparación técnica no equivale a aceptación ni publicación.

## 4. Decisiones y delegación

### Episodio 1 — T1: contrato inicial aceptado

| Paso | Evidencia real |
| --- | --- |
| Objetivo/requisito | Definir `R1`–`R7`, decisiones temporales, criterios de aceptación y un README con cuatro secciones antes de implementar. |
| Contexto e instrucción | El usuario pidió «hace el t1». El trabajo tomó el alcance Spring Boot + SQLite, documentación en español y la necesidad de no simular una solución terminada. |
| Resultado del agente | Tras fallos de delegación sin cambios, el padre redactó inline la especificación y el README inicial. El resultado quedó en `6b3b91e` (`docs(lab): define initial reservation specification`). La comprobación estructural encontró `R1`–`R7`, criterios, cuatro secciones y enlaces; `git diff --check` no reportó errores. No hubo pruebas de producto porque T1 era documentación pasiva. |
| Decisión humana | **Aceptar:** «esta perfecto». La aceptación cerró T1; no aprobó T2 ni publicación. |
| Versión entregada | [Especificación](docs/specification.md), [plan](odd/tasks/lab-reservations.md) y commit `6b3b91e`; aceptación registrada en `L2` y evidencia posterior en `1bba904`. |

**Separación de fuentes:** los intentos fallidos del escritor solo informaron error y cero herramientas; no cuentan como resultado. Las comprobaciones estructurales fueron observadas por el padre y la aceptación fue decisión del usuario.

### Episodio 2 — T2: API de inventario aceptada con comprobación independiente incompleta

| Paso | Evidencia real |
| --- | --- |
| Objetivo/requisito | Crear el proyecto ejecutable, persistencia SQLite, inventario fijo, inicio/reinicio y separación `repository`, `controller`, `service`, `model` cuando fuera necesaria. |
| Contexto e instrucción | El usuario autorizó T2, luego pidió reorganizar responsabilidades y continuar. También pidió inicialmente exigir una comprobación independiente HTTP completa. |
| Resultado del agente | El escritor entregó Spring Boot `3.5.9`, SQLite JDBC `3.49.1.0`, esquema/carga y `GET /api/equipos`; informó RED `404`, GREEN con 1 prueba y package satisfactorio. El padre observó `mvn -B test` con 1 prueba y un JAR iniciado dos veces sobre el mismo SQLite, ambas con HTTP `200` y cinco IDs. Después del refactor, el padre volvió a observar test/package y un smoke de inventario satisfactorios. La entrega está en `b206a9d` (`feat(lab): add persistent inventory API`). |
| Decisión humana | **Aceptar con excepción:** «ahh dalo por hecho no es necesario,». El usuario levantó la exigencia de una comprobación independiente adicional; no convirtió los intentos incompletos en aprobados. |
| Versión entregada | [InventoryHttpTest](src/test/java/org/example/lab/InventoryHttpTest.java), [`pom.xml`](pom.xml), [configuración SQLite](src/main/resources/application.properties), commit `b206a9d`; decisión y resultados en `L6`–`L10`. |

**Separación de fuentes:** el RED/GREEN y package iniciales fueron informados por el escritor. El test repetido, los dos HTTP `200` y el smoke posterior fueron observados por el padre. Las dos comprobaciones independientes no cerraron: una terminó con salida `124` y otra con salida `1` por acceso al SQLite abierto en Windows; se registran como incompletas, no exitosas.

### Episodio 3 — T3b: ciclo de vida aceptado con smoke entre procesos inconcluso

| Paso | Evidencia real |
| --- | --- |
| Objetivo/requisito | Agregar cancelación sin borrado, búsqueda por ID, historial, rechazos restantes y persistencia de reservas después de reiniciar. |
| Contexto e instrucción | T3a ya había entregado disponibilidad, creación y listado. T3b debía completar `R4`–`R6` y `R7`, preservando las reglas existentes y distinguiendo el reinicio comprobado del pendiente. |
| Resultado del agente | El escritor informó RED focalizado: 3 de 4 pruebas fallaron por rutas ausentes; luego GREEN 4/4 y suite/package con 9 pruebas. El padre repitió `mvn -B test` con salida `0`. El verificador observó prueba focalizada y suite aislada satisfactorias. [ReservationRestartHttpTest](src/test/java/org/example/lab/ReservationRestartHttpTest.java) recuperó activa y cancelada en dos contextos con el mismo archivo. El smoke manual entre dos procesos permaneció ejecutándose unos 19 minutos y se detuvo sin salida de éxito. La entrega está en `95b3139` (`feat(lab): preserve reservation history on cancellation`). |
| Decisión humana | **Aceptar evidencia actual:** el usuario eligió «Aceptar evidencia actual» conociendo expresamente la diferencia entre dos contextos Spring y dos procesos Java. |
| Versión entregada | [ReservationLifecycleHttpTest](src/test/java/org/example/lab/ReservationLifecycleHttpTest.java), [ReservationRestartHttpTest](src/test/java/org/example/lab/ReservationRestartHttpTest.java), commit `95b3139`; resultado y decisión en `L14`–`L16`. |

**Separación de fuentes:** el RED/GREEN y package fueron informes del escritor. La repetición Maven fue del padre; los checks focalizados fueron del verificador. La persistencia en dos contextos es un resultado automatizado real. El smoke de procesos es incompleto y la decisión humana acepta esa limitación, no un resultado inexistente.

### Decisiones posteriores que afectan la versión actual

- El usuario pidió retirar las validaciones concurrentes porque la suficiencia se juzgaría por los requisitos funcionales; luego eligió exactamente “Solo pruebas y referencias a concurrencia”.
- Se eliminaron dos tests dedicados a carreras, se conservaron las transacciones SQLite y la suite actual quedó en 8 pruebas. El commit de ajuste es `5b63bfe`.
- Las comprobaciones históricas de concurrencia siguen en el Log como hechos ejecutados en aquel momento, pero no se presentan como alcance de prueba actual.
- T4 fue autorizado con «vamos por t4» y retomado con «nos quedamos en t4» (`L19`–`L20`). Eso autoriza completar y registrar T4 en la rama local; no autoriza push, PR ni entrega pública.

### Trazabilidad rápida

| Unidad | Commit de producto/documentación | Evidencia principal | Decisión humana |
| --- | --- | --- | --- |
| T1 | `6b3b91e` | [Especificación](docs/specification.md) | «esta perfecto» |
| T2 | `b206a9d` | [InventoryHttpTest](src/test/java/org/example/lab/InventoryHttpTest.java) | «ahh dalo por hecho no es necesario,» |
| T3a | `99cf479` | [ReservationHttpTest](src/test/java/org/example/lab/ReservationHttpTest.java) | Continuación autorizada dentro de T3 |
| T3b | `95b3139` | [Lifecycle](src/test/java/org/example/lab/ReservationLifecycleHttpTest.java) y [restart](src/test/java/org/example/lab/ReservationRestartHttpTest.java) | «Aceptar evidencia actual» |
| Ajuste de pruebas | `5b63bfe` | Suite funcional actual de 8 pruebas | “Solo pruebas y referencias a concurrencia” |
| T4 | `0142400` y commit de cierre del plan | Este README y checks documentales | «vamos por t4» / «nos quedamos en t4» |

Para auditar detalles, resultados fallidos y límites sin reconstruir conversaciones, consultar el [Log versionado del plan](odd/tasks/lab-reservations.md#log). La entrega permanece local y no publicada.
