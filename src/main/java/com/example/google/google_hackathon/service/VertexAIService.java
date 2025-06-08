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

@Service
public class VertexAIService {

    private static final String PROJECT_ID = "nomadic-bison-459812-a8";
    private static final String ENDPOINT = String.format(
        "https://us-central1-aiplatform.googleapis.com/v1/projects/%s/locations/us-central1/publishers/google/models/textembedding-gecko:predict",
        PROJECT_ID
    );

    public List<Double> generateEmbedding(String text) throws IOException, InterruptedException {
        // Cloud Run 上ではこの認証でOK
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
            .createScoped("https://www.googleapis.com/auth/cloud-platform");
        credentials.refreshIfExpired();
        String accessToken = credentials.getAccessToken().getTokenValue();

        // リクエスト構築
        JsonObject requestBody = new JsonObject();
        JsonArray instances = new JsonArray();
        JsonObject instance = new JsonObject();
        instance.addProperty("content", text);
        instances.add(instance);
        requestBody.add("instances", instances);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Authorization", "Bearer " + accessToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray vector = json.getAsJsonArray("predictions").get(0).getAsJsonObject().getAsJsonArray("values");

        return StreamSupport.stream(vector.spliterator(), false)
            .map(JsonElement::getAsDouble)
            .collect(Collectors.toList());
    }
}
