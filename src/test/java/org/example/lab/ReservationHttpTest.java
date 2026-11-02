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

class ReservationHttpTest {
    @TempDir Path temporaryDirectory;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private String base;
    private static final String START = "2026-11-03T10:00:00Z";
    private static final String END = "2026-11-03T11:00:00Z";

    @Test
    void createsListsAndChecksSemiOpenAvailabilityAcrossUnitsAndOffsets() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            assertEquals(0, list().size());
            assertAvailable("MIC-01", START, END, true);
            JsonNode first = create("MIC-01", "Persona Demo", START, END, 200);
            assertTrue(first.get("id").asText().length() > 0);
            assertEquals("MIC-01", first.get("equipoId").asText());
            assertEquals("Persona Demo", first.get("solicitante").asText());
            assertEquals(START, first.get("inicio").asText());
            assertEquals(END, first.get("fin").asText());
            assertEquals("ACTIVA", first.get("estado").asText());
            assertEquals(first, list().get(0));
            assertAvailable("MIC-01", "2026-11-03T07:30:00-03:00", "2026-11-03T11:30:00Z", false);
            assertAvailable("MIC-01", END, "2026-11-03T12:00:00Z", true);
            assertAvailable("MIC-02", START, END, true);
            rejectCreate("MIC-01", "Demo", "2026-11-03T10:30:00Z", "2026-11-03T11:30:00Z", 409);
            JsonNode next = create("MIC-01", "Demo", END, "2026-11-03T12:00:00Z", 200);
            JsonNode other = create("MIC-02", "Demo", "2026-11-03T07:00:00-03:00", "2026-11-03T08:00:00-03:00", 200);
            assertNotEquals(first.get("id"), next.get("id"));
            assertNotEquals(first.get("id"), other.get("id"));
            assertEquals(START, other.get("inicio").asText());
            assertEquals(END, other.get("fin").asText());
            assertEquals(3, list().size());
        }
    }

    @Test
    void rejectsInvalidInputsWithoutMutatingStoredRecords() throws Exception {
        try (ConfigurableApplicationContext app = start()) {
            create("OSC-01", "Demo", START, END, 200);
            rejectCreate("UNKNOWN", "Demo", START, END, 404);
            rejectAvailability("UNKNOWN", START, END, 404);
            for (String applicant : new String[]{"", "   "}) rejectCreate("OSC-01", applicant, END, "2026-11-03T12:00:00Z", 400);
            for (String[] interval : new String[][]{
                    {"bad", END}, {"2026-11-03T10:00:00", END}, {START, "bad"},
                    {START, "2026-11-03T11:00:00"}, {START, START}, {END, START}
            }) {
                rejectCreate("OSC-01", "Demo", interval[0], interval[1], 400);
                rejectAvailability("OSC-01", interval[0], interval[1], 400);
            }
            rejectAvailability("OSC-01", null, END, 400);
            rejectAvailability("OSC-01", START, null, 400);
            assertAvailable("OSC-01", START, END, false);
            assertEquals(1, list().size());
        }
    }

    private ConfigurableApplicationContext start() {
        ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class)
                .properties("server.port=0")
                .run("--spring.datasource.url=jdbc:sqlite:" + temporaryDirectory.resolve("reservations.sqlite").toAbsolutePath());
        base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
        return app;
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path));
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private String query(String value) { return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String availability(String unit, String start, String end) {
        return "/api/equipos/" + unit + "/disponibilidad?" + (start == null ? "" : "inicio=" + query(start) + "&") +
                (end == null ? "" : "fin=" + query(end));
    }
    private JsonNode list() throws Exception {
        HttpResponse<String> response = request("GET", "/api/reservas", null);
        assertEquals(200, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
    private JsonNode create(String unit, String applicant, String start, String end, int status) throws Exception {
        String body = json.writeValueAsString(new Input(unit, applicant, start, end));
        HttpResponse<String> response = request("POST", "/api/reservas", body);
        assertEquals(status, response.statusCode(), response.body());
        return json.readTree(response.body());
    }
    private void rejectCreate(String unit, String applicant, String start, String end, int status) throws Exception {
        JsonNode before = list();
        JsonNode error = create(unit, applicant, start, end, status);
        assertTrue(error.hasNonNull("error") && !error.get("error").asText().isBlank(), error.toString());
        assertEquals(before, list());
    }
    private void rejectAvailability(String unit, String start, String end, int status) throws Exception {
        JsonNode before = list();
        HttpResponse<String> response = request("GET", availability(unit, start, end), null);
        assertEquals(status, response.statusCode(), response.body());
        JsonNode error = json.readTree(response.body());
        assertTrue(error.hasNonNull("error") && !error.get("error").asText().isBlank(), response.body());
        assertEquals(before, list());
    }
    private void assertAvailable(String unit, String start, String end, boolean expected) throws Exception {
        HttpResponse<String> response = request("GET", availability(unit, start, end), null);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(expected, json.readTree(response.body()).get("disponible").asBoolean());
    }
    private record Input(String equipoId, String solicitante, String inicio, String fin) { }
}
