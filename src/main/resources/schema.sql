CREATE TABLE IF NOT EXISTS equipos (
    id TEXT PRIMARY KEY,
    tipo TEXT NOT NULL,
    orden INTEGER NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS reservas (
    id TEXT PRIMARY KEY,
    equipo_id TEXT NOT NULL REFERENCES equipos(id),
    solicitante TEXT NOT NULL,
    inicio TEXT NOT NULL,
    fin TEXT NOT NULL,
    estado TEXT NOT NULL CHECK (estado IN ('ACTIVA', 'CANCELADA'))
);

CREATE INDEX IF NOT EXISTS reservas_intervalo ON reservas (equipo_id, estado, inicio, fin);
