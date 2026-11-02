# Reservas del laboratorio

**Estado: T3a, disponibilidad, creación y listado ejecutables.** La API permite consultar el inventario y reservar unidades en SQLite. Cancelación, búsqueda por ID y comprobación de persistencia de reservas tras reiniciar quedan pendientes de T3b. El contrato está en [la especificación](docs/specification.md) y las tareas en [el plan](odd/tasks/lab-reservations.md).

## 1. Especificación breve

La API local usa Spring Boot, Java y SQLite para cinco unidades de laboratorio: dos microscopios digitales (`MIC-01`, `MIC-02`), un osciloscopio (`OSC-01`), un multímetro (`MUL-01`) y una fuente (`FUE-01`). Se consulta el inventario, la disponibilidad, se crean reservas y se listan registros; cancelar y recuperar un registro por ID quedan pendientes de T3b.

Cada reserva tiene ID estable generado, unidad, solicitante ficticio, inicio, fin y estado `ACTIVA`. Los intervalos son `[inicio, fin)` con fechas ISO 8601 y desplazamiento explícito, normalizadas a UTC. Se admiten fechas pasadas sin duración ni anticipación máximas. Solo las activas bloquean una unidad. Los requisitos `R1`–`R7` están en [docs/specification.md](docs/specification.md): R2, R3 y el listado de R5 están implementados; R7 parcialmente; R4 y el resto de R5–R7 quedan pendientes.

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

### Consultar y reservar (T3a)

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

La disponibilidad devuelve `{"disponible":false}` si hay una activa que se superpone en esa unidad; intervalos consecutivos y unidades distintas son independientes. Equipo desconocido: 404; solicitante vacío, fechas inválidas/sin zona o inicio no anterior al fin: 400; intervalo ocupado al crear: 409. Los rechazos incluyen `{"error":"<mensaje>"}` y no insertan registros. La creación serializa la comprobación y la inserción mediante una transacción SQLite `BEGIN IMMEDIATE` con espera acotada. No hay endpoints de cancelación ni consulta por ID todavía.

### Archivo y reinicio

La base predeterminada es `./lab-reservations.sqlite`, relativa al directorio desde donde se inicia Java; el archivo se crea al arrancar. El esquema `schema.sql` usa `CREATE TABLE IF NOT EXISTS`; `data.sql` inserta las cinco unidades solo si no existen. Spring ejecuta ambos en cada inicio porque `spring.sql.init.mode=always` (SQLite no es un datasource embebido reconocido por defecto). El inventario permanece sin duplicados al detener el proceso y ejecutar de nuevo el mismo `java -jar` desde el mismo directorio. Para usar otro archivo, define `LAB_DB_PATH` con una ruta a un archivo cuya carpeta ya exista antes del arranque; por ejemplo, en PowerShell: `$env:LAB_DB_PATH = './lab-reservations.sqlite'`. No cambies la ruta entre arranques si quieres conservar los datos.

**Reiniciar datos no es reiniciar el proceso:** para empezar con otra base, detén el servidor y elimina explícitamente el archivo SQLite (y cualquier archivo lateral SQLite asociado, si existiera) o elige una nueva ruta vacía. Borrar la base **también borra todas las reservas e historial**; no hagas esto para un reinicio normal. La comprobación automática del reinicio con reservas activas y canceladas queda pendiente de T3b. El directorio `target/` y la base predeterminada están excluidos de Git.

La aplicación tiene `LabApplication` como entrada. La lectura de inventario se organiza en `controller/EquipoController` (HTTP), `service/EquipoService` (caso de uso), `repository/EquipoRepository` (consulta SQL) y `model/Equipo` (dato de salida); el esquema y la carga inicial están en `src/main/resources/`. Las reservas se organizan en `ReservaController`, `ReservaService`, `ReservaRepository` y `Reserva`. Cancelación, búsqueda por ID y ensayo de persistencia de reservas activas y canceladas de R6 quedan para T3b. Sin autenticación, roles reales, notificaciones, pagos ni despliegue de producción; usa únicamente solicitantes ficticios.

## 3. Preparación del agente

El trabajo se realiza con el harness Pi/el Gentleman y su proceso ODD: tareas versionadas, decisiones humanas y verificación antes de afirmar resultados. Las herramientas y permisos concretos, instrucciones saneadas, configuración/modelo efectivo, adaptación específica al problema y versión del entorno deberán documentarse con evidencia real en T4. No se requiere una cuenta de modelo para ejecutar la futura API. No se publicarán credenciales, transcripciones privadas completas ni datos personales de estudiantes.

## 4. Decisiones y delegación

- **Decisiones humanas registradas:** usar Spring Boot con SQLite y redactar la documentación del proyecto en español. El plan está en [odd/tasks/lab-reservations.md](odd/tasks/lab-reservations.md).
- **T1 y T2:** el usuario aceptó explícitamente T1 con «esta perfecto» y luego cerró T2 con «ahh dalo por hecho no es necesario,». La prueba automatizada, el empaquetado y el arranque HTTP manual pasaron; la verificación HTTP independiente adicional quedó incompleta (salidas 124 y 1) y fue dispensada expresamente, no aprobada.
- **Delegación:** los intentos anteriores de envío al escritor delegado fallaron antes de producir archivos. No cuentan como episodios completos ni como decisiones sobre resultados entregados. El registro de tres episodios reales (objetivo, contexto, salida, decisión humana y evidencia) queda pendiente de T4; no se inventan interacciones.
- **Publicación:** rama de trabajo local; no hay envío, pull request ni aceptación de entrega autorizados.

[Revisar requisitos y criterios de aceptación](docs/specification.md) · [Ver tareas y evidencia](odd/tasks/lab-reservations.md)
