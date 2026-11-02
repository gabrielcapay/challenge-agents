package org.example.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ReservationConcurrencyHttpTest {
    @TempDir Path temporaryDirectory;

    @Test
    void concurrentRequestsCannotBothReserveSameUnitAndInterval() throws Exception {
        try (ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class)
                .properties("server.port=0")
                .run("--spring.datasource.url=jdbc:sqlite:" + temporaryDirectory.resolve("concurrent.sqlite").toAbsolutePath());
             ExecutorService executor = Executors.newFixedThreadPool(2)) {
            String url = "http://localhost:" + app.getEnvironment().getProperty("local.server.port") + "/api/reservas";
            HttpClient client = HttpClient.newHttpClient();
            String body = """
                    {"equipoId":"MIC-01","solicitante":"Demo","inicio":"2026-11-03T10:00:00Z","fin":"2026-11-03T11:00:00Z"}
                    """;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> attempts = List.of(
                    executor.submit(() -> send(client, url, body, start)),
                    executor.submit(() -> send(client, url, body, start)));
            start.countDown();
            HttpResponse<String> first = attempts.get(0).get();
            HttpResponse<String> second = attempts.get(1).get();
            assertEquals(List.of(200, 409), List.of(first.statusCode(), second.statusCode()).stream().sorted().toList(),
                    first.body() + " / " + second.body());
            HttpResponse<String> listing = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, listing.statusCode());
            JsonNode records = new ObjectMapper().readTree(listing.body());
            assertEquals(1, records.size(), listing.body());
            assertEquals("ACTIVA", records.get(0).get("estado").asText());
        }
    }

    private HttpResponse<String> send(HttpClient client, String url, String body, CountDownLatch start) throws Exception {
        start.await();
        return client.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
