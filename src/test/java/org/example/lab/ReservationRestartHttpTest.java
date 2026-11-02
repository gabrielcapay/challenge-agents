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

class ReservationRestartHttpTest {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private String base;
    private static final String START = "2026-11-03T10:00:00Z";
    private static final String END = "2026-11-03T11:00:00Z";

    @Test
    void activeAndCancelledRecordsSurviveTwoContextsWithTheSameFile() throws Exception {
        JsonNode active;
        JsonNode cancelled;
        try (ConfigurableApplicationContext first = start()) {
            active = create("MIC-01");
            JsonNode old = create("MIC-02");
            cancelled = parse(send("POST", "/api/reservas/" + old.get("id").asText() + "/cancelacion", null, 200));
            assertEquals(2, parse(send("GET", "/api/reservas", null, 200)).size());
        }
        try (ConfigurableApplicationContext second = start()) {
            assertEquals(active, parse(send("GET", "/api/reservas/" + active.get("id").asText(), null, 200)));
            assertEquals(cancelled, parse(send("GET", "/api/reservas/" + cancelled.get("id").asText(), null, 200)));
            JsonNode list = parse(send("GET", "/api/reservas", null, 200));
            assertEquals(2, list.size());
            assertEquals(active, list.get(0));
            assertEquals(cancelled, list.get(1));
            assertAvailable("MIC-01", false);
            assertAvailable("MIC-02", true);
            JsonNode replacement = create("MIC-02");
            assertNotEquals(cancelled.get("id"), replacement.get("id"));
            assertEquals(3, parse(send("GET", "/api/reservas", null, 200)).size());
            assertAvailable("MIC-02", false);
        }
    }

    private ConfigurableApplicationContext start() {
        ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class).properties("server.port=0")
                .run("--spring.datasource.url=jdbc:sqlite:" + directory.resolve("reservations.sqlite").toAbsolutePath());
        base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
        return app;
    }
    private JsonNode create(String unit) throws Exception {
        return parse(send("POST", "/api/reservas", "{\"equipoId\":\"" + unit +
                "\",\"solicitante\":\"Persona Demo\",\"inicio\":\"" + START + "\",\"fin\":\"" + END + "\"}", 200));
    }
    private void assertAvailable(String unit, boolean expected) throws Exception {
        String path = "/api/equipos/" + unit + "/disponibilidad?inicio=" + URLEncoder.encode(START, StandardCharsets.UTF_8) +
                "&fin=" + URLEncoder.encode(END, StandardCharsets.UTF_8);
        assertEquals(expected, parse(send("GET", path, null, 200)).get("disponible").asBoolean());
    }
    private HttpResponse<String> send(String method, String path, String body, int status) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        if (body != null) request.header("Content-Type", "application/json");
        HttpResponse<String> response = client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return response;
    }
    private JsonNode parse(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
}
