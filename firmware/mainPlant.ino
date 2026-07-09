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

const long gmtOffset=-18000; //-5 hours offset for EST from GMT, from seconds
const int daylightOffset=3600;


// input network credentials
const char* ssid = "VIDEOTRON6107"; 
const char* password = "XTAC74VFJ349V";
const char* ntpServer="pool.ntp.org";

void processData(AsyncResult &result);
UserAuth user_auth(WEB_API_KEY, USER_EMAIL, USER_PASSWORD);

//const int dry = 3560; // soil moisture sensor reading in pure water
//const int wet = 1380; // soil moisture sensor reading in air

FirebaseApp app;
WiFiClientSecure ssl_client;
using AsyncClient = AsyncClientClass;
AsyncClient aClient(ssl_client);
RealtimeDatabase Database;

// Intialization of time tracking variables
unsigned long lastSendTime = 0;
const unsigned long sendInterval = 60000; // one minute interval, reduce for testing

void processData(AsyncResult &result);

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
  Serial.begin(9600);

  // setting the wifi to station mode and disconnecting in case it was previously connected
  WiFi.mode(WIFI_STA); 
  WiFi.onEvent(ConnectedToAP_Handler, ARDUINO_EVENT_WIFI_STA_CONNECTED);
  WiFi.onEvent(GotIP_Handler, ARDUINO_EVENT_WIFI_STA_GOT_IP);
  WiFi.onEvent(WiFi_Disconnected_Handler, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);  
  WiFi.disconnect(); 
  
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
  int moisturePercentage = (sensorValue - 1380) * 100 / (3560 - 1380);
  String messageWater;
  char timeWateredChar[30];
  String timeWatered;
  Serial.print("Soil Moisture Level: ");
  Serial.print(moisturePercentage);
  Serial.println("%");

  int refillState = digitalRead(water_sensor); // Reading float water sensor state
  if (refillState == 1){ // When input is HIGH -> bulb is lifted -> Water is sufficent
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

    }
  }

  
  delay(2000); // Delay for two seconds before the next reading
}

void processData(AsyncResult &result) {
  if (!result.isResult()) 
  return;

  if (result.isError()) {
    Firebase.printf("Upload failed: %s\n", result.error().message().c_str());
    return;
  }  

  if (result.available())
    Firebase.printf("Upload successful: %s\n", result.uid().c_str());

}
