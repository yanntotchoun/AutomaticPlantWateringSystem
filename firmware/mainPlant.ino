
#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h> 
#include <FirebaseClient.h>
#include <ArduinoJson.h>
#include "time.h"


// Firebase credentials
#define DATABASE_URL "https://plantwateringdatabase-default-rtdb.firebaseio.com/"
#define WEB_API_KEY "AIzaSyD6n9M0yJhekVh1fvyfnA5ZaER9Ic0WUg0"
#define USER_EMAIL "gabe.wilardi@gmail.com"
#define USER_PASSWORD "123456"

#define water_sensor 13
#define moisture_sensor_A 36
#define water_pump_A 22
#define flow_sensor_A 15

#define moisture_sensor_B 0
#define water_pump_B 0
#define flow_sensor_B 0

const long gmtOffset=-18000; //-5 hours offset for EST from GMT, from seconds
const int daylightOffset=3600;


// input network credentials
const char* ssid = "AD"; 
const char* password = "William@2750";
const char* ntpServer="pool.ntp.org";

// esp32 network credentials for initial connection with app
const char* esp32_ssid = "ESP32access";
const char* esp32_password = "0987654321";

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
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
RealtimeDatabase Database;
//RealtimeDatabase StreamDatabase;

// Holding necessary plant variables in one type
struct hardwarePackage {
  int moisture_sens; // Moisture sensor pin number
  int pump; // Water pump pin number
  int flow_sens; // Flow rate sensor pin number
  int moisture_threshold; // Moisture threshold set by the app, originally initialized to 40
  bool taken; // State of the hardware package (Is there a plant there)
  String name; // Name of the plant. Set to NULL if there taken = 0
};

// Initializing plant bundle structures
hardwarePackage bundle1 = {moisture_sensor_A, water_pump_A, flow_sensor_A, 40, false, ""};
hardwarePackage bundle2 = {moisture_sensor_B, water_pump_B, flow_sensor_B, 20, false, ""};

// Intialization of time tracking variables
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 20000; // one minute interval, reduce for testing

// Variables to ensure consistent operation of the water pump
int current_pump_state = 0; // 1 for testing
int previous_pump_state = 0;
int automatic_pump_time = 3000;
int manual_pump_time = 0;

// Variables to track water flow through the pump
double flow_rate = 0.0;
double total_flow = 0.0;
volatile int pulse_count = 0;
const double pulses_per_liter = 5880.0;


// Interrupt Service Routine used for flow rate measurement when the water pump is active
void IRAM_ATTR flow_rate_ISR() {
  pulse_count++;
}

// State variable for the status of database upload and requests
volatile bool upload_complete = true;
volatile bool watering_requested = false;

void ConnectedToAP_Handler(WiFiEvent_t wifi_event, WiFiEventInfo_t wifi_info) {
  Serial.println("Connected To The WiFi Network");
}

void GotIP_Handler(WiFiEvent_t wifi_event, WiFiEventInfo_t wifi_info) {
  Serial.print("Local ESP32 IP: ");
  Serial.println(WiFi.localIP());
}

void WiFi_Disconnected_Handler(WiFiEvent_t wifi_event, WiFiEventInfo_t wifi_info) {
  Serial.println("Disconnected From WiFi Network");
  // Attempt Re-Connection
  WiFi.begin(ssid, password);
}

int getMoisture(int moisture_sensor_pin) {
  int sensorValue = analogRead(moisture_sensor_pin);
  int moisturePercentage = 100 - ((sensorValue - 1380) * 100 / (3560 - 1380));
  if (moisturePercentage < 0) {
    moisturePercentage = 0;
  }
  else if (moisturePercentage > 100){
    moisturePercentage = 100;
  }
  
  Serial.print("Soil Moisture Level: ");
  Serial.print(moisturePercentage);
  Serial.println("%");

  return moisturePercentage;
}

String getWaterTankState(int float_sensor_pin){
  int refillState = digitalRead(float_sensor_pin); // Reading float water sensor state
  String messageWater;
  if (refillState == 1) { // When input is HIGH -> bulb is lifted -> Water is sufficent
    messageWater="Sufficient water is available";
    Serial.println(messageWater);
  }
  else { // when input is LOW -> bulb is lowered -> Water supply needs a refill
    messageWater="Insufficient water supply, please refill";
    Serial.println(messageWater);
  }

  return messageWater;
}

void setup() {
  pinMode(water_sensor, INPUT_PULLUP); // setting the water sensor pin to input

  pinMode(water_pump_A, OUTPUT); // Setting the water pump pin to output
  digitalWrite(water_pump_A, LOW); // Set the water pump off to begin

  pinMode(flow_sensor_A, INPUT); // Define the water flow sensor pin as an input
  attachInterrupt(digitalPinToInterrupt(flow_sensor_A), flow_rate_ISR, RISING); // Attach ISR to flow sensor pin


  Serial.begin(115200);

  // setting the wifi to station mode and disconnecting in case it was previously connected
  //WiFi.mode(WIFI_STA); 
  WiFi.onEvent(ConnectedToAP_Handler, ARDUINO_EVENT_WIFI_STA_CONNECTED);
  WiFi.onEvent(GotIP_Handler, ARDUINO_EVENT_WIFI_STA_GOT_IP);
  WiFi.onEvent(WiFi_Disconnected_Handler, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);  
  WiFi.disconnect(); 

  WiFi.mode(WIFI_STA);
  //WiFi.softAP(esp32_ssid, esp32_password);

  //IPAddress IP = WiFi.softAPIP();
  //Serial.print("AP IP address: ");
  //Serial.println(IP);

  server.begin();
  
  // connect to wifi using network credentials from user
  WiFi.begin(ssid, password); 
  // *while loop is for testing ONLY
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(100);
  } 

  // Configure SSL client

  ssl_client.setInsecure();
  ssl_client.setConnectionTimeout(1000);
  ssl_client.setHandshakeTimeout(5);

  configTime(gmtOffset,daylightOffset,ntpServer);

  //Initialize Firebase
  initializeApp(aClient, app, getAuth(user_auth), processData, " authorisationTask");
  app.getApp<RealtimeDatabase>(Database);
  Database.url(DATABASE_URL);
}



void loop() {

  app.loop();
  if (app.ready()) {
    JsonDocument doc; 
    String jsonString = Database.get<String>(aClient, "/plants"); // Pulls Nodes under plants in Json object

    DeserializationError error = deserializeJson(doc, jsonString);
    
    if (error) { // handle error case
      Serial.println(error.c_str());
    }

    // if no plants in database, make sure that both hardware bundles are deallocated
    else if (doc.isNull()) {
      Serial.println("No plants found. De-allocating sensors if needed");
      bundle1.taken = false;
      bundle1.name = "";

      bundle2.taken = false;
      bundle2.name = "";
    }

    else {
      JsonObject plants = doc.as<JsonObject>();

      int plantcount = sizeof(plants);
      if (plantcount > 2) {
        Serial.println("More than 2 plants in the database. Only two plant slots available");
      }
      
      int loopCount = 0;

      for (JsonPair plant : plants) {
        if (loopCount >= 2) // Break the for loop if more than two plant objects in database
          break;
        
        // Store the name of the plant in String for future reference
        String name = plant.key().c_str();
      
        // Parse the attributes of the plant in data
        JsonObject data = plant.value().as<JsonObject>();
        Serial.println(name);

        // Conditional clause handling new plant that isn't registered in hardware
        if (data["taken"] == 0) {

          if (bundle1.taken && bundle2.taken) {
            Serial.println("Both hardware slots taken");
          }

          // First condition checks if bundle1 isn't taken and then checks if the plant is not already registered in bundle2
          else if (!bundle1.taken && name != bundle2.name) { 

            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + name + "/taken", 1, processData, "RTDB_Assigning " + name + " to bundle1");

          }

          else if (!bundle2.taken && name != bundle1.name) {
            
            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + name + "/taken", 1, processData, "RTDB_Assigning " + name + " to bundle1");
          }
        }

        // Set the moisture threshold to the appropriate plant
        if (name == bundle1.name){
          bundle1.moisture_threshold = data["threshold"];
        }
        else if (name == bundle2.name){
          bundle2.moisture_threshold = data["threshold"];
        }

      }
    }    
  }

  struct tm timeInfo;
  if (!getLocalTime(&timeInfo)){
    Serial.println("Failed to procure time");
  }


  int moisturePercentage = getMoisture(moisture_sensor_A);

  char timeWateredChar[30];
  String timeWatered;
  String messageWater = getWaterTankState(water_sensor);


  strftime(timeWateredChar,sizeof(timeWateredChar), "%A, %B %d %H:%M:%S", &timeInfo);

  timeWatered=String(timeWateredChar);


  app.loop();
  // Check if authentication is ready
  if (app.ready()) {
    // Periodic data sending every minute
    unsigned long currentTime = millis();
    if (currentTime - lastSendTime >= sendInterval){
      // Update the last send time
      lastSendTime = currentTime;

      Database.set<int>(aClient,"/plants/plant1/water_pump_A_state", 0, processData, "RTDB_Send_WaterPump_State"); // FOR TESTING

      // Send moisture level
      Database.set<int>(aClient, "/plants/plant1/moisture_level", moisturePercentage, processData, "RTDB_Send_MoistureLevel");

      // Send water tank state
      Database.set<String>(aClient, "/plants/plant1/water_level", messageWater, processData, "RTDB_Send_WaterLevel");

      // Send last time watered
      Database.set<String>(aClient, "/plants/plant1/last_time", timeWatered, processData, "RTDB_Send_Time");

    }
  }
  else {
    Serial.println("Database is not ready to receive data from hardware");
  }

  if (app.ready()) {
    app.loop();
    String event = Database.get<String>(aClient,"/plants/plant1/latest_watering_status/event");
    bool is_completed = Database.get<bool>(aClient, "/plants/plant1/latest_watering_status/is_completed");
    bool watering_mode = Database.get<bool>(aClient, "/plants/plant1/auto_watering_mode");
    String needs_water = Database.get<String>(aClient, "/plants/plant1/messageESP");
    int threshold = Database.get<int>(aClient, "/plants/plant1/threshold");
    if (threshold != 0) bundle1.moisture_threshold = threshold;
    Serial.print("Moisture threshold: ");
    Serial.println(bundle1.moisture_threshold);


    if (event == "Manual watering requested" && !is_completed){
      Serial.println("MANUAL WATERING REQUEST ACKNOWLEDGED. TRIGGERING WATER PUMP...");
      int water_duration = Database.get<int>(aClient, "/plants/plant1/latest_watering_status/duration");

      digitalWrite(water_pump_A, HIGH);
      delay(water_duration*100);
      digitalWrite(water_pump_A, LOW);

      app.loop();
      Database.set<bool>(aClient, "/plants/plant1/latest_watering_status/is_completed", true, processData, "RTDB_ManualWateringLog_Update");
      Database.set<String>(aClient, "/plants/plant1/watering_log/time", timeWatered, processData,"RTDB_Send_wateringTime");
      Database.set<String>(aClient, "/plants1/plant1/watering_log/type", "Manual", processData);
      Serial.println("MANUAL WATERING ACCOMPLISHED");
    }
    else if (watering_mode && needs_water == "NEEDS WATER") {
      Serial.println("TRIGGERING AUTO-WATERING MODE...");

      digitalWrite(water_pump_A, HIGH);
      delay(3000);
      digitalWrite(water_pump_A, LOW);
    
      app.loop();
      Database.set<String>(aClient, "/plants/plant1/watering_log/time", timeWatered, processData,"RTDB_Send_wateringTime");
      Database.set<String>(aClient, "/plants1/plant1/watering_log/type", "Automatic", processData);
      Serial.println("AUTO-WATERING FINISHED");
    }
  }


  
  // Runs in the condition that the water pump is set to turn ONLY if set in the current loop iteration
  /**if (current_pump_state == 1 && previous_pump_state == 0) {

    // Turn the water pump on for a set duration
    digitalWrite(water_pump_A, HIGH);
    delay(automatic_pump_time);
    digitalWrite(water_pump_A, LOW);
    total_flow = (pulse_count / pulses_per_liter); // Gives the amount of water dispensed in L

    Serial.print("Water dispensed: ");
    Serial.print(total_flow);
    Serial.println(" L");

    flow_rate = total_flow * 20; // Converts the dispensed water amount from L to L/sec

    Serial.print("Flow rate: ");
    Serial.print(flow_rate);
    Serial.println(" L/min");

    pulse_count = 0; // Reset the pulse counter of the flow sensor
    unsigned long start = millis();

    // Make sure to wait until database is free to handle requests, as the next two requests are critical and cannot be skipped until next loop iteration
    upload_complete = false;
    while(!app.ready()){} 
  
    Database.set<double>(aClient, "/plants/plant1/dispensed_water", total_flow, processData, "RTDB_Send_DispensedWater_Volume");
    while (!upload_complete && millis() - start < 20000){
      app.loop();
      delay(1);
    }

    start = millis();
    upload_complete = false;
    while(!app.ready()){ delay(1000);} 

    Database.set<double>(aClient, "/plants/plant1/dispensed_flow_rate", flow_rate, processData, "RTDB_Send_Dispensed_Flow_Rate");
    while (!upload_complete && millis() - start < 20000){
      app.loop();
      delay(1);
    }

  }**/

  // Update the previous water pump state for next loop iteration
  previous_pump_state = current_pump_state; 
  pulse_count = 0;

  delay(5000); // Delay for two seconds before the next reading
}

void processData(AsyncResult &result) {
  Serial.println("Callback enterd");
  if (!result.isResult()) {
    Serial.println("Authentication failed");
    return;
  }  

  if (result.isError()) {
    Firebase.printf("Upload failed: %s\n", result.error().message().c_str());
    if (result.uid() == "RTDB_Send_DispensedWater_Volume" || result.uid() == "RTDB_Send_Dispensed_Flow_Rate") {
      upload_complete = true;
    }
    return;
  }  

  if (result.available()) {
    Firebase.printf("Upload successful: %s\n", result.uid().c_str());
    // Water flow and volume data case interrupt CPU and must be have their condition variable flipped when completed
    if (result.uid() == "RTDB_Send_DispensedWater_Volume" || result.uid() == "RTDB_Send_Dispensed_Flow_Rate") {
      upload_complete = true;
    }
  }
    
}

/**void processCommand(AsyncResult &result) {
  if (!result.isResult())
    return;

  if (result.isError()) {
    Serial.println(result.error().message());
    return;
  }

  if (result.available()) {
    RealtimeDatabaseResult &command = result.to<RealtimeDatabaseResult>();

    Serial.println(command.event());
    Serial.println(command.dataPath());
    Serial.println(command.to<const char *>());

    JsonDocument doc;
    deserializeJson(doc, command.to<const char *>());
    String event = doc["event"];
    if (event == "Manual watering requested") {
      if (!watering_requested) {
        manual_pump_time = doc["duration"];
        watering_requested = true;
      }
    }
  }
} **/
