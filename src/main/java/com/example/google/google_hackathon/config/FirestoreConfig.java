package com.example.google.google_hackathon.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;

@Configuration
public class FirestoreConfig {

    
@Bean
public Firestore firestore() throws IOException {
if (FirebaseApp.getApps().isEmpty()) {
FirebaseOptions options = FirebaseOptions.builder()
.setCredentials(GoogleCredentials.getApplicationDefault())
.setProjectId("nomadic-bison-459812-a8")
.build();
FirebaseApp.initializeApp(options);
}

        return FirestoreClient.getFirestore();
    }
}
