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


## Part 2: Complex Scenarios & Edge Cases 

### Scenario D: The Edge-Case Device Constraints 

Context: Our field workers are using 4GB RAM phones. We are getting reports that after 
watching 3-4 offline video training modules, the app suddenly crashes (ANR - Application Not 
Responding). However, it works perfectly on our high-end office test devices. 
 
● Question: Walk us through exactly how you would reproduce this issue, debug it, and 
prove the root cause to a developer. What specific ADB commands, profilers, or device 
manipulation techniques would you use to simulate low storage and memory limits? 

=> To prove exactly why this ANR is happening on budget devices, we need to strip away the safety net of high-end office hardware, force the failure, and capture the system-level traces.

**1. Simulating the Hardware Specs**

- We cannot rely on high-end test devices. We must artificially choke the environment using ADB and emulators to match the field hardware.

- Restrict RAM: I would spin up an Android Virtual Device (AVD) explicitly configured with 4GB of RAM and a restricted VM heap size (e.g., 128MB or 256MB). Alternatively, on a physical rooted device, you can use adb shell setprop dalvik.vm.heapsize 256m and reboot.

- Simulate Storage Exhaustion: Low-end phones often have almost full storage, which severely impacts SQLite performance and OS swapping. I would fill the test device's storage using a dummy file via ADB:

    ```bash
    adb shell dd if=/dev/zero of=/sdcard/dummy_large_file.bin bs=1024k count=10000
    # (This command creates a 10GB file of zeroes, instantly eating up free space).
    ```

#### 2. Execution & Real-Time Profiling
   
- An ANR means the Main/UI thread was blocked for more than 5 seconds. In the context of "watching 3-4 videos," this is almost certainly caused by Garbage Collection (GC) thrashing (the system desperately trying to free memory) or loading uncompressed thumbnail bitmaps directly on the main thread.

- The Android Studio Memory Profiler: I would connect the choked device to Android Studio, open the Profiler tab, and monitor the memory timeline while manually clicking through the 4 training modules.

- Command-Line alternative: If running automated Appium flows, I would track memory growth programmatically after every single video module using:

    ```bash
    adb shell dumpsys meminfo <your.app.package>
    ```
- What to look for: I am looking for a "Staircase Pattern" in the memory graph. If memory jumps by 50MB when Video 1 opens, but only drops by 5MB when Video 1 closes, we have a textbook memory leak. By Video 4, the app hits the 4GB ceiling, triggers constant GC pauses, freezes the UI thread, and throws the ANR.

#### 3. Extracting the Smoking Gun (Traces)

- Once the app locks up and the ANR dialog appears, the Android OS automatically records exactly what every thread was doing at the moment of the freeze. This is the undeniable proof for the developer.

- Pull the ANR Trace:

    ```bash
    adb pull /data/anr/traces.txt ./anr_traces.txt
    ```
    Note: On newer Android versions, this might be located via adb bugreport or in ```/data/anr/anr_*```

- Analyze the Trace: I would open traces.txt and search for "main" prio=5. This block shows the exact method the Main Thread was stuck on.

#### 4. Presenting the Root Cause to the Developer

- I would open a bug ticket containing three definitive pieces of evidence:

- The Heap Dump (.hprof file): Captured from the Profiler right before the crash, proving that 4 instances of the mock video thumbnail activity are being held in memory and not destroyed.

- The traces.txt snippet: Showing the Main Thread blocked by BitmapFactory.decodeResource or blocked by Garbage Collection (WaitingForGcToComplete).

- The Fix Recommendation: Point out that because they are targeting budget phones, they must use an image loading library like Glide or Coil to downsample the thumbnails, and ensure they are unbinding the view when the fake video finishes so the garbage collector can clear it.

### Scenario E: The HFC (High-Frequency Checks) Engine 

Context: The Survey Builder includes an HFC engine that runs on the mobile device. It uses 
cross-question logic to ensure data quality (e.g.,  
if Age < 15 AND Education = 'College', Action = 'reask_and_flag'   

OR 

if Sleep Well = Yes AND Sleep hours < 6, Action = ‘reask_and_flag’  ). 

● Question: Design a brief test plan (can be a markdown table) for testing the HFC engine 
itself. Include positive, negative, and edge-case scenarios to ensure it blocks invalid data 
when necessary but doesn't trap a user in an infinite loop. 


| Scenario Category | Test Case Description | Input Data | Expected Action & System Behavior |
| :--- | :--- | :--- | :--- |
| **Positive** | **Clean Data / Normal Flow:** Valid data that falls safely outside the rule conditions. | `q_2` (Sleep Well) = "Yes"<br>`q_3` (Sleep Hours) = 8. | **Pass:** No rules match. The engine remains silent, and the user proceeds normally. |
| **Negative** | **Suspicious Data (Soft Check):** Data matches the anomaly criteria mapped to a soft action. | `q_2` (Sleep Well) = "Yes"<br>`q_3` (Sleep Hours) = 4. | **`reask_once`:** The app pauses flow and displays the specific warning message ("unusually low number"). |
| **Negative** | **Impossible Data (Hard Check):** Data matches a critical impossibility mapped to a hard action. | Age = 12<br>Education = "College"<br>(Configured as `block` severity). | **`block`:** The app prevents the user from proceeding or submitting the form until at least one of the conflicting fields is changed. |
| **Edge Case** | **Infinite Loop Prevention:** User insists the suspicious data is factually correct. | User enters `q_3` = 4, receives warning, and confirms `q_3` = 4 again. | **Accept & Flag:** Because the action is `reask_once`, the engine must accept the submission on the second attempt, flag the data for the supervisor, and unblock the UI. |
| **Edge Case** | **Combinator Logic (`all` vs `any`):** Ensuring multi-condition rules strictly follow boolean logic. | `q_2` (Sleep Well) = "No"<br>`q_3` (Sleep Hours) = 4. | **Pass:** The rule uses the `all` (AND) combinator requiring Sleep Well to be "Yes". Because one condition fails, the HFC must *not* trigger. |
| **Edge Case** | **Empty / Null Evaluation:** A dependent question is evaluated before the user provides input. | `q_2` (Sleep Well) = "Yes"<br>`q_3` (Sleep Hours) = *Left blank*. | **Safe Evaluation:** The engine must safely evaluate `empty < 6` without throwing a Null Pointer Exception or crashing the app. |
| **Edge Case** | **Dynamic Unblocking:** Correcting a blocked state in real-time. | User triggers a `block` on Age/College, then changes Age to 19. | **Immediate Unblock:** The engine must re-evaluate instantly on input change and remove the block without requiring a screen refresh. |


## Part 3: QA Process & AI Integration 

1. Process Creation: You are joining as our first dedicated QA Engineer. Developers currently test their own code and push directly to the Play Store. Walk us through your first 30 days. How do you implement a structured QA process (entry/exit criteria, staging environments) without slowing down the fast-paced development team? 

    => 
    
    **Days 1–10: Observe & Setup (No Roadblocks)**
    - The goal is to map the current flow without slowing anyone down.
    - **Create Staging:** Set up a dedicated staging environment (backend and app build) so testing is isolated from production data.
    - **Shadow Releases:** Watch how developers currently push code to the Play Store. Do not block their releases yet.
    - **Silent Testing:** Test their builds in the background. Log highly detailed bugs (including ADB crash traces, Room SQLite dumps, and Charles Proxy logs)

    **Days 11–20: Introduce Lightweight Rules (Entry/Exit Criteria)**
    - Introduce just enough structure in Jira/Trello to keep bad builds out of QA, but keep the rules simple so developers don't feel bogged down.
    - **Entry Criteria (When is it ready for QA?):** The code must compile, pass existing CI unit tests, and include a one-sentence "How to Test" note from the developer.
    - **Exit Criteria (When is it ready for Release?):** Core flows (like offline sync and login) must pass, and the app must not crash (ANR) on budget devices. Minor visual glitches go into the backlog so the release can still go out on time.

    **Days 21–30: Build the Automation Safety Net**
    - Manual testing will eventually become a bottleneck, so you must start automating the core sanity checks.
    - **Lean Framework:** Build a script-driven automation repository using Appium for mobile UI and Java/TestNG/RestAssured for the backend APIs. Keep it lightweight and strictly code-based without relying on bloated commercial record-and-play tools.
    - Write scripts for the most critical paths first, like the massive JSON form and the offline background sync.
    - **CI/CD Integration:** Hook the TestNG/RestAssured scripts into GitHub Actions (or the CI pipeline). This gives developers instant automated feedback on their Pull Requests before you even look at them manually.



2. Leveraging AI: How do you currently use AI tools (like ChatGPT, GitHub Copilot, Gemini, etc.) in your day-to-day QA workflow? Give a specific example of how AI could help you write tests for the complex dynamic schemas mentioned in Scenario B. 

    => 
    
    **Day-to-Day QA Workflow:**
    - **Test Data Generation:** I use AI to instantly generate massive JSON mock payloads or SQL records instead of typing them out manually.
    - **Smart Locators:** AI helps me quickly write complex, resilient XPath and CSS selectors for mobile (Appium) and web elements when unique IDs are missing.
    - **Log Analysis:** I paste large ADB crash dumps or Charles Proxy network logs into the AI to quickly isolate the root cause of complex errors.

    **Testing Complex Dynamic Schemas (Scenario B):**
    - **Generating Edge Cases:** I feed the base schema rules to the AI and ask it to generate dozens of edge-case payloads (e.g., missing mandatory fields, invalid data types, extreme boundary values). 
    - **Data-Driven Execution:** AI helps quickly structure the boilerplate for data providers (like TestNG `@DataProvider`) so the automation framework can iterate through all those generated schema variations in a single run.
    - **Dynamic Assertions:** I use AI to write complex JSONPath queries that validate deeply nested schema responses dynamically, without having to hardcode every single field expectation.

3. Automation Tooling: With limited time, how do you decide what should be automated versus what should not be automated? Why did you choose the specific tools you used for Part 1 of this assignment? 

    => 
    
    **Deciding What to Automate (The ROI Approach):**
    - **What gets automated first:** I prioritize core business flows (the "happy paths" that must work for release), highly repetitive data-driven scenarios (like validating massive JSON schemas), and critical smoke tests that serve as a gatekeeper in the CI/CD pipeline. If a test requires running multiple times per week and the flow is stable, it goes into the automation backlog.
    - **What remains manual:** Features that are still in active development or have a highly volatile UI. I also leave exploratory testing, complex edge cases that rarely occur, and subjective UX/UI feel to manual testing. The maintenance overhead for volatile tests usually outweighs the automation benefits.

    **Why I Chose Appium, Selenium, and RestAssured:**
    - **Mobile Automation (Appium):** It is the industry standard for mobile testing because it relies on the WebDriver protocol, allowing for true native interaction across both Android and iOS. It avoids vendor lock-in and lets me write mobile scripts in the same language as web and API tests.
    - **Web Automation (Selenium):** It provides a mature, robust, and highly customizable foundation for web testing. Instead of relying on bloated commercial record-and-play tools, pure Selenium gives me low-level architectural control to build exactly what the framework needs, handling complex locators and dynamic waits efficiently.
    - **API Testing (RestAssured):** Its fluent domain-specific language (DSL) makes writing API tests highly readable. It excels at parsing deeply nested JSON responses, extracting dynamic tokens, and asserting payloads seamlessly within a Java/TestNG framework.
    - **Unified Ecosystem:** Combining these three open-source tools creates a lightweight, completely script-driven architecture. They integrate perfectly with CI/CD pipelines for fast, reliable, headless execution, avoiding the unnecessary overhead of heavy commercial wrappers.


## Part 4: UI/UX & User-Centric QA 
 
Context: Our end-users are field workers in rural areas. They often have limited reading skills, use small-screen budget devices in bright sunlight, and rely heavily on visual cues (colors, icons). A feature might pass all functional tests but fail completely in the field because the text is too dense, the contrast is too low, or touch targets are too small. 
 
Your Task: 
 
1. Scenario Evaluation: A developer submits a new "Training Video Hub" screen for QA. It features a standard video player, a block of 3 paragraphs describing the video content, and a standard-sized "Next" button at the very bottom of a scrollable page. 
2. Question: As a QA Engineer: 
- how would you evaluate this screen beyond basic functional testing?  
- List at least 5 specific UX/UI issues or rendering risks you would flag based on our target demographic (low literacy, budget phones).  
- How do you systematically test for these issues, and how do you constructively communicate these "non-functional" bugs to the design and development teams? 

    => 
    
    **Evaluating Beyond Functional Testing (Context-Driven Testing):**
    I would shift my mindset from "Does the code work?" to "Does this screen work *for the user, in their actual environment*?". This requires simulating real-world constraints. I would evaluate the app by turning down screen brightness, testing outside in the sun, navigating using only visual cues (ignoring the text entirely), and throttling the network to simulate rural connectivity.

    **5 Specific UX/UI Issues & Risks to Flag:**
    1. **Text Overload (Low Literacy):** Three paragraphs of text will be entirely ignored or cause frustration. Critical instructions must rely on recognizable icons, color-coding, or audio voiceovers rather than dense reading material.
    2. **Hidden "Next" Button (Discoverability):** On a small 4-inch budget screen, placing the "Next" button below the fold guarantees many users will watch the video and get stuck, not realizing they have to scroll. The primary call-to-action should be sticky/floating at the bottom.
    3. **Small Touch Targets (Usability):** A "standard-sized" button and tiny video player controls (play/pause/fullscreen) are difficult to tap accurately in the field, especially on unresponsive budget touchscreens or if the worker has calloused hands.
    4. **Glare and Low Contrast (Visibility):** Standard UI colors and subtle drop shadows wash out completely in direct sunlight. If the video player controls or the "Next" button lack stark, high-contrast boundaries, they become invisible outdoors.
    5. **Bandwidth and Buffering (Rural Connectivity):** Standard video players will likely freeze on poor networks. The UI risks leaving the user staring at a black box if there isn't a highly visible loading indicator, a low-resolution fallback, or a clear offline-mode prompt.

    **Systematic Testing & Constructive Communication:**
    - **Systematic Testing:** 
        - **Accessibility Tools:** I use tools like Google's Accessibility Scanner to mathematically verify touch target sizes (ensuring they meet the 48x48 dp minimum) and validate color contrast ratios.
        - **Device Profiling:** I use Charles Proxy to throttle the network to Edge/3G speeds to observe video buffering behavior, and monitor device logs (via ADB) to ensure the video player isn't draining the battery or spiking the CPU on a low-end device.
    - **Constructive Communication:** 
        - **Show, Don't Just Tell:** Instead of logging a generic "UI is bad" ticket, I provide visual evidence. I attach screenshots of the screen simulated on a 4-inch display, or screen recordings of the app timing out under a throttled network.
        - **Solution-Oriented Framing:** I frame the bug around the user persona to avoid developer defensiveness. Instead of "Your text is too long," I write: *"Risk: Field workers may miss critical steps due to text density. Suggestion: Replace paragraphs with a 3-point icon list and pin the 'Next' button to the bottom."*
        - **Proper Categorization:** I log these tickets with "Usability Enhancement" or "UX Risk" labels. This ensures the feedback is documented and prioritized by Product/Design without creating immediate, aggressive roadblocks for the developer's functional work.