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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReservationCancelCreateHttpTest {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private static final String START = "2026-11-03T10:00:00Z";
    private static final String END = "2026-11-03T11:00:00Z";
    private static final String BODY = """
            {"equipoId":"MIC-01","solicitante":"Persona Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z"}
            """;

    @Test
    void simultaneousCancellationAndCreationPreserveOneConsistentOutcome() throws Exception {
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class)
                .properties("server.port=0")
                .run("--spring.datasource.url=jdbc:sqlite:" + directory.resolve("race.sqlite").toAbsolutePath());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            String base = "http://localhost:" + app.getEnvironment().getProperty("local.server.port");
            HttpResponse<String> created = send("POST", base + "/api/reservas", BODY);
            assertEquals(200, created.statusCode(), created.body());
            JsonNode original = json.readTree(created.body());
            String id = original.get("id").asText();
            CountDownLatch go = new CountDownLatch(1);
            Future<HttpResponse<String>> cancellation = executor.submit(() -> {
                go.await();
                return send("POST", base + "/api/reservas/" + id + "/cancelacion", null);
            });
            Future<HttpResponse<String>> creation = executor.submit(() -> {
                go.await();
                return send("POST", base + "/api/reservas", BODY);
            });
            go.countDown();
            HttpResponse<String> cancelled = cancellation.get(10, TimeUnit.SECONDS);
            HttpResponse<String> attempted = creation.get(10, TimeUnit.SECONDS);
            assertEquals(200, cancelled.statusCode(), cancelled.body());
            JsonNode cancelledRecord = json.readTree(cancelled.body());
            for (String field : new String[]{"id", "equipoId", "solicitante", "inicio", "fin"})
                assertEquals(original.get(field), cancelledRecord.get(field), field);
            assertEquals("CANCELADA", cancelledRecord.get("estado").asText());
            HttpResponse<String> found = send("GET", base + "/api/reservas/" + id, null);
            assertEquals(200, found.statusCode(), found.body());
            assertEquals(cancelledRecord, json.readTree(found.body()));
            HttpResponse<String> all = send("GET", base + "/api/reservas", null);
            assertEquals(200, all.statusCode(), all.body());
            JsonNode listed = json.readTree(all.body());
            assertEquals(cancelledRecord, listed.get(0));
            assertTrue(attempted.statusCode() == 200 || attempted.statusCode() == 409, attempted.body());
            boolean newReservation = attempted.statusCode() == 200;
            assertEquals(newReservation ? 2 : 1, listed.size());
            if (newReservation) {
                JsonNode next = json.readTree(attempted.body());
                assertNotEquals(id, next.get("id").asText());
                assertEquals("ACTIVA", next.get("estado").asText());
                assertEquals(next, listed.get(1));
            } else {
                assertEquals("Equipo ocupado en ese intervalo", json.readTree(attempted.body()).get("error").asText());
            }
            String availability = base + "/api/equipos/MIC-01/disponibilidad?inicio=" +
                    URLEncoder.encode(START, StandardCharsets.UTF_8) + "&fin=" + URLEncoder.encode(END, StandardCharsets.UTF_8);
            HttpResponse<String> check = send("GET", availability, null);
            assertEquals(200, check.statusCode(), check.body());
            assertEquals(!newReservation, json.readTree(check.body()).get("disponible").asBoolean());
        }
    }

    private HttpResponse<String> send(String method, String url, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url));
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
