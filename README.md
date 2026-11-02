# Reservas del laboratorio

**Estado: T3b, ciclo de vida de reservas ejecutable.** La API permite consultar inventario, disponibilidad, crear, cancelar y recuperar reservas activas y canceladas en SQLite. La prueba de reinicio cubre dos contextos Spring con el mismo archivo; no se verificaron dos procesos del sistema operativo. El contrato está en [la especificación](docs/specification.md) y las tareas en [el plan](odd/tasks/lab-reservations.md).

## 1. Especificación breve

La API local usa Spring Boot, Java y SQLite para cinco unidades de laboratorio: dos microscopios digitales (`MIC-01`, `MIC-02`), un osciloscopio (`OSC-01`), un multímetro (`MUL-01`) y una fuente (`FUE-01`). Se consulta el inventario y la disponibilidad, se crean y cancelan reservas y se recuperan registros por ID o en listado.

Cada reserva tiene ID estable generado, unidad, solicitante ficticio, inicio, fin y estado `ACTIVA` o `CANCELADA`. Los intervalos son `[inicio, fin)` con fechas ISO 8601 y desplazamiento explícito, normalizadas a UTC. Se admiten fechas pasadas sin duración ni anticipación máximas. Solo las activas bloquean una unidad. Los requisitos `R1`–`R7` están en [docs/specification.md](docs/specification.md): R1–R5 y R7 están implementados y probados por HTTP; R6 se probó con dos contextos Spring consecutivos, no con dos procesos del sistema operativo.

## 2. Solución y ejecución

### Instalar e iniciar

Se necesitan Java 21 y Maven 3.9.12 (o Maven 3.9 compatible); las dependencias se descargan de Maven Central la primera vez. El `pom.xml` fija Spring Boot `3.5.9` y SQLite JDBC `3.49.1.0`. Desde la raíz del repositorio:

```sh
mvn -B test
mvn -B package
java -jar target/lab-reservations-0.1.0-SNAPSHOT.jar
```

En otra terminal, con el servidor en marcha:

```sh
curl http://localhost:8080/api/equipos
```

La respuesta es un arreglo JSON de cinco objetos `{ "id": "MIC-01", "tipo": "microscopio digital" }` (y `MIC-02`, `OSC-01`, `MUL-01`, `FUE-01` con sus respectivos tipos). La prueba automatizada realiza peticiones HTTP reales, comprueba los cinco objetos y reinicia el contexto apuntando al mismo archivo. Además, se inició manualmente el JAR dos veces con el mismo archivo SQLite temporal y se consultó `GET /api/equipos` con `curl`: ambas respuestas fueron HTTP 200 con los cinco IDs esperados. El arranque manual no prueba todavía persistencia de reservas.

### Consultar, reservar y cancelar

Con el servidor iniciado, usar instantes ISO 8601 con zona explícita (se comparan en UTC):

```sh
curl 'http://localhost:8080/api/equipos/MIC-01/disponibilidad?inicio=2026-11-03T10:00:00Z&fin=2026-11-03T11:00:00Z'
# {"disponible":true}
curl -X POST http://localhost:8080/api/reservas -H 'Content-Type: application/json' -d '{"equipoId":"MIC-01","solicitante":"Persona Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z"}'
# 200: {"id":"<UUID generado>","equipoId":"MIC-01","solicitante":"Persona Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z","estado":"ACTIVA"}
curl http://localhost:8080/api/reservas
# 200: arreglo con las reservas, incluida la anterior
```

Los ejemplos `curl` anteriores son para una terminal que ejecute `curl` real (por ejemplo, Git Bash). En Windows PowerShell, `curl` puede ser un alias de `Invoke-WebRequest`; para crear una reserva con PowerShell sin depender de cómo escape las comillas de `curl.exe`:

```powershell
$body = @{ equipoId = 'MIC-01'; solicitante = 'Persona Demo'; inicio = '2026-11-03T10:00:00Z'; fin = '2026-11-03T11:00:00Z' } | ConvertTo-Json
Invoke-RestMethod -Uri 'http://localhost:8080/api/reservas' -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

La disponibilidad devuelve `{"disponible":false}` si hay una activa que se superpone en esa unidad; intervalos consecutivos y unidades distintas son independientes. Equipo desconocido: 404; solicitante vacío, fechas inválidas/sin zona o inicio no anterior al fin: 400; intervalo ocupado al crear: 409. Los rechazos incluyen `{"error":"<mensaje>"}` y no insertan registros. La creación y la cancelación usan transacciones SQLite.

### Cancelación e historial

Sustituye el valor de `ID` por el `id` recibido al crear (UUID generado):

```sh
ID='pega-aqui-el-uuid-generado'
curl -X POST "http://localhost:8080/api/reservas/$ID/cancelacion"
# 200: mismo id, equipoId, solicitante, inicio y fin; "estado":"CANCELADA"
curl "http://localhost:8080/api/reservas/$ID"
# 200: registro conservado en estado CANCELADA
curl http://localhost:8080/api/reservas
# 200: arreglo con activas y canceladas, cada una con su propio ID
```

Al cancelar, el intervalo queda libre para una reserva nueva, con otro ID; las otras reservas activas continúan bloqueando. Buscar o cancelar un ID desconocido devuelve 404 `{"error":"Reserva desconocida"}`. Repetir la cancelación devuelve 409 `{"error":"Reserva ya cancelada"}` sin modificar registros. Estos errores y los de creación no agregan filas.

### Archivo y reinicio

La base predeterminada es `./lab-reservations.sqlite`, relativa al directorio desde donde se inicia Java; el archivo se crea al arrancar. El esquema `schema.sql` usa `CREATE TABLE IF NOT EXISTS`; `data.sql` inserta las cinco unidades solo si no existen. Spring ejecuta ambos en cada inicio porque `spring.sql.init.mode=always` (SQLite no es un datasource embebido reconocido por defecto). El inventario permanece sin duplicados al detener el proceso y ejecutar de nuevo el mismo `java -jar` desde el mismo directorio. Para usar otro archivo, define `LAB_DB_PATH` con una ruta a un archivo cuya carpeta ya exista antes del arranque; por ejemplo, en PowerShell: `$env:LAB_DB_PATH = './lab-reservations.sqlite'`. No cambies la ruta entre arranques si quieres conservar los datos.

**Reiniciar datos no es reiniciar el proceso:** para empezar con otra base, detén el servidor y elimina explícitamente el archivo SQLite (y cualquier archivo lateral SQLite asociado, si existiera) o elige una nueva ruta vacía. Borrar la base **también borra todas las reservas e historial**; no hagas esto para un reinicio normal. La prueba automática inicia y cierra dos contextos Spring sobre el mismo archivo temporal: recupera IDs, campos y estados de una activa y una cancelada, comprueba que la activa bloquea y la cancelada deja crear otra reserva. No se ejecutó un ensayo de dos procesos del sistema operativo con reservas; para comprobarlo manualmente, crea y cancela reservas con los comandos anteriores, detén Java y ejecuta de nuevo `java -jar` desde el mismo directorio sin cambiar `LAB_DB_PATH`; consulta los mismos IDs y la disponibilidad. Reiniciar no borra datos; comenzar desde cero sí. El directorio `target/` y la base predeterminada están excluidos de Git.

La aplicación tiene `LabApplication` como entrada. La lectura de inventario se organiza en `controller/EquipoController` (HTTP), `service/EquipoService` (caso de uso), `repository/EquipoRepository` (consulta SQL) y `model/Equipo` (dato de salida); el esquema y la carga inicial están en `src/main/resources/`. Las reservas se organizan en `ReservaController`, `ReservaService`, `ReservaRepository` y `Reserva`. El historial se conserva en la misma tabla de reservas; no existe endpoint de edición o borrado. Sin autenticación, roles reales, notificaciones, pagos ni despliegue de producción; usa únicamente solicitantes ficticios.

## 3. Preparación del agente

El trabajo se realiza con el harness Pi/el Gentleman y su proceso ODD: tareas versionadas, decisiones humanas y verificación antes de afirmar resultados. Las herramientas y permisos concretos, instrucciones saneadas, configuración/modelo efectivo, adaptación específica al problema y versión del entorno deberán documentarse con evidencia real en T4. No se requiere una cuenta de modelo para ejecutar la futura API. No se publicarán credenciales, transcripciones privadas completas ni datos personales de estudiantes.

## 4. Decisiones y delegación

- **Decisiones humanas registradas:** usar Spring Boot con SQLite y redactar la documentación del proyecto en español. El plan está en [odd/tasks/lab-reservations.md](odd/tasks/lab-reservations.md).
- **T1–T3:** el usuario aceptó T1 con «esta perfecto», cerró T2 con «ahh dalo por hecho no es necesario,» y eligió «Aceptar evidencia actual» para T3b. T3 pasó las pruebas HTTP funcionales; la suite actual tiene ocho pruebas. El reinicio con reservas se probó entre dos contextos Spring sobre el mismo archivo. El ensayo manual entre dos procesos Java no terminó tras ~19 minutos y se detuvo: se aceptó esa limitación, no se aprobó el ensayo. En T2, la verificación HTTP independiente adicional también había quedado incompleta (salidas 124 y 1) y fue dispensada, no aprobada.
- **Delegación:** los intentos anteriores de envío al escritor delegado fallaron antes de producir archivos. No cuentan como episodios completos ni como decisiones sobre resultados entregados. El registro de tres episodios reales (objetivo, contexto, salida, decisión humana y evidencia) queda pendiente de T4; no se inventan interacciones.
- **Publicación:** rama de trabajo local; no hay envío, pull request ni aceptación de entrega autorizados.

[Revisar requisitos y criterios de aceptación](docs/specification.md) · [Ver tareas y evidencia](odd/tasks/lab-reservations.md)
