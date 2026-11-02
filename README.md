# Reservas del laboratorio

**Estado: T1, especificación inicial.** Este repositorio todavía no contiene la aplicación, comandos de ejecución ni resultados de pruebas funcionales. El contrato propuesto está en [la especificación](docs/specification.md) y las tareas y evidencias pendientes en [el plan](odd/tasks/lab-reservations.md).

## 1. Especificación breve

Se proyecta una API local con Spring Boot, Java y SQLite para cinco unidades de laboratorio: dos microscopios digitales (`MIC-01`, `MIC-02`), un osciloscopio (`OSC-01`), un multímetro (`MUL-01`) y una fuente (`FUE-01`). Permitirá consultar disponibilidad, crear reservas con solicitante ficticio, cancelar sin borrar historial y recuperar el estado después de reiniciar el proceso sin eliminar la base.

Cada reserva tendrá un ID, unidad, solicitante, inicio, fin y estado. Los intervalos serán `[inicio, fin)` con fechas ISO 8601 y desplazamiento explícito. Solo las reservas activas bloquearán una unidad: horarios consecutivos y unidades distintas podrán coexistir. Los requisitos `R1`–`R7`, rechazos y escenarios esperados están en [docs/specification.md](docs/specification.md). **Son criterios pendientes de implementar y verificar.**

## 2. Solución y ejecución

| Tema | Situación en T1 |
| --- | --- |
| Arquitectura propuesta | API Spring Boot (Java 21), base SQLite local en archivo. La estructura de código y las dependencias se decidirán y verificarán en T2. |
| Requisitos locales | Java 21 y Maven 3.9.12 se observaron disponibles durante la planificación; no se verificaron aún dependencias ni compilación en esta versión. |
| Instalación y carga inicial | Pendientes de T2: todavía no hay proyecto ejecutable ni comando de inicio o precarga probado. |
| Datos y reinicio | Pendiente de T2: se documentarán ruta de la base, cómo arrancar desde cero y cómo reiniciar el proceso conservando reservas; no confundir reinicio de proceso con borrado de datos. |
| API y ejemplos de entrada/salida | Contrato y escenarios propuestos en [la especificación](docs/specification.md); no hay respuesta real de servidor para mostrar. |
| Comprobación | Pendiente de T2–T3: compilación, pruebas de reglas y ensayo de persistencia entre procesos. |

Limitaciones previstas: sin autenticación, roles reales, notificaciones, pagos, integraciones externas, interfaz gráfica compleja ni despliegue obligatorio. El inventario es fijo y el solicitante debe ser ficticio. No uses este documento como instrucción de instalación de una aplicación ya disponible.

## 3. Preparación del agente

El trabajo se realiza con el harness Pi/el Gentleman y su proceso ODD: tareas versionadas, decisiones humanas y verificación antes de afirmar resultados. Las herramientas y permisos concretos, instrucciones saneadas, configuración/modelo efectivo, adaptación específica al problema y versión del entorno deberán documentarse con evidencia real en T4. No se requiere una cuenta de modelo para ejecutar la futura API. No se publicarán credenciales, transcripciones privadas completas ni datos personales de estudiantes.

## 4. Decisiones y delegación

- **Decisiones humanas registradas:** usar Spring Boot con SQLite y redactar la documentación del proyecto en español. El plan está en [odd/tasks/lab-reservations.md](odd/tasks/lab-reservations.md).
- **T1:** se redactan ahora el contrato y este punto de entrada. Su aceptación humana y los resultados de la revisión todavía están pendientes; no se atribuye al usuario una aceptación implícita.
- **Delegación:** los intentos anteriores de envío al escritor delegado fallaron antes de producir archivos. No cuentan como episodios completos ni como decisiones sobre resultados entregados. El registro de tres episodios reales (objetivo, contexto, salida, decisión humana y evidencia) queda pendiente de T4; no se inventan interacciones.
- **Publicación:** rama de trabajo local; no hay envío, pull request ni aceptación de entrega autorizados.

[Revisar requisitos y criterios de aceptación](docs/specification.md) · [Ver tareas y evidencia](odd/tasks/lab-reservations.md)
