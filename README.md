# Reservas del laboratorio

**Estado: T2, inventario ejecutable; reservas pendientes.** La API ya permite consultar el inventario fijo en SQLite. La disponibilidad, creación, cancelación e historial de reservas son trabajo futuro (T3). El contrato está en [la especificación](docs/specification.md) y las tareas en [el plan](odd/tasks/lab-reservations.md).

## 1. Especificación breve

La API local usa Spring Boot, Java y SQLite para cinco unidades de laboratorio: dos microscopios digitales (`MIC-01`, `MIC-02`), un osciloscopio (`OSC-01`), un multímetro (`MUL-01`) y una fuente (`FUE-01`). Actualmente se consulta el inventario; T3 añadirá disponibilidad, reservas, cancelación y conservación del historial.

En T3 cada reserva tendrá ID, unidad, solicitante ficticio, inicio, fin y estado. Los intervalos previstos son `[inicio, fin)` con fechas ISO 8601 y desplazamiento explícito. Solo las activas bloquearán una unidad. Los requisitos `R1`–`R7` están en [docs/specification.md](docs/specification.md): R1 está implementado; R6 solo tiene el inventario persistente, y los demás criterios siguen pendientes.

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

### Archivo y reinicio

La base predeterminada es `./lab-reservations.sqlite`, relativa al directorio desde donde se inicia Java; el archivo se crea al arrancar. El esquema `schema.sql` usa `CREATE TABLE IF NOT EXISTS`; `data.sql` inserta las cinco unidades solo si no existen. Spring ejecuta ambos en cada inicio porque `spring.sql.init.mode=always` (SQLite no es un datasource embebido reconocido por defecto). El inventario permanece sin duplicados al detener el proceso y ejecutar de nuevo el mismo `java -jar` desde el mismo directorio. Para usar otro archivo, define `LAB_DB_PATH` con una ruta a un archivo cuya carpeta ya exista antes del arranque; por ejemplo, en PowerShell: `$env:LAB_DB_PATH = './lab-reservations.sqlite'`. No cambies la ruta entre arranques si quieres conservar los datos.

**Reiniciar datos no es reiniciar el proceso:** para empezar con otra base, detén el servidor y elimina explícitamente el archivo SQLite (y cualquier archivo lateral SQLite asociado, si existiera) o elige una nueva ruta vacía. Borrar la base **también borrará todas las reservas e historial** cuando T3 los implemente; no hagas esto para un reinicio normal. El directorio `target/` y la base predeterminada están excluidos de Git.

La aplicación tiene `LabApplication` como entrada. La lectura de inventario se organiza en `controller/EquipoController` (HTTP), `service/EquipoService` (caso de uso), `repository/EquipoRepository` (consulta SQL) y `model/Equipo` (dato de salida); el esquema y la carga inicial están en `src/main/resources/`. Actualmente no hay rutas para crear, consultar disponibilidad, cancelar ni listar reservas; el ensayo de persistencia de reservas activas y canceladas de R6 queda para T3. Sin autenticación, roles reales, notificaciones, pagos ni despliegue de producción; usa únicamente solicitantes ficticios cuando T3 esté disponible.

## 3. Preparación del agente

El trabajo se realiza con el harness Pi/el Gentleman y su proceso ODD: tareas versionadas, decisiones humanas y verificación antes de afirmar resultados. Las herramientas y permisos concretos, instrucciones saneadas, configuración/modelo efectivo, adaptación específica al problema y versión del entorno deberán documentarse con evidencia real en T4. No se requiere una cuenta de modelo para ejecutar la futura API. No se publicarán credenciales, transcripciones privadas completas ni datos personales de estudiantes.

## 4. Decisiones y delegación

- **Decisiones humanas registradas:** usar Spring Boot con SQLite y redactar la documentación del proyecto en español. El plan está en [odd/tasks/lab-reservations.md](odd/tasks/lab-reservations.md).
- **T1 y T2:** el usuario aceptó explícitamente T1 con «esta perfecto» y luego cerró T2 con «ahh dalo por hecho no es necesario,». La prueba automatizada, el empaquetado y el arranque HTTP manual pasaron; la verificación HTTP independiente adicional quedó incompleta (salidas 124 y 1) y fue dispensada expresamente, no aprobada.
- **Delegación:** los intentos anteriores de envío al escritor delegado fallaron antes de producir archivos. No cuentan como episodios completos ni como decisiones sobre resultados entregados. El registro de tres episodios reales (objetivo, contexto, salida, decisión humana y evidencia) queda pendiente de T4; no se inventan interacciones.
- **Publicación:** rama de trabajo local; no hay envío, pull request ni aceptación de entrega autorizados.

[Revisar requisitos y criterios de aceptación](docs/specification.md) · [Ver tareas y evidencia](odd/tasks/lab-reservations.md)
