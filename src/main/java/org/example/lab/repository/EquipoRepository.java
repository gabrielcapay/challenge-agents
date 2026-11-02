package org.example.lab.repository;

import org.example.lab.model.Equipo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EquipoRepository {
    private final JdbcTemplate jdbc;

    public EquipoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Equipo> findAll() {
        return jdbc.query("SELECT id, tipo FROM equipos ORDER BY orden",
                (rs, row) -> new Equipo(rs.getString("id"), rs.getString("tipo")));
    }
}
