#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h> 
#include <FirebaseClient.h>
#include <ArduinoJson.h>
#include "time.h"
#include <WiFiManager.h>
#include <Preferences.h>


// Firebase credentials
#define DATABASE_URL "https://plantwateringdatabase-default-rtdb.firebaseio.com/"
#define WEB_API_KEY "AIzaSyD6n9M0yJhekVh1fvyfnA5ZaER9Ic0WUg0"
#define USER_EMAIL "gabe.wilardi@gmail.com"
#define USER_PASSWORD "123456"

#define water_sensor 13
#define moisture_sensor_A 36
#define water_pump_A 22

#define moisture_sensor_B 35
#define water_pump_B 21


const long gmtOffset=-18000; //-5 hours offset for EST from GMT, from seconds
const int daylightOffset=3600;


const char* ntpServer="pool.ntp.org";

WiFiManager wm;

// Set web server port number to 80
WiFiServer server(80);

// Variable to store the HTTP request
String header;

void processData(AsyncResult &result);
//void processCommand(AsyncResult &result);
UserAuth user_auth(WEB_API_KEY, USER_EMAIL, USER_PASSWORD);


// Database relevant objects
FirebaseApp app;
WiFiClientSecure ssl_client;
WiFiClientSecure ssl2_client;
WiFiClientSecure get_client;

using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
AsyncClient a2Client(ssl2_client);
AsyncClient getClient(get_client);

RealtimeDatabase Database;
//RealtimeDatabase StreamDatabase;

// Holding necessary plant variables in one type
struct hardwarePackage {

  int moisture_threshold; // Moisture threshold set by the app, originally initialized to 40
  bool taken; // State of the hardware package (Is there a plant there)
  bool manual_watering;
  int manual_duration;
  bool manual_watering_serviced;
};

struct pins {
  int moisture_pin;
  int pump_pin;
};

Preferences preferences;

pins pins1 = {moisture_sensor_A, water_pump_A};
pins pins2 = {moisture_sensor_B, water_pump_B};
// Initializing plant bundle structures
hardwarePackage bundle1 = {0, false, false, 0, true};
hardwarePackage bundle2 = {0, false, false, 0, true};

// Intialization of time tracking variables
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 20000; // one minute interval, reduce for testing

unsigned long last_connection_time = 0;
const unsigned long connection_check_interval = 15000;

unsigned long portal_start_time = 0;
const unsigned long portal_timeout = 600000; // 10 minutes

const int automatic_pump_time = 1;
const unsigned long pump_cooldown = 60000;
unsigned long last_pump_time = 0;

unsigned long pump1_last_time = 0;
unsigned long pump2_last_time = 0;
const unsigned long pump_interval = 60000;


volatile int pulse_count = 0;


bool connected = false;
int reconnect_attemps = 0;
const int max_reconnects = 4;
volatile bool portal_running = false;


// State variable for the status of database upload and requests
volatile bool upload_complete = true;
volatile bool watering_requested = false;
volatile bool manual_watering_done = true;

void configModeCallback(WiFiManager *myWiFiManager) {
  portal_running = true;
  Serial.println("Portal started");
}

void GotIP_Handler(WiFiEvent_t wifi_event, WiFiEventInfo_t wifi_info) {
  Serial.print("Local ESP32 IP: ");
  Serial.println(WiFi.localIP());

  portal_running = false;
  reconnect_attemps = 0;
}

int getMoisture(int moisture_sensor_pin) {
  int sensorValue = analogRead(moisture_sensor_pin);
  int moisturePercentage;
  if (moisture_sensor_pin == pins1.moisture_pin){
    moisturePercentage = 100 - ((sensorValue - 1400) * 100 / (3560 - 1400));
  }
  else { 
    moisturePercentage = 100 - ((sensorValue - 350) * 100 / (2200 - 350));
  }  
  if (moisturePercentage < 0) {
    moisturePercentage = 0;
  }
  else if (moisturePercentage > 100){
    moisturePercentage = 100;
  }

  return moisturePercentage;
}

String getWaterTankState(int float_sensor_pin) {
  int refillState = digitalRead(float_sensor_pin); // Reading float water sensor state
  String messageWater;
  if (refillState == 1) { // When input is HIGH -> bulb is lifted -> Water is sufficent
    messageWater="Sufficient water is available";
    //Serial.println(messageWater);
  }
  else { // when input is LOW -> bulb is lowered -> Water supply needs a refill
    messageWater="Insufficient water supply, please refill";
    //Serial.println(messageWater);
  }

  return messageWater;
}

void activatePump(int pump_pin, int duration) {
  Serial.println("Activating pump: ");
  digitalWrite(pump_pin, HIGH);
  delay(duration*1000);
  digitalWrite(pump_pin, LOW);
  Serial.println("Closing pump");
}

/**void checkForWatering(hardwarePackage *bundle, int moisturePercentage) {

  if (!bundle->manual_watering && moisturePercentage <= bundle->moisture_threshold) {
    Serial.println("Automatic watering incoming");
    activatePump(bundle->pump, automatic_pump_time);
  }
  else if (!bundle->manual_watering_serviced){
    bundle->manual_watering = false;
    Serial.println("Still servicing previous request");

  app.loop();
  if (app.ready()) {
    Serial.println("Ready to send confirmation of manual watering");
    Database.set<bool>(aClient, "/plants/" + bundle->name + "/auto_watering_mode", true, processData, "RTDB_Confirming_Manual_Watering_Bundle1");
    }
  }
  else if (bundle->manual_watering) {
    Serial.println("Handling new manual request:");
    activatePump(bundle->pump, bundle->manual_duration);
    bundle->manual_watering = false;
    bundle->manual_watering_serviced = false;
  }
}**/

void setup() {
  pinMode(water_sensor, INPUT_PULLUP); // setting the water sensor pin to input

  pinMode(water_pump_A, OUTPUT); // Setting the water pump pin to output
  digitalWrite(water_pump_A, LOW); // Set the water pump off to begin
  pinMode(water_pump_B, OUTPUT);
  digitalWrite(water_pump_B, LOW);

  //pinMode(flow_sensor_A, INPUT); // Define the water flow sensor pin as an input
  //attachInterrupt(digitalPinToInterrupt(flow_sensor_A), flow_rate_ISR, RISING); // Attach ISR to flow sensor pin


  Serial.begin(115200);

  WiFi.onEvent(GotIP_Handler, ARDUINO_EVENT_WIFI_STA_GOT_IP);

  wm.setCustomHeadElement(
 "<style>"
  "body { font-family: 'Segoe UI', sans-serif; background: #EEF4EA; color: #2A4A26; }"
  "input { background: #FFFFFF; border: 1px solid rgba(78,143,69,0.25); "
  "        color: #2A4A26; border-radius: 8px; padding: 10px; }"
  "button { background: #4E8F45; color: white; border: none; "
  "         border-radius: 8px; padding: 12px 24px; font-weight: 600; cursor: pointer; }"
  "button:hover { background: #427A3A; }"
  ".c { text-align: center; }"
  "</style>"
);

  wm.setTitle("Device Setup for the PlantWatering app");
  wm.setWiFiAutoReconnect(true); //autoreconnect if something happens
  wm.setConfigPortalBlocking(false); //do not block loop execution if WiFi disocnnects
  wm.setConfigPortalTimeout(600); //timeout after 10 minutes if no connection
  wm.setAPCallback(configModeCallback);

  portal_running = true;
  connected = wm.autoConnect("ESP32-Setup", "configure123");
  portal_start_time = millis();

  if (!connected){ //if fails to connect, restart
    Serial.println("Failed to intially connect");
  }
  // Configure SSL client

  ssl_client.setInsecure();
  ssl_client.setConnectionTimeout(1000);
  ssl_client.setHandshakeTimeout(5);
  
  ssl2_client.setInsecure();
  ssl2_client.setConnectionTimeout(1000);
  ssl2_client.setHandshakeTimeout(5);
  
  get_client.setInsecure();
  get_client.setConnectionTimeout(1000);
  get_client.setHandshakeTimeout(5);

  configTime(gmtOffset,daylightOffset,ntpServer);

  //Initialize Firebase
  initializeApp(aClient, app, getAuth(user_auth), processData, " authorisationTask");
  app.getApp<RealtimeDatabase>(Database);
  Database.url(DATABASE_URL);


  if (!preferences.begin("plants", false)) {
    Serial.println("Failed to open Preferences");
  }
  // Get plant specifications from flash memory in case of ESP restart
  preferences.getBytes("bundle1", &bundle1, sizeof(bundle1));
  Serial.print("bundle1.taken: ");
  Serial.println(bundle1.taken);
  Serial.print("bundle1 moisture threshold: ");
  Serial.println(bundle1.moisture_threshold);

  preferences.getBytes("bundle2", &bundle2, sizeof(bundle2));
  Serial.print("bundle2.taken: ");
  Serial.println(bundle2.taken);
  Serial.print("bundle2 moisture threshold: ");
  Serial.println(bundle2.moisture_threshold);

  preferences.end();
}



void loop() {

  wm.process();

  int m = getMoisture(pins2.moisture_pin);
  int a = getMoisture(pins1.moisture_pin);
  Serial.print("Moisture number bundle1: ");
  Serial.println(a);
  Serial.print("Moisture number for bundle2: ");
  Serial.println(m);

  unsigned long connection_time = millis();
  if (portal_running && (millis() - portal_start_time) >= portal_timeout) {
    portal_running = false;
    Serial.println("Configuration portal timed out.");
  }
  if (connection_time - last_connection_time >= connection_check_interval) {
    last_connection_time = connection_time;
    Serial.println("Checking connection status");

    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("Connected to the internet...");
      reconnect_attemps = 0;
    }
    else {
      WiFi.reconnect();
      reconnect_attemps++;

      if (reconnect_attemps >= max_reconnects && !portal_running) {
        Serial.println("Starting configuration portal...");
        
        wm.startConfigPortal("ESP32-Setup", "configure123");
        portal_start_time = millis();
        reconnect_attemps = 0;
      }
    }
  }

  else {
    if (app.ready() && !portal_running) {
      bool start_portal = Database.get<bool>(getClient, "/connection_portal");
      if (start_portal) {
  
        wm.startConfigPortal("ESP32-Setup", "configure123");
        portal_start_time = millis();
      }
    }
  }



  app.loop();
  // Parsing plant objects from database and assigning them to hardware if necessary
  if (app.ready()) {
    JsonDocument doc; 
    String jsonString = Database.get<String>(getClient, "/plants"); // Pulls Nodes under plants in Json object

    DeserializationError error = deserializeJson(doc, jsonString);
    
    if (error) { // handle error case
      Serial.println(error.c_str());
    }

    // if no plants in database, make sure that both hardware bundles are deallocated
    else if (doc.isNull()) {
      //Serial.println("No plants found. De-allocating sensors if needed");
      bundle1.taken = false;
      bundle1.manual_watering = false;
      bundle1.manual_watering_serviced = true;

      bundle2.taken = false;
      bundle2.manual_watering = false;
      bundle2.manual_watering_serviced = true;
    }

    else {
      JsonObject plants = doc.as<JsonObject>();
      bool is_slot1 = false; // Flag for slot1
      bool is_slot2 = false; // Flag for slot2
      
      int loopCount = 0;
      for (JsonPair plant : plants) {
        loopCount++;
        if (loopCount > 2) // Break the for loop if more than two plant objects in database
          break;
        
        // Store the name of the plant in String for future reference
        String plantName = plant.key().c_str();
        // Parse the attributes of the plant in data
        JsonObject data = plant.value().as<JsonObject>();

        if (plantName == "slot1") {
          is_slot1 = true;

          // Update plant1 information from database
          bundle1.moisture_threshold = data["threshold"];
          if (data["auto_watering_mode"] != true) {
            bundle1.manual_watering = true;
            bundle1.manual_duration = data["manual_watering_duration"];
          }
          bundle1.taken = true;
        }

        else if (plantName == "slot2") {
          is_slot2 = true;

          // update plant2 information from database
          bundle2.moisture_threshold = data["threshold"];
          if (data["auto_watering_mode"] != true) {
            bundle2.manual_watering = true;
            bundle2.manual_duration = data["manual_watering_duration"];
          }
          bundle2.taken = true;
        }
        //char name[16]; 
        //plantName.toCharArray(name, sizeof(name));
  
        // Conditional clause handling new plant that isn't registered in hardware
        /**if (data["taken"] == false) {

          // First condition checks if bundle1 isn't taken and then checks if the plant is not already registered in bundle2
          else if (!bundle1.taken && strcmp(name, bundle2.name) != 0) { 

            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + plantName + "/taken", 1, processData, "RTDB_Assigning_Plant");
            while (!upload_complete){
              app.loop();
              delay(10);
            }
            upload_complete = false;
            bundle1.taken = true;
            //int n = sizeof(name) / sizeof(name[0]);
            //copyArr(name, bundle1.name, n);

          }

          else if (!bundle2.taken && strcmp(name, bundle1.name) != 0) {
            
            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + plantName + "/taken", 1, processData, "RTDB_Assigning_Plant");
            while (!upload_complete){
              app.loop();
              delay(10);
            }
            upload_complete = false;
            bundle2.taken = true;
            //int n = sizeof(name) / sizeof(name[0]);
            //copyArr(name, bundle2.name, n);
          }
        } **/

      } // End of for loop

      if (!is_slot1) {
        // Set flags back to default values if the slot1 node is not in the database 
        bundle1.taken = false;
        bundle1.manual_watering = false;
        bundle1.manual_watering_serviced = true;
      }
      if (!is_slot2) {
        // Set flags back to default values if the slot2 node is not in the database 
        bundle2.taken = false;
        bundle2.manual_watering = false;
        bundle2.manual_watering_serviced = true;
      }

      if (!preferences.begin("plants", false)) {
        Serial.println("Failed to open Preferences");
      }
      // Save plant hardware data to flash memory
      preferences.putBytes("bundle1", &bundle1, sizeof(bundle1));
      preferences.putBytes("bundle2", &bundle2, sizeof(bundle2));

      preferences.end();
    }    
  }

  struct tm timeInfo;
  if (!getLocalTime(&timeInfo)){
    Serial.println("Failed to procure time");
  }
  char timeWateredChar[30];
  String timeWatered;

  strftime(timeWateredChar,sizeof(timeWateredChar), "%A, %B %d %H:%M:%S", &timeInfo);
  timeWatered=String(timeWateredChar);

  int moisturePercentage_A;
  // Check for watering logic
  if (bundle1.taken) {
    moisturePercentage_A = getMoisture(pins1.moisture_pin);

    if (!bundle1.manual_watering && moisturePercentage_A <= bundle1.moisture_threshold) {
      Serial.println("Automatic watering incoming");
      unsigned long current_pump1_time = millis();
      if (current_pump1_time - pump1_last_time >= pump_interval) {
        activatePump(pins1.pump_pin, automatic_pump_time);
        pump1_last_time = current_pump1_time;
      }
      else {
        Serial.println("Pump 1 was used less than a minute ago, CANNOT start pump");
      }
    }
    else if (!bundle1.manual_watering_serviced){
      bundle1.manual_watering = false;
      Serial.println("Still servicing previous request");

      app.loop();
      if (app.ready()) {
        Serial.println("Ready to send confirmation of manual watering");
        Database.set<bool>(aClient, "/plants/slot1/auto_watering_mode", true, processData, "RTDB_Confirming_Manual_Watering_Bundle1");
      }
    }
    else if (bundle1.manual_watering) {
      Serial.println("Handling new manual request:");
      unsigned long current_pump1_time = millis();
      if (current_pump1_time - pump1_last_time >= pump_interval) {
        bundle1.manual_watering = false;
        bundle1.manual_watering_serviced = false;
        
        preferences.begin("watering_serviced", false);
        preferences.putBytes("bundle1", &bundle1, sizeof(bundle1));
        preferences.end();
        
        pump1_last_time = current_pump1_time;
        activatePump(pins1.pump_pin, automatic_pump_time);
      }
      else {
        Serial.println("Pump 1 was used less than a minute ago, CANNOT start pump");
      }
    }

  }  
  int moisturePercentage_B;
  // Check for watering logic
  if (bundle2.taken) {
    moisturePercentage_B = getMoisture(pins2.moisture_pin);

    if (!bundle2.manual_watering && moisturePercentage_B <= bundle2.moisture_threshold) {
      Serial.println("Automatic watering incoming");
      unsigned long current_pump2_time = millis();

      // Only water if the previous watering event happened over 60 seconds ago
      if (current_pump2_time - pump2_last_time >= pump_interval) {
        activatePump(pins2.pump_pin, automatic_pump_time);
        pump2_last_time = current_pump2_time;
      }
      else {
        Serial.println("Pump 2 was used less than a minute ago, CANNOT start pump");
      }
    }
    else if (!bundle2.manual_watering_serviced){
      bundle2.manual_watering = false;
      Serial.println("Still servicing previous request");

      app.loop();
      if (app.ready()) {
        Serial.println("Ready to send confirmation of manual watering");
        // Update the database auto watering variable
        Database.set<bool>(aClient, "/plants/slot2/auto_watering_mode", true, processData, "RTDB_Confirming_Manual_Watering_Bundle1");
      }
    }
    else if (bundle2.manual_watering) {
      Serial.println("Handling new manual request:");
      unsigned long current_pump2_time = millis();
      
      // Only water if the previous watering event happened over 60 seconds ago
      if (current_pump2_time - pump2_last_time >= pump_interval) {
        bundle2.manual_watering = false;
        bundle2.manual_watering_serviced = false;

        // Save manual watering flags to flash memory in case of ESP restart
        preferences.begin("watering_serviced", false);
        preferences.putBytes("bundle2", &bundle2, sizeof(bundle2));
        preferences.end();
        
        pump2_last_time = current_pump2_time;
        activatePump(pins2.pump_pin, automatic_pump_time);
      }
      else {
        Serial.println("Pump 2 was used less than a minute ago, CANNOT start pump");
      }
    }

  }  

  String messageWater = getWaterTankState(water_sensor);


  app.loop();
  // Sending sensor readings and time to database
  if (app.ready()) {
    // Periodic data sending every minute
    unsigned long currentTime = millis();
    if (currentTime - lastSendTime >= sendInterval){
      // Update the last send time
      lastSendTime = currentTime;

      // Send last time watered
      Database.set<String>(a2Client, "/last_time", timeWatered, processData, "RTDB_Send_Time");
      
      // Send moisture level
      if (bundle1.taken) {
        Database.set<int>(aClient, "/plants/slot1/moisture_level", moisturePercentage_A, processData, "RTDB_Send_MoistureLevel");

        // Send water tank state
        Database.set<String>(aClient, "/plants/slot1/water_level", messageWater, processData, "RTDB_Send_WaterLevel");
      }
      if (bundle2.taken) {
        Database.set<int>(aClient, "/plants/slot2/moisture_level", moisturePercentage_B, processData, "RTDB_Send_MoistureLevel");

        // Send water tank state
        Database.set<String>(a2Client, "/plants/slot2/water_level", messageWater, processData, "RTDB_Send_WaterLevel");
      }
    }

    
  }

  pulse_count = 0;
  delay(5000); // Delay for two seconds before the next reading
}

void processData(AsyncResult &result) {
  Serial.println("Callback entered for " + result.uid());
  if (!result.isResult()) {
    Serial.println("Authentication failed");
    return;
  }  

  if (result.isError()) {
    Firebase.printf("Upload failed: %s\n", result.error().message().c_str());
    if (result.uid() == "RTDB_Assigning_Plant") {
      upload_complete = true;
    }
    else if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle1") {
      bundle1.manual_watering_serviced = true;
    }
    else if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle2") {
      bundle2.manual_watering_serviced = true;
    }
    return;
  }  

  if (result.available()) {
    Firebase.printf("Upload successful: %s\n", result.uid().c_str());
    // Water flow and volume data case interrupt CPU and must be have their condition variable flipped when completed
    if (result.uid() == "RTDB_Assigning_Plant") {
      upload_complete = true;
    }
    else if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle1") {
      bundle1.manual_watering_serviced = true;
    }
    else if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle2") {
      bundle2.manual_watering_serviced = true;
    }
  }
    
}

