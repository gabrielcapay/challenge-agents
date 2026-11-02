# Especificación inicial: reservas del laboratorio

**Estado:** contrato inicial aceptado en T1. T2 implementó el inventario (`R1`); T3a implementó disponibilidad (`R2`) y creación (`R3`); T3b agregó cancelación (`R4`), consulta/historial (`R5`) y rechazos restantes (`R7`). T4 documenta la evidencia en el README. La persistencia de reservas (`R6`) se comprobó entre dos contextos Spring que usaron el mismo archivo; el ensayo entre procesos del sistema operativo no concluyó y el usuario aceptó cerrar T3 con esa limitación.

**Objetivo:** consultar disponibilidad y reservar unidades del inventario del laboratorio, cancelar sin perder el registro y recuperar los datos después de reiniciar la aplicación.

**Lectura rápida:** los requisitos `R1`–`R7` describen resultados observables; las pruebas HTTP comprobaron inventario, disponibilidad, creación, cancelación, historial y rechazos. La prueba de reinicio recuperó activas y canceladas entre contextos; falta la prueba entre procesos Java separados.

## Modelo y decisiones temporales

| Concepto | Decisión inicial |
| --- | --- |
| Inventario | Cinco unidades físicas precargadas, identificadas de forma única: `MIC-01`, `MIC-02` (microscopios digitales), `OSC-01` (osciloscopio), `MUL-01` (multímetro) y `FUE-01` (fuente de alimentación). No se crean ni eliminan equipos desde la API. |
| Reserva | ID estable generado por el sistema, ID de unidad, solicitante ficticio, inicio, fin y estado `ACTIVA` o `CANCELADA`. |
| Instantes | Cadenas ISO 8601 con fecha, hora y desplazamiento explícito, por ejemplo `2026-11-03T10:00:00-03:00` o `2026-11-03T13:00:00Z`. Sin desplazamiento se rechazan; se comparan como instantes (normalizados a UTC), no como hora de pared. |
| Intervalos | Semiabiertos `[inicio, fin)`: el fin no ocupa el instante de la siguiente reserva. Inicio estrictamente anterior al fin. |
| Pasado y límites | Se permiten intervalos en el pasado para reproducir escenarios de demostración. No se fija duración máxima ni anticipación máxima; la duración debe ser positiva. No depender del reloj actual para decidir disponibilidad. |
| Persistencia | Base SQLite local en archivo; reiniciar el proceso sin borrar ese archivo conserva activas y canceladas. Una operación explícita de reinicio de datos, si se agrega, debe distinguirse de reiniciar el proceso e indicar que borra reservas. |

`GET /api/equipos` está implementada desde T2; T3a implementó disponibilidad, creación y listado, y T3b agregó cancelación y búsqueda por ID. Los ejemplos son criterios de aceptación; no se atribuye un ensayo de dos procesos Java hasta observarlo.

## R1 — Inventario fijo

La consulta `GET /api/equipos` devuelve exactamente las cinco unidades anteriores, cada una con ID y tipo. No hay endpoint de alta o baja de equipos.

**Aceptación R1:** en una instalación nueva se ven los cinco IDs sin duplicados; dos microscopios son unidades independientes y se pueden reservar simultáneamente. Reiniciar el proceso no agrega unidades duplicadas.

## R2 — Disponibilidad

`GET /api/equipos/{id}/disponibilidad?inicio=<ISO8601>&fin=<ISO8601>` indica si una unidad conocida está libre durante todo el intervalo solicitado. Una reserva `ACTIVA` bloquea si `inicioConsulta < finReserva` **y** `inicioReserva < finConsulta`. Las `CANCELADA` no bloquean. La consulta usa las mismas reglas temporales y de ID que la creación.

**Aceptación R2:** si `MIC-01` está activa de `10:00Z` a `11:00Z`, consultar `10:30Z`–`11:30Z` indica no disponible; consultar `11:00Z`–`12:00Z` indica disponible. Consultar `MIC-02` en el horario ocupado de `MIC-01` indica disponible, salvo que `MIC-02` tenga su propia reserva activa.

## R3 — Creación

`POST /api/reservas` recibe `equipoId`, `solicitante`, `inicio` y `fin`; al crear una reserva válida y disponible genera un ID, guarda estado `ACTIVA` y devuelve el registro. Una consulta posterior de disponibilidad para el mismo intervalo deja de indicar libre. No se almacena información personal real: los ejemplos usan nombres ficticios.

**Aceptación R3:** crear en `OSC-01` de `10:00Z` a `11:00Z` tiene éxito; intentar otra reserva activa de esa unidad de `10:30Z` a `11:30Z` se rechaza sin crear registro. De `11:00Z` a `12:00Z` se acepta. El mismo horario en `MUL-01` se acepta. La regla de creación y la de R2 nunca discrepan para el mismo estado y horario.

## R4 — Cancelación sin borrado

`POST /api/reservas/{id}/cancelacion` cambia una reserva `ACTIVA` a `CANCELADA` sin modificar su ID, solicitante, unidad ni intervalo. No elimina la fila ni crea otra reserva. Volver a cancelar una ya cancelada se rechaza como conflicto sin alterar el registro.

**Aceptación R4:** luego de cancelar una reserva, consultar su ID devuelve los datos originales y estado `CANCELADA`; la disponibilidad del mismo equipo e intervalo vuelve a indicar libre y se puede crear una nueva reserva allí. Una segunda cancelación no altera el historial.

## R5 — Consulta de registro e historial

`GET /api/reservas/{id}` devuelve una reserva existente independientemente de su estado. `GET /api/reservas` permite consultar el registro de reservas activas y canceladas; incluir el ID y el estado permite distinguir la reserva original de otra creada después de cancelar. No se requiere edición de reservas ni borrado desde la API.

**Aceptación R5:** listar después de crear, cancelar y volver a reservar muestra ambos registros con IDs distintos y estados respectivos. Buscar cualquiera por ID recupera el mismo registro. Un ID inexistente no aparece como reserva válida.

## R6 — Persistencia entre procesos

Inventario y reservas se almacenan localmente en SQLite. El archivo de datos debe tener una ubicación documentada en el README final. Al detener e iniciar de nuevo el servidor apuntando al mismo archivo, se recuperan los IDs, campos y estados existentes, sin volver a crear reservas ni limpiar datos automáticamente.

**Aceptación R6:** crear una activa y otra cancelada, detener el proceso, volverlo a iniciar sin borrar la base y consultar ambas: las dos conservan sus IDs y estados; la activa sigue bloqueando, la cancelada no. Documentar por separado cómo iniciar desde cero y cómo reiniciar sin borrar datos.

## R7 — Rechazos y consistencia

Se rechaza un `equipoId` desconocido; un `solicitante` vacío o compuesto solo por espacios; timestamps inválidos o sin desplazamiento; y `inicio >= fin`. El intento de reservar un intervalo ocupado se rechaza como conflicto. Consultar disponibilidad inválida no responde falsamente «libre». Cancelar o buscar un ID inexistente no crea registros. Los rechazos no modifican reservas, estados ni disponibilidad.

**Aceptación R7:** enviar cada caso inválido anterior tanto a la operación que corresponda como, para equipo y tiempo, a disponibilidad; comprobar el rechazo, la ausencia de nuevas reservas y que la consulta posterior de una reserva existente mantiene sus datos. Un conflicto tampoco altera el número de reservas. Las respuestas distinguen entradas inválidas (400), recursos inexistentes (404) y conflictos de estado u ocupación (409), con un mensaje explicativo; la forma exacta del error se fijará al implementar.

## Escenarios de prueba reproducibles (comprobados por HTTP en tests; reinicio entre procesos pendiente)

Los ejemplos usan instantes fijos; `A` y `B` representan IDs generados, no valores predeterminados.

1. Crear `A`: `MIC-01`, solicitante `Persona Demo`, `2026-11-03T10:00:00Z`–`2026-11-03T11:00:00Z`. Esperar `ACTIVA` y disponibilidad falsa en `10:30Z`–`11:00Z`.
2. Crear en `MIC-01` `10:30Z`–`11:30Z`: esperar conflicto sin nuevo registro. Crear en `MIC-01` `11:00Z`–`12:00Z` y en `MIC-02` `10:00Z`–`11:00Z`: esperar éxito de ambos.
3. Cancelar `A` y consultar por ID: esperar `CANCELADA` con datos originales. Crear `B` en el intervalo original de `MIC-01`: esperar éxito y un ID diferente. Reiniciar el proceso conservando SQLite: `A` y `B` permanecen; `B` bloquea y `A` no.
4. Repetir la consulta con `2026-11-03T07:00:00-03:00` como inicio equivalente a `10:00Z`: esperar el mismo resultado. Probar un horario sin desplazamiento y fin igual a inicio: esperar rechazo sin cambio de datos.

## Límites y trazabilidad

Fuera de alcance: autenticación y roles reales, notificaciones, pagos, integraciones externas, interfaz gráfica compleja y despliegue de producción. El solicitante es ficticio y no representa datos de estudiantes reales. Las pruebas funcionales comprueban las reglas de superposición.

| Requisito | Trabajo previsto | Evidencia aún pendiente |
| --- | --- | --- |
| R1, R6 | T2 | Inventario precargado, base en archivo y reinicio comprobado |
| R2–R5, R7 | T3 | Endpoints y pruebas de aceptación por regla |
| R1–R7 | T4 | README operativo, pruebas ejecutadas y decisiones humanas documentadas; ensayo entre procesos no concluido |

[Volver al README](../README.md) · [Plan de tareas](../odd/tasks/lab-reservations.md)
