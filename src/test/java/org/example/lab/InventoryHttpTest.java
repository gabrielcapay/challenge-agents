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
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryHttpTest {
    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void returnsFiveDistinctUnitsOnFirstStartAndAfterRestart() throws Exception {
        String database = temporaryDirectory.resolve("inventory.sqlite").toAbsolutePath().toString();
        for (int start = 0; start < 2; start++) {
            try (ConfigurableApplicationContext app = new SpringApplicationBuilder(LabApplication.class)
                    .properties("server.port=0")
                    .run("--spring.datasource.url=jdbc:sqlite:" + database)) {
                int port = Integer.parseInt(app.getEnvironment().getProperty("local.server.port"));
                for (int request = 0; request < 2; request++) {
                    HttpResponse<String> response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/equipos"))
                                    .GET().build(), HttpResponse.BodyHandlers.ofString());
                    assertEquals(200, response.statusCode(), response.body());
                    JsonNode result = mapper.readTree(response.body());
                    assertEquals(5, result.size(), response.body());
                    assertEquals(List.of("MIC-01", "MIC-02", "OSC-01", "MUL-01", "FUE-01"),
                            StreamSupport.stream(result.spliterator(), false).map(unit -> unit.get("id").asText()).toList());
                    assertEquals(List.of("microscopio digital", "microscopio digital", "osciloscopio", "multímetro", "fuente de alimentación"),
                            StreamSupport.stream(result.spliterator(), false).map(unit -> unit.get("tipo").asText()).toList());
                    result.forEach(unit -> assertEquals(2, unit.size()));
                }
            }
        }
    }
}
