# QA Engineer Assignment

## Part 1: Hands-On Technical Inspection & Automation 

### Scenario A: The Offline Sync & Data Collision (API & Mobile) 
Context: Two field workers, Ankit and Pooja, are surveying the same village completely offline. 
 
1. At 10:00 AM, Ankit registers "Govt Primary School" on his device, fills a survey, and his 
phone assigns it a local ID (e.g., School_1). 
2. At 11:00 AM, Pooja visits the exact same school, registers it on her device, fills a survey, 
and her phone assigns it a local ID (e.g., School_1). 
3. At 5:00 PM, they both reach a town with internet, and the app automatically syncs their 
data to the server at the exact same time via a background process. 
4. When 50,000 users do this simultaneously, it essentially DDoSs our backend. 
 
Your Task: 
 
1. API Automation (Hands-on): Write a mock API test script (using Postman, RestAssured, Cypress, or your preferred tool) that simulates hitting a POST /sync-survey endpoint with a JSON payload. How would you write tests to ensure the API 
correctly handles duplicate local IDs without overwriting data? Include the script in your 
repo. 

    => To handle the collision of duplicate local IDs without data overwrite, the backend must rely on a composite key (e.g., device_id + local_id) rather than treating local_id as a global primary key.

    **Implementation**: [SyncApiTest.java](https://github.com/themihirbhagat/Agro-Assessment/blob/main/src/test/java/com/agro/test/SyncApiTest.java)



2. Strategy (README.md): Explain how you would test the Android app's background sync behavior under poor network conditions (e.g., network dropping exactly at 99% of a photo upload). What tools (e.g., Charles Proxy, ADB) would you use, and how? 

    => The app stores data offline using a Room database and uses Android's 'WorkManager' to push that data to the server when the internet is back. 
    
    1. To observe what happens when network drop at 99% upload I would use Charles proxy to route Android device through it and throttle the connection down to a slow speed giving me enough time to intercept it.

    - I would set a breakpoint specifically on the POST endpoint (/sync-survey) in Charles.

    - I would let the app try to sync. Once Chales catches the request I will forcefully drop the request inside Charles or turn off the device's Wifi by running ```adb shell svc wifi disable```

    - I will check that Survey's Sync status on the app's dashboard is not marked as Synced. Instead, 'WorkManager' should quietly queue the job retry later and the app should not freeze or crash during the sync process or post process.

    2. To Prevent the DDoS (Test Concurrency & Jitter)

    - When 50,000 field workers reach a town with intenet at 6 PM, their apps will all wake up to sync at the same time. If all of their devices hit our backend at the same time, the API will crash.

    - I will test it by taking 4 to 5 devices (or emulators), put them all in Airplane Mode, and fill out a few offline surveys on each devices.

    - I will turn off Airplane Mode on all devices at the exact same time so they all regain internet simultaneously. 

    - I'd monitor the system logs using ADB ```adb logcat | grep SyncWorker```

    - I want to see the sync requests firing at staggered times (e.g., Device 1 at 3 seconds, Device 2 at 17 seconds, Device 3 at 44 seconds). If every single device tries to sync immediately, it means the developers haven't implemented randomized "Jitter". I would flag this as a major risk to the dev team.


### Scenario B: Dynamic Form Rendering & Regression (Web & Mobile) 

Context: Our web-based Survey Builder allows admins to create forms with 20+ input types, 
dynamic skip logic (visibility based on previous answers), and infinite repeating rosters (e.g., 
asking 5 questions for every household member). The mobile app downloads this complex 
JSON schema and renders the UI dynamically. Currently, when developers add a new feature 
to the builder, it often breaks existing form logic in the mobile app. 
 
Attachment: “3.Sample Survey Schema.json” 
 
Your Task: 
 
1. Web Automation (Hands-on): Provide a sample automated test script (using Cypress, 
Playwright, or Selenium) that verifies a dynamic skip logic rule on a web form (e.g., If 
"Do you have a hobby?" = "Yes", assert that the "What is your hobby?" text field 
becomes visible). 

    => Implementation: [SkipLogicTest.java](https://github.com/themihirbhagat/Agro-Assessment/blob/main/src/test/java/com/agro/test/SkipLogicTest.java)



2. Strategy (README.md): How do you design a regression test suite for an app where 
screens aren't static, but generated dynamically from JSON? What parts would you 
automate, and what parts would you leave for manual exploratory testing? 