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


    => 1. What I Would Automate
    - Instead of writing scripts for hundreds of different forms, I would create one massive, static JSON file. This single schema would contain exactly one instance of every supported feature: all 20+ input types, a Matrix grid, a Roster, and every validation rule. By loading this single file into the app, our automation script verifies the engine can render every component without crashing.   
    
    - Data-Driven Locators: Hardcoded locators (like By.cssSelector(".submit-btn")) are too brittle here. The automation framework must dynamically read the JSON schema and target elements using their unique data keys, such as data-question-id="q_1".   
    
    - Skip Question Logic: I would automate the business logic that the JSON schema drives. This includes asserting that Skip Logic hides/shows questions correctly, boundary validations trigger correctly (like entering 25 hours when the rule is "max": "24"), and High-Frequency Checks (HFC) fire the expected reask_once or block prompts.  

    2. What I Would Leave for Manual Exploratory Testing
    
    - Visual Layouts on Budget Devices: Automation tools like Appium are functionally blind to ugly layouts. If a complex Matrix question is nested inside a repeating Roster, it might get squeezed off the screen on a budget Android phone. Appium will still find the element in the DOM and pass the test, but a manual tester will immediately see the UI is broken.
    
    - Hardware Interruptions & State Loss: I would manually test the physical device features. For example, testing what happens when a user takes a photo or records an audio clip, but receives an incoming phone call or rotates the screen right in the middle of it.   
    
    - Low-Literacy UX Reality Checks: The app is designed for users with limited reading skills. Manual exploratory testing is required to ensure touch targets remain large enough, color feedback (red/green) makes logical sense, and image-based options (like pictures of different house types) render clearly beside their labels.


### Scenario C: Offline Sync & State Management (Android WorkManager & Room) 

Context: The Android app saves survey data locally in a Room database and uses WorkManager to trigger a background sync when internet connectivity is restored. We need to verify data integrity before, during, and after this sync process. 
 
Your Task: 
 
1. Technical Inspection Strategy (README.md): Imagine you are provided with the release APK. Write down the exact steps and tooling you would use to: 
- Extract and inspect the local Room SQLite database from an unrooted test device to verify an offline survey was saved correctly before syncing. 
- Force the background WorkManager sync job to execute immediately via ADB for testing purposes. 
- Simulate a failure by intercepting the network request (e.g., using Charles Proxy/Proxyman) and returning a 500 Internal Server Error. How do you verify the app correctly schedules a retry without losing the local data? 

    => 
    
    **1. Extract and Inspect the Room Database on an Unrooted Device (Using ADB Backup )**
    
    Because a release APK has android:debuggable="false", direct shell commands like run-as or accessing /data/data/<package.name>/databases/ will be blocked by Android’s sandboxing on an unrooted device.

    If the app's AndroidManifest.xml leaves android:allowBackup="true" (the Android default):

    1. Pull the app data archive: ```adb backup -f app_backup.ab -noapk <package.name>```

        (Confirm the backup prompt on the phone screen without setting a password).

    2. Unpack the .ab archive using Android Backup Extractor (abe.jar): 
    
        ```bash
        java -jar abe.jar unpack app_backup.ab app_backup.tar 
        tar -xvf app_backup.tar 
        ```

    3. Locate and inspect the SQLite database:

        Navigate to apps/<package.name>/db/.

        Open the database file using the SQLite CLI or a GUI like DB Browser for SQLite:

        ```bash
        sqlite3 <database_name>.db "SELECT id, local_id, sync_status, payload FROM surveys;"
        ```


        Verification: Confirm that the newly created survey record exists, matches the entered fields, and has its status flag marked as PENDING or UNSYNCED.   

    #### 2. Force Immediate Execution of WorkManager Sync via ADB

    Instead of waiting for system network constraints to trigger WorkManager naturally, force the underlying Android JobScheduler to run the task immediately.
    
    1. Locate the WorkManager Job ID: Dump the active jobs registered for the application:
    
        ```bash
        adb shell dumpsys jobscheduler | grep -A 25 <package.name>
        ```
    
        Look for the job associated with ```androidx.work.impl.background.systemjob.SystemJobService``` or your app's SyncWorker. Note its numeric Job ID (e.g., 1001).
    
    2. Force execute the job:
    
        Use the -f (force) flag to bypass battery, charging, and network constraints:

        ```bash
        adb shell cmd jobscheduler run -f <package.name> <JOB_ID>
        ```

    3. Verify execution in real-time logs:
        
        Filter logcat to monitor the worker lifecycle:
            
        ```bash
        adb logcat -s WM-WorkerWrapper WorkManager:D
        ```
        
    #### 3. Simulate 500 Error, Verify Retry Scheduling, and Confirm Data Integrity
    
    **Step 1: Network Interception Setup**
    
    Release APK SSL Note: Because Android 7.0+ ignores user-installed CA certificates on release builds by default, the APK must either be patched with a permissive network_security_config.xml (via the apktool method above) or tested on an Android emulator running a root-level system certificate store.
    
    1. Configure Charles Proxy / Proxyman:
    Connect the test device to the same Wi-Fi and configure the proxy host and port (e.g., 8888).
    
    - Add a Rewrite Rule (or Map Remote) targeting the sync endpoint:
    - URL: POST /api/v1/sync-survey
    - Action: Replace response status code 200/201 with 500 Internal Server Error.
    - Response Body: {"error": "InternalServerError", "message": "Database temporary write failure"}.
    
    **Step 2: Trigger the Sync and Induce Failure**
    
    - Fill out an offline survey on the device.
    Re-enable Wi-Fi or run the forced ADB command:
    
        ```bash
        adb shell cmd jobscheduler run -f <package.name> <JOB_ID>
        ```

        Observe Charles Proxy catching the request and returning the mock 500 response.
    
    **Step 3: Verifications**

    - Logcat Assertion (Worker State):
    Monitor logcat for Result.retry() handling:
    
        ```bash
        adb logcat | grep -E "WM-WorkerWrapper|SyncWorker"
        ```
    
    - Verify that the log outputs:
    Worker result RETRY for Work [ id=... ]
    
    - Verify that WorkManager calculates an exponential backoff time (e.g., Scheduling next attempt at T + 30000ms).
    Database Integrity Assertion:
    Re-extract the database using the ADB method outlined in Section 1:
    
        ```bash
        sqlite3 extracted_survey.db "SELECT id, sync_status, retry_count FROM surveys;"
        ```

    - Confirm that the survey record still exists and was not deleted or truncated.
    - Confirm that sync_status remains FAILED or PENDING_RETRY.
    - Confirm that retry_count has incremented (e.g., from 0 to 1).
    
    **UI/State Assertion:**
    
    Open the app and check the central dashboard:
    - The dashboard must continue to display the survey under "Surveys Pending Sync" rather than updating it to "Synced".
    - No unhandled crash dialogs or UI freeze should occur on screen.


