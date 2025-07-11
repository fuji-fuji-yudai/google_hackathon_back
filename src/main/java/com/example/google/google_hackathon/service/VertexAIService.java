package com.example.google.google_hackathon.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.gson.*;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VertexAIService {

    private static final String PROJECT_ID = "nomadic-bison-459812-a8";
    private static final Logger logger = LoggerFactory.getLogger(VertexAIService.class);
    private static final String ENDPOINT = String.format(
        "https://us-central1-aiplatform.googleapis.com/v1/projects/%s/locations/us-central1/publishers/google/models/gemini-embedding-001:predict",
        PROJECT_ID
    );

    public List<Double> generateEmbedding(String text) throws IOException, InterruptedException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
        .createScoped("https://www.googleapis.com/auth/cloud-platform");
    credentials.refreshIfExpired();
    String accessToken = credentials.getAccessToken().getTokenValue();

    JsonObject requestBody = new JsonObject();
    JsonArray instances = new JsonArray();
    JsonObject instance = new JsonObject();
    instance.addProperty("content", text);
    instances.add(instance);
    requestBody.add("instances", instances);

    logger.debug("Vertex AI embedding request body: {}", requestBody.toString());

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(ENDPOINT))
        .header("Authorization", "Bearer " + accessToken)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
        .build();

    HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    logger.debug("Vertex AI embedding response body: {}", response.body());

    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
    JsonArray predictions = json.getAsJsonArray("predictions");

    if (predictions == null || predictions.size() == 0) {
        throw new RuntimeException("Vertex AI embedding API returned no predictions: " + response.body());
    }

    // JsonArray vector = predictions.get(0).getAsJsonObject().getAsJsonArray("values");

    // if (vector == null) {
    //     throw new RuntimeException("Vertex AI embedding API returned no values: " + response.body());
    // }

JsonObject firstPrediction = predictions.get(0).getAsJsonObject();
JsonObject embeddings = firstPrediction.has("embeddings") ? firstPrediction.getAsJsonObject("embeddings") : null;

JsonArray vector = null;
if (embeddings != null && embeddings.has("values") && embeddings.get("values").isJsonArray()) {
    vector = embeddings.getAsJsonArray("values");
}

if (vector == null || vector.size() == 0) {
    throw new RuntimeException("Vertex AI embedding API returned no values: " + response.body());
}

logger.debug("Embedding vector size: {}", vector.size());

return StreamSupport.stream(vector.spliterator(), false)
    .map(JsonElement::getAsDouble)
    .collect(Collectors.toList());

}

}
