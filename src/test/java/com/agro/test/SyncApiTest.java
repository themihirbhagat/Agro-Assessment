package com.agro.test;

import java.time.Instant;
import java.util.UUID;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public class SyncApiTest {

    @BeforeClass
    public void setup() {
        // Set your backend base URL here
        RestAssured.baseURI = "https://api.yourdomain.com";
        RestAssured.basePath = "/api/v1";
    }

    /**
     * Generates a mock payload based on the Survey Schema provided in the requirements.
     */
    private String createSyncPayload(String userId, String deviceId, String localId) {
        return String.format("""
            {
              "userId": "%s",
              "deviceId": "%s",
              "syncTimestamp": "%s",
              "surveyData": {
                "localId": "%s",
                "title": "Student Daily Habits Survey",
                "sections": [
                  {
                    "sectionId": "sec_1",
                    "answers": [
                      { "questionId": "q_1", "value": "Aarav" },
                      { "questionId": "q_2", "value": "3rd" }
                    ]
                  }
                ]
              }
            }
            """, userId, deviceId, Instant.now().toString(), localId);
    }

    @Test(description = "Should handle identical local IDs from different devices without overwriting")
    public void testDuplicateLocalIdSync() {
        // 1. Ankit syncs School_1
        String ankitPayload = createSyncPayload("user_ankit", "device_A", "School_1");
        Response ankitRes = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(ankitPayload)
                .post("/sync-survey");
        
        Assert.assertEquals(ankitRes.getStatusCode(), 201, "Ankit sync should return 201");
        String ankitGlobalId = ankitRes.jsonPath().getString("globalSurveyId");
        Assert.assertNotNull(ankitGlobalId, "Backend must generate a global ID for Ankit");

        // 2. Pooja syncs School_1 (Exact same local ID as Ankit)
        String poojaPayload = createSyncPayload("user_pooja", "device_B", "School_1");
        Response poojaRes = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(poojaPayload)
                .post("/sync-survey");
        
        Assert.assertEquals(poojaRes.getStatusCode(), 201, "Pooja sync should return 201");
        String poojaGlobalId = poojaRes.jsonPath().getString("globalSurveyId");
        Assert.assertNotNull(poojaGlobalId, "Backend must generate a global ID for Pooja");

        // 3. Validation: The backend must issue distinct global IDs for the same local ID
        Assert.assertNotEquals(ankitGlobalId, poojaGlobalId, "Global IDs must be unique to prevent data overwrite");

        // 4. Verify Ankit's data remains intact (Optional DB/GET verification)
        Response fetchAnkit = RestAssured.given()
                .pathParam("id", ankitGlobalId)
                .get("/surveys/{id}");
        Assert.assertEquals(fetchAnkit.jsonPath().getString("submittedBy"), "user_ankit");
    }

    @Test(
        description = "Should handle strict concurrency (Simulated DDoS / Race Condition)",
        invocationCount = 50, 
        threadPoolSize = 50
    )
    public void testConcurrentSync() {
        // TestNG will execute this method simultaneously across 50 threads.
        String uniqueUserId = "user_" + UUID.randomUUID().toString();
        String payload = createSyncPayload(uniqueUserId, "device_concurrent", "School_1");

        Response res = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(payload)
                .post("/sync-survey");

        int statusCode = res.getStatusCode();
        
        // Assert that the backend did not buckle (No 502 Bad Gateway / 503 Service Unavailable)
        // 429 Too Many Requests is acceptable if an API Gateway rate limiter is configured
        Assert.assertTrue(
            statusCode == 201 || statusCode == 202 || statusCode == 429, 
            "Expected successful creation or rate limiting, but got: " + statusCode
        );
    }
}