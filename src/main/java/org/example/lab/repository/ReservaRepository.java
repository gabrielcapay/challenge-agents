package org.example.lab.repository;

import org.example.lab.model.Reserva;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class ReservaRepository {
    // Fixed width UTC strings preserve full nanosecond precision in SQLite lexical comparisons.
    private static final DateTimeFormatter STORED = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();
    private final DataSource dataSource;

    public ReservaRepository(DataSource dataSource) { this.dataSource = dataSource; }

    public List<Reserva> findAll() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT id, equipo_id, solicitante, inicio, fin, estado FROM reservas ORDER BY rowid");
             ResultSet rows = query.executeQuery()) {
            List<Reserva> reservations = new ArrayList<>();
            while (rows.next()) reservations.add(read(rows));
            return reservations;
        } catch (SQLException e) { throw new IllegalStateException("No se pudo listar reservas", e); }
    }

    public boolean existsEquipment(Connection connection, String equipment) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM equipos WHERE id = ?")) {
            query.setString(1, equipment);
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    public boolean occupied(Connection connection, String equipment, Instant start, Instant end) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM reservas WHERE equipo_id = ? AND estado = 'ACTIVA' AND inicio < ? AND fin > ? LIMIT 1")) {
            query.setString(1, equipment);
            query.setString(2, STORED.format(end));
            query.setString(3, STORED.format(start));
            try (ResultSet rows = query.executeQuery()) { return rows.next(); }
        }
    }

    public boolean available(String equipment, Instant start, Instant end) {
        try (Connection connection = dataSource.getConnection()) {
            if (!existsEquipment(connection, equipment)) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Equipo desconocido");
            return !occupied(connection, equipment, start, end);
        } catch (SQLException e) { throw new IllegalStateException("No se pudo consultar disponibilidad", e); }
    }

    public Reserva create(String equipment, String applicant, Instant start, Instant end) {
        try (Connection connection = dataSource.getConnection()) {
            // JDBC setAutoCommit(false) starts a deferred transaction in SQLite. Acquire write intent
            // explicitly before the overlap SELECT so competing writers cannot both observe free space.
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA busy_timeout = 5000");
                statement.execute("BEGIN IMMEDIATE");
            }
            try {
                if (!existsEquipment(connection, equipment)) throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Equipo desconocido");
                if (occupied(connection, equipment, start, end)) throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Equipo ocupado en ese intervalo");
                Reserva reservation = new Reserva(UUID.randomUUID().toString(), equipment, applicant, start, end, "ACTIVA");
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO reservas (id, equipo_id, solicitante, inicio, fin, estado) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, reservation.id());
                    insert.setString(2, equipment);
                    insert.setString(3, applicant);
                    insert.setString(4, STORED.format(start));
                    insert.setString(5, STORED.format(end));
                    insert.setString(6, reservation.estado());
                    insert.executeUpdate();
                }
                try (Statement statement = connection.createStatement()) { statement.execute("COMMIT"); }
                return reservation;
            } catch (SQLException | RuntimeException failure) {
                try (Statement statement = connection.createStatement()) { statement.execute("ROLLBACK"); }
                catch (SQLException rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        } catch (SQLException e) { throw new IllegalStateException("No se pudo crear reserva", e); }
    }

    private Reserva read(ResultSet row) throws SQLException {
        return new Reserva(row.getString("id"), row.getString("equipo_id"), row.getString("solicitante"),
                Instant.parse(row.getString("inicio")), Instant.parse(row.getString("fin")), row.getString("estado"));
    }
}
