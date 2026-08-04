
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

#define moisture_sensor_B 35
#define water_pump_B 21
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
  int moisture_sens; // Moisture sensor pin number
  int pump; // Water pump pin number
  int flow_sens; // Flow rate sensor pin number
  int moisture_threshold; // Moisture threshold set by the app, originally initialized to 40
  bool taken; // State of the hardware package (Is there a plant there)
  String name; // Name of the plant. Set to NULL if there taken = 0
  bool manual_watering;
  int manual_duration;
  volatile bool manual_watering_serviced;
};

struct plantStatus {
  int moisture;
  String lastTime;
  String waterLevel;
};

// Initializing plant bundle structures
hardwarePackage bundle1 = {moisture_sensor_A, water_pump_A, flow_sensor_A, 40, false, "", false, 0, true};
hardwarePackage bundle2 = {moisture_sensor_B, water_pump_B, flow_sensor_B, 20, false, "", false, 0, true};

// Intialization of time tracking variables
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 20000; // one minute interval, reduce for testing

const int automatic_pump_time = 1;
const unsigned long pump_cooldown = 60000;
unsigned long last_pump_time = 0;

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
volatile bool manual_watering_done = true;

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

String getWaterTankState(int float_sensor_pin) {
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

void activatePump(int pump_pin, int duration) {
  Serial.println("Activating pump: ");
  digitalWrite(pump_pin, HIGH);
  delay(duration*1000);
  digitalWrite(pump_pin, LOW);
  Serial.println("Closing pump");
}

void checkForWatering(hardwarePackage *bundle, int moisturePercentage) {

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
}

void setup() {
  pinMode(water_sensor, INPUT_PULLUP); // setting the water sensor pin to input

  pinMode(water_pump_A, OUTPUT); // Setting the water pump pin to output
  digitalWrite(water_pump_A, LOW); // Set the water pump off to begin
  pinMode(water_pump_B, OUTPUT);
  digitalWrite(water_pump_B, LOW);

  //pinMode(flow_sensor_A, INPUT); // Define the water flow sensor pin as an input
  //attachInterrupt(digitalPinToInterrupt(flow_sensor_A), flow_rate_ISR, RISING); // Attach ISR to flow sensor pin


  Serial.begin(115200);

  // setting the wifi to station mode and disconnecting in case it was previously connected
  //WiFi.mode(WIFI_STA); 
  WiFi.onEvent(ConnectedToAP_Handler, ARDUINO_EVENT_WIFI_STA_CONNECTED);
  WiFi.onEvent(GotIP_Handler, ARDUINO_EVENT_WIFI_STA_GOT_IP);
  WiFi.onEvent(WiFi_Disconnected_Handler, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);  
  WiFi.disconnect(); 

  WiFi.mode(WIFI_STA);
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
}



void loop() {

  //int moisturePercentage_test = getMoisture(moisture_sensor_B);
  app.loop();
  if (app.ready()) {
    JsonDocument doc; 
    String jsonString = Database.get<String>(getClient, "/plants"); // Pulls Nodes under plants in Json object

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
            Serial.println("Both hardware slots taken. Cannot assign plant");
          }

          // First condition checks if bundle1 isn't taken and then checks if the plant is not already registered in bundle2
          else if (!bundle1.taken && name != bundle2.name) { 

            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + name + "/taken", 1, processData, "RTDB_Assigning_Plant");
            while (!upload_complete){
              app.loop();
              delay(10);
            }
            upload_complete = false;
            bundle1.taken = true;
            bundle1.name = name;

          }

          else if (!bundle2.taken && name != bundle1.name) {
            
            app.loop();
            // Set the taken state in the database to TRUE
            Database.set<bool>(aClient, "/plants/" + name + "/taken", 1, processData, "RTDB_Assigning_Plant");
            while (!upload_complete){
              app.loop();
              delay(10);
            }
            upload_complete = false;
            bundle2.taken = true;
            bundle2.name = name;
          }
        }

        // Set the moisture threshold to the appropriate plant
        if (name == bundle1.name){
          bundle1.moisture_threshold = data["threshold"];
          if (data["auto_watering_mode"] != true) {
            bundle1.manual_watering = true;
            bundle1.manual_duration = data["manual_watering_duration"];
          }
        }
        else if (name == bundle2.name){
          bundle2.moisture_threshold = data["threshold"];
          if (data["auto_watering_mode"] != true) {
            bundle1.manual_watering = true;
            bundle1.manual_duration = data["manual_watering_duration"];
          }
        }

      }
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
  if (bundle1.taken) {
    moisturePercentage_A = getMoisture(bundle1.moisture_sens);
    checkForWatering(&bundle1, moisturePercentage_A);
    /**moisturePercentage_A = getMoisture(bundle1.moisture_sens);

    if (!bundle1.manual_watering && moisturePercentage_A <= bundle1.moisture_threshold) {
      Serial.println("Automatic watering incoming");
      activatePump(bundle1.pump, automatic_pump_time);
    }
    else if (!bundle1.manual_watering_serviced){
      bundle1.manual_watering = false;
      Serial.println("Still servicing previous request");

      app.loop();
      if (app.ready()) {
        Serial.println("Ready to send confirmation of manual watering");
        Database.set<bool>(aClient, "/plants/" + bundle1.name + "/auto_watering_mode", true, processData, "RTDB_Confirming_Manual_Watering_Bundle1");
      }
    }
    else if (bundle1.manual_watering) {
      Serial.println("Handling new manual request:");
      activatePump(bundle1.pump, bundle1.manual_duration);
      bundle1.manual_watering = false;
      bundle1.manual_watering_serviced = false;
    } **/

  }  
  int moisturePercentage_B;
  if (bundle2.taken) {
    moisturePercentage_B = getMoisture(bundle2.moisture_sens);
    checkForWatering(&bundle2, moisturePercentage_B);
  }

  String messageWater = getWaterTankState(water_sensor);


  app.loop();
  // Check if authentication is ready
  if (app.ready()) {
    // Periodic data sending every minute
    unsigned long currentTime = millis();
    if (currentTime - lastSendTime >= sendInterval){
      // Update the last send time
      lastSendTime = currentTime;

      /**plantStatus status = {moisturePercentage_A, timeWatered, messageWater};

      JsonDocument data;
      data["moisture_level"] = status.moisture;
      data["last_time"] = status.lastTime;
      data["water_level"] = status.waterLevel;

      String json;
      serializeJson(data, json); **/
      // Send moisture level
      if (bundle1.taken) {
        Database.set<int>(aClient, "/plants/" + bundle1.name + "/moisture_level", moisturePercentage_A, processData, "RTDB_Send_MoistureLevel");

        // Send water tank state
        Database.set<String>(aClient, "/plants/" + bundle1.name + "/water_level", messageWater, processData, "RTDB_Send_WaterLevel");

        // Send last time watered
        Serial.println(timeWatered);
        Database.set<String>(a2Client, "/plants/" + bundle1.name + "/last_time", timeWatered, processData, "RTDB_Send_Time");
      }
      if (bundle2.taken) {
        Database.set<int>(aClient, "/plants/" + bundle2.name + "/moisture_level", moisturePercentage_B, processData, "RTDB_Send_MoistureLevel");

        // Send water tank state
        //Database.set<String>(aClient, "/plants/" + bundle2.name + "/water_level", messageWater, processData, "RTDB_Send_WaterLevel");

        // Send last time watered
        Serial.println(timeWatered);
        Database.set<String>(a2Client, "/plants/" + bundle2.name + "/last_time", timeWatered, processData, "RTDB_Send_Time");
      }
    }

    
  }
  else {
    Serial.println("Database is not ready to receive data from hardware");
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
    if (result.uid() == "RTDB_Send_DispensedWater_Volume" || result.uid() == "RTDB_Send_Dispensed_Flow_Rate" || result.uid() == "RTDB_Assigning_Plant") {
      upload_complete = true;
    }
    if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle1") {
      bundle1.manual_watering_serviced = true;
    }
    return;
  }  

  if (result.available()) {
    Firebase.printf("Upload successful: %s\n", result.uid().c_str());
    // Water flow and volume data case interrupt CPU and must be have their condition variable flipped when completed
    if (result.uid() == "RTDB_Send_DispensedWater_Volume" || result.uid() == "RTDB_Send_Dispensed_Flow_Rate" || result.uid() == "RTDB_Assigning_Plant") {
      upload_complete = true;
    }
    if (result.uid() == "RTDB_Confirming_Manual_Watering_Bundle1") {
      bundle1.manual_watering_serviced = true;
    }
  }
    
}

