# QA Engineer Assignment

Part 1: Hands-On Technical Inspection & Automation 

Scenario A: The Offline Sync & Data Collision (API & Mobile) 
Context: Two field workers, Ankit and Pooja, are surveying the same village completely offline. 
 
1. At 10:00 AM, Ankit registers "Govt Primary School" on his device, fills a survey, and his 
phone assigns it a local ID (e.g., School_1). 
2. At 11:00 AM, Pooja visits the exact same school, registers it on her device, fills a survey, 
and her phone assigns it a local ID (e.g., School_1). 
3. At 5:00 PM, they both reach a town with internet, and the app automatically syncs their 
data to the server at the exact same time via a background process. 
4. When 50,000 users do this simultaneously, it essentially DDoSs our backend. 
 
Your Task: 
 
1. API Automation (Hands-on): Write a mock API test script (using Postman, 
RestAssured, Cypress, or your preferred tool) that simulates hitting a POST /sync-
survey endpoint with a JSON payload. How would you write tests to ensure the API 
correctly handles duplicate local IDs without overwriting data? Include the script in your 
repo. 

    => To handle the collision of duplicate local IDs without data overwrite, the backend must rely on a composite key (e.g., device_id + local_id) rather than treating local_id as a global primary key.

    **Implementation**: ./src/java/com/agro/test/SyncApiTest.java



2. Strategy (README.md): Explain how you would test the Android app's background sync 
behavior under poor network conditions (e.g., network dropping exactly at 99% of a 
photo upload). What tools (e.g., Charles Proxy, ADB) would you use, and how? 

    => 