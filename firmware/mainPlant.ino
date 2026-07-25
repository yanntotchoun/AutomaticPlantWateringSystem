#define ENABLE_USER_AUTH
#define ENABLE_DATABASE

#include <Arduino.h>
#include <WiFi.h>
#include <WiFiClientSecure.h> 
#include <FirebaseClient.h>
#include "time.h"


// Firebase credentials
#define DATABASE_URL "https://plantwateringdatabase-default-rtdb.firebaseio.com/"
#define WEB_API_KEY "AIzaSyD6n9M0yJhekVh1fvyfnA5ZaER9Ic0WUg0"
#define USER_EMAIL "duongbrian80@gmail.com"
#define USER_PASSWORD "Asdfgh"

#define water_sensor 13
#define moisture_sensor 36
#define water_pump 22
#define flow_sensor 15

const long gmtOffset=-18000; //-5 hours offset for EST from GMT, from seconds
const int daylightOffset=3600;


// input network credentials
const char* ssid = "VIDEOTRON6107";
const char* password = "XTAC74VFJ349V";
const char* ntpServer="pool.ntp.org";

// esp32 network credentials for initial connection with app
const char* esp32_ssid = "ESP32access";
const char* esp32_password = "0987654321";

// Set web server port number to 80
WiFiServer server(80);

// Variable to store the HTTP request
String header;

void processData(AsyncResult &result);
UserAuth user_auth(WEB_API_KEY, USER_EMAIL, USER_PASSWORD);


FirebaseApp app;
WiFiClientSecure ssl_client;
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
RealtimeDatabase Database;

// Intialization of time tracking variables
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 60000; // one minute interval, reduce for testing

// Variables to ensure consistent operation of the water pump
int current_pump_state = 1; // FOR TESTING. BRING BACK TO ZERO ONCE TESTING IS DONE
int previous_pump_state = 0;
int pumping_time = 3000;

// Variables to track water flow through the pump
double flow_rate = 0.0;
double total_flow = 0.0;
volatile int pulse_count = 0;
const double pulses_per_liter = 5880.0;

// Interrupt Service Routine used for flow rate measurement when the water pump is active
void IRAM_ATTR flow_rate_ISR() {
  pulse_count++;
}

// State variable for the status of database upload requests
volatile bool upload_complete = true;

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



void setup() {
  pinMode(water_sensor, INPUT_PULLUP); // setting the water sensor pin to input

  pinMode(water_pump, OUTPUT); // Setting the water pump pin to output
  digitalWrite(water_pump, LOW); // Set the water pump off to begin

  pinMode(flow_sensor, INPUT); // Define the water flow sensor pin as an input
  attachInterrupt(digitalPinToInterrupt(flow_sensor), flow_rate_ISR, RISING); // Attach ISR to flow sensor pin


  Serial.begin(115200);

  // setting the wifi to station mode and disconnecting in case it was previously connected
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

  struct tm timeInfo;
  if (!getLocalTime(&timeInfo)){
    Serial.println("Failed to procure time");
  }

  int sensorValue = analogRead(moisture_sensor); // reading moisture sensor value from 1380 (pure water) to 3560 (air)
  int moisturePercentage = 100 - ((sensorValue - 1380) * 100 / (3560 - 1380));
  if (moisturePercentage < 0) {
    moisturePercentage = 0;
  }
  else if (moisturePercentage > 100){
    moisturePercentage = 100;
  }

  String messageWater;
  char timeWateredChar[30];
  String timeWatered;
  Serial.print("Soil Moisture Level: ");
  Serial.print(moisturePercentage);
  Serial.println("%");

  int refillState = digitalRead(water_sensor); // Reading float water sensor state
  if (refillState == 1) { // When input is HIGH -> bulb is lifted -> Water is sufficent
    messageWater="Sufficient water is available";
    Serial.println(messageWater);
  }
  else { // when input is LOW -> bulb is lowered -> Water supply needs a refill
    messageWater="Insufficient water supply, please refill";
    Serial.println(messageWater);
  }

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
      Serial.println("Current time:");
      Serial.println(currentTime);

      // Send moisture level
      Database.set<int>(aClient, "/plant1/moisture_level", moisturePercentage, processData, "RTDB_Send_MoistureLevel");

      // Send water tank state
      Database.set<String>(aClient, "/plant1/water_level", messageWater, processData, "RTDB_Send_WaterLevel");


      // Send last time watered
      Database.set<String>(aClient, "/plant1/last_time", timeWatered, processData, "RTDB_Send_Time");

      // Receive the required state of the water pump from the database
      current_pump_state = Database.get<int>(aClient, "/plant1/water_pump_state");

    }
  }
  
  // Runs in the condition that the water pump is set to turn ONLY if set in the current loop iteration
  if (current_pump_state == 1 && previous_pump_state == 0) {

    // Turn the water pump on for a set duration
    digitalWrite(water_pump, HIGH);
    delay(pumping_time);
    digitalWrite(water_pump, LOW);

    // --- THE FIX: Tell Firebase to stop the pump (Reset the switch) ---
    Database.set<int>(aClient, "/plant1/water_pump_state", 0, processData, "Stop_Pump_Logic");

    // Update the single status check flag for the App UI
    Database.set<bool>(aClient, "/plant1/latest_watering_status/is_completed", true, nullptr);

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
  
    Database.set<double>(aClient, "/plant1/dispensed_water", total_flow, processData, "RTDB_Send_DispensedWater_Volume");
    while (!upload_complete && millis() - start < 20000){
      app.loop();
      delay(1);
    }

    start = millis();
    upload_complete = false;
    while(!app.ready()){} 

    Database.set<double>(aClient, "/plant1/dispensed_flow_rate", flow_rate, processData, "RTDB_Send_Dispensed_Flow_Rate");
    while (!upload_complete && millis() - start < 20000){
      app.loop();
      delay(1);
    }

  }

  // Update the previous water pump state for next loop iteration
  previous_pump_state = current_pump_state; 
  pulse_count = 0;

  delay(2000); // Delay for two seconds before the next reading
}

void processData(AsyncResult &result) {
  if (!result.isResult())
    return;

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
