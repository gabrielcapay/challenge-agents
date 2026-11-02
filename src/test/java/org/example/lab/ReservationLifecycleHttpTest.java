package org.example.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReservationLifecycleHttpTest {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String base;
    private static final String START = "2026-11-03T10:00:00Z";
    private static final String END = "2026-11-03T11:00:00Z";

    @Test
    void cancellationPreservesRecordAndReleasesInterval() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            JsonNode original = create("MIC-01", START, END);
            String id = original.get("id").asText();
            assertEquals(200, availability("MIC-01").statusCode());
            assertEquals(false, parse(availability("MIC-01")).get("disponible").asBoolean());
            JsonNode cancelled = parse(expect("POST", "/api/reservas/" + id + "/cancelacion", null, 200));
            assertEquals(original.get("id"), cancelled.get("id"));
            for (String field : new String[]{"equipoId", "solicitante", "inicio", "fin"})
                assertEquals(original.get(field), cancelled.get(field), field);
            assertEquals("CANCELADA", cancelled.get("estado").asText());
            assertEquals(cancelled, parse(expect("GET", "/api/reservas/" + id, null, 200)));
            assertEquals(true, parse(availability("MIC-01")).get("disponible").asBoolean());
            JsonNode replacement = create("MIC-01", START, END);
            assertNotEquals(id, replacement.get("id").asText());
            assertEquals("ACTIVA", replacement.get("estado").asText());
            assertEquals(false, parse(availability("MIC-01")).get("disponible").asBoolean());
        }
    }

    @Test
    void listsAndFindsBothHistoricalAndActiveRecordsById() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            JsonNode old = create("OSC-01", START, END);
            String oldId = old.get("id").asText();
            JsonNode cancelled = parse(expect("POST", "/api/reservas/" + oldId + "/cancelacion", null, 200));
            JsonNode active = create("OSC-01", START, END);
            JsonNode list = parse(expect("GET", "/api/reservas", null, 200));
            assertEquals(2, list.size());
            assertNotEquals(oldId, active.get("id").asText());
            assertEquals(cancelled, list.get(0));
            assertEquals(active, list.get(1));
            assertEquals(cancelled, parse(expect("GET", "/api/reservas/" + oldId, null, 200)));
            assertEquals(active, parse(expect("GET", "/api/reservas/" + active.get("id").asText(), null, 200)));
        }
    }

    @Test
    void unknownAndRepeatedCancellationRejectWithoutMutation() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            JsonNode original = create("MUL-01", START, END);
            String id = original.get("id").asText();
            JsonNode before = parse(expect("GET", "/api/reservas", null, 200));
            assertEquals("Reserva desconocida", parse(expect("GET", "/api/reservas/missing", null, 404)).get("error").asText());
            assertEquals("Reserva desconocida", parse(expect("POST", "/api/reservas/missing/cancelacion", null, 404)).get("error").asText());
            assertEquals(before, parse(expect("GET", "/api/reservas", null, 200)));
            assertEquals(original, parse(expect("GET", "/api/reservas/" + id, null, 200)));
            assertEquals(false, parse(availability("MUL-01")).get("disponible").asBoolean());
            JsonNode cancelled = parse(expect("POST", "/api/reservas/" + id + "/cancelacion", null, 200));
            JsonNode after = parse(expect("GET", "/api/reservas", null, 200));
            assertEquals(1, after.size());
            assertEquals("Reserva ya cancelada", parse(expect("POST", "/api/reservas/" + id + "/cancelacion", null, 409)).get("error").asText());
            assertEquals(after, parse(expect("GET", "/api/reservas", null, 200)));
            assertEquals(cancelled, parse(expect("GET", "/api/reservas/" + id, null, 200)));
            assertEquals(true, parse(availability("MUL-01")).get("disponible").asBoolean());
        }
    }

    @Test
    void existingCreationStillRejectsOverlapWithoutAddingRecords() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            JsonNode first = create("MIC-02", START, END);
            JsonNode before = parse(expect("GET", "/api/reservas", null, 200));
            assertEquals("Equipo ocupado en ese intervalo", parse(expect("POST", "/api/reservas", body("MIC-02", START, END), 409)).get("error").asText());
            assertEquals(before, parse(expect("GET", "/api/reservas", null, 200)));
            assertEquals(first, before.get(0));
        }
    }

    private ConfigurableApplicationContext start() {
        ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class).properties("server.port=0")
                .run("--spring.datasource.url=jdbc:sqlite:" + directory.resolve("reservations.sqlite").toAbsolutePath());
        base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
        return app;
    }
    private String body(String unit, String from, String to) {
        return "{\"equipoId\":\"" + unit + "\",\"solicitante\":\"Persona Demo\",\"inicio\":\"" + from + "\",\"fin\":\"" + to + "\"}";
    }
    private JsonNode create(String unit, String from, String to) throws Exception {
        JsonNode result = parse(expect("POST", "/api/reservas", body(unit, from, to), 200));
        assertEquals("ACTIVA", result.get("estado").asText());
        return result;
    }
    private HttpResponse<String> availability(String unit) throws Exception {
        return expect("GET", "/api/equipos/" + unit + "/disponibilidad?inicio=" +
                URLEncoder.encode(START, StandardCharsets.UTF_8) + "&fin=" + URLEncoder.encode(END, StandardCharsets.UTF_8), null, 200);
    }
    private HttpResponse<String> expect(String method, String path, String body, int status) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (body != null) request.header("Content-Type", "application/json");
        HttpResponse<String> response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response;
    }
    private JsonNode parse(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
}
