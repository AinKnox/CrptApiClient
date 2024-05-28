package org.example;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CrptApi {

    private final HttpClient httpClient;
    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;

    // Конструктор класса с ограничением количества запросов
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.httpClient = HttpClient.newHttpClient();
        this.semaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();

        long interval = timeUnit.toMillis(1);
        Thread resetThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(interval);
                    semaphore.release(requestLimit - semaphore.availablePermits());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        resetThread.setDaemon(true);
        resetThread.start();
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        if (!semaphore.tryAcquire()) {
            semaphore.acquire();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer YOUR_ACCESS_TOKEN")
                .POST(HttpRequest.BodyPublishers.ofString(documentToJson(document)))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        semaphore.release();

        if (response.statusCode() != 200) {
            throw new RuntimeException("Не удалось создать документ: " + response.body());
        }
    }

    private String documentToJson(Document document) throws JsonProcessingException {
        return objectMapper.writeValueAsString(document);
    }

    public static class Document {
        @JsonProperty("description")
        public Description description;
        @JsonProperty("doc_id")
        public String doc_id;
        @JsonProperty("doc_status")
        public String doc_status;
        @JsonProperty("doc_type")
        public String doc_type;
        @JsonProperty("importRequest")
        public boolean importRequest;
        @JsonProperty("owner_inn")
        public String owner_inn;
        @JsonProperty("participant_inn")
        public String participant_inn;
        @JsonProperty("producer_inn")
        public String producer_inn;
        @JsonProperty("production_date")
        public String production_date;
        @JsonProperty("production_type")
        public String production_type;
        @JsonProperty("products")
        public Product[] products;
        @JsonProperty("reg_date")
        public String reg_date;
        @JsonProperty("reg_number")
        public String reg_number;

        public static class Description {
            @JsonProperty("participantInn")
            public String participantInn;
        }
    }

    public static class Product {
        @JsonProperty("certificate_document")
        public String certificate_document;
        @JsonProperty("certificate_document_date")
        public String certificate_document_date;
        @JsonProperty("certificate_document_number")
        public String certificate_document_number;
        @JsonProperty("owner_inn")
        public String owner_inn;
        @JsonProperty("producer_inn")
        public String producer_inn;
        @JsonProperty("production_date")
        public String production_date;
        @JsonProperty("tnved_code")
        public String tnved_code;
        @JsonProperty("uit_code")
        public String uit_code;
        @JsonProperty("uitu_code")
        public String uitu_code;
    }

    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.MINUTES, 5);

        CrptApi.Document document = new CrptApi.Document();

        try {
            api.createDocument(document, "sample-signature");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
