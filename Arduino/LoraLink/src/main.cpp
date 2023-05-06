#include <Arduino.h>
#include <SPI.h> 
#include <LoRa.h>
#include <BluetoothSerial.h>
#include "DHT20.h"
#include <Wire.h>
#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

/* Constants */
#define BAUD_RATE 9600
#define PROGRAM_DELAY 3*SECOND
#define SECOND 1000
#define RIGHT_BUTTON 0
#define LEFT_BUTTON 35
#define DHT20_OK_MAX 28
#define SENSOR_ERR "NULL?"
#define FROM_ESP32 "From ESP32: "
// LoRa
#define SCK 13
#define MISO 22
#define MOSI 21
#define SS 15
#define RST 2
#define DI0 17
#define LORA_CONNECTED "LoRa Connected..."
#define LORA_ERR "LoRa Failed...Check connections"
// Insert Firebase project API Key
#define API_KEY "ENTER_API_KEY"
// Insert Authorized Username and Corresponding Password
#define USER_EMAIL "user@lora.com"
#define USER_PASSWORD "loraaa"
// Insert RTDB URLefine the RTDB URL
#define DATABASE_URL "https://lora-link-default-rtdb.firebaseio.com/"

/* variable Constanst*/
const long frequency = 915E6;
const char* ssid = "ENTER_WIFI_SSID";
const char* pass = "ENTER_WIFI_PASSWORD";

/* variables */
String temp;
String humidity;
String lastMessageSentViaBluetooth = "";
String sendPacketBack = "";
String tempString = "";
// Variables to save database paths
String messagePath = "/message";
String tempPath = "/temp";
String humidityPath = "/humidity";

/* objects */
BluetoothSerial SerialBT;
DHT20 DHT;
// Define Firebase objects
FirebaseData stream;
FirebaseAuth auth;
FirebaseConfig config;
FirebaseData fbdo;
FirebaseJson json;

/* methods */
void recieveBluetooth();
void sendPacket(String);
String readTempSensor();
void sendVialBluetooth(String);
void pushToFirebase(String);
String getTemp();
String getHumidity();

// ##############################################
//                    ESP32
// ##############################################

void IRAM_ATTR onReceive(int packetSize) {
  Serial.println("Packet received");
  String packet = "";
  if (packetSize) {
    if(LoRa.available()) {
      packet = LoRa.readString();
      Serial.print("Received packet -> ");
      Serial.println(packet);
      sendPacketBack = packet;
    }
  }
}

// Initialize WiFi
void initWiFi() {
  WiFi.begin(ssid, pass);
  Serial.print("Connecting to WiFi ..");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print('.');
    delay(1000);
  }
  Serial.println(WiFi.localIP());
  Serial.println();
}

void setup() {
  // initialize serial
  Serial.begin(BAUD_RATE);

  initWiFi();

  Wire.begin(25, 33); // SDA, SCL

  // init LoRa
  SPI.begin(SCK, MISO, MOSI, SS);
  LoRa.setPins(SS, RST, DI0);

  if (!LoRa.begin(915E6)) {
    Serial.println(LORA_ERR);
    while(true);
  }

  Serial.println(LORA_CONNECTED);

  SerialBT.begin("ESP32 LoRa"); // Set Bluetooth name
  Serial.println("Bluetooth started.");
  
  // configure on board buttons
  pinMode(RIGHT_BUTTON, INPUT_PULLUP);
  pinMode(LEFT_BUTTON, INPUT_PULLUP);

  // register the receive callback
  LoRa.onReceive(onReceive);
  // put the radio into receive mode
  LoRa.receive();

  // begin sensor readings
  DHT.begin();

  // Set up Firebase
  config.api_key = API_KEY;
  auth.user.email = USER_EMAIL;
  auth.user.password = USER_PASSWORD;
  config.database_url = DATABASE_URL;
  Firebase.reconnectWiFi(true);
  config.token_status_callback = tokenStatusCallback; 
  config.max_token_generation_retry = 5;
  Firebase.begin(&config, &auth);
}


void loop() {
  recieveBluetooth();

  if(strcmp(sendPacketBack.c_str(), "!temp") == 0 || strcmp(sendPacketBack.c_str(), "!temp plz") == 0){
    delay(SECOND * 2);
    sendPacket(readTempSensor());
    sendPacketBack = "";
  }
  else if(sendPacketBack.length() > 0){
    // send to Android device via bluetooth
    sendVialBluetooth(sendPacketBack);
    // upload to firebase
    pushToFirebase(sendPacketBack);
    delay(1500);
    ESP.restart();
  }
}

void recieveBluetooth() {
  // recieve bluetooth data
  if (SerialBT.available()) {
    String messageReceived = SerialBT.readString();
    Serial.println("Received message: ");
    Serial.println(messageReceived);

    if(strcmp(messageReceived.c_str(), "!temp plz") == 0 || strcmp(messageReceived.c_str(), "!temp") == 0) {
      sendVialBluetooth(readTempSensor());
      delay(1000);
      return;
    }
    else if(strcmp(messageReceived.c_str(), "!restart") == 0){
      sendVialBluetooth("Restarting...Plz reconnect");
      delay(1000);
      ESP.restart();
    }
    else if(strstr(messageReceived.c_str(), lastMessageSentViaBluetooth.c_str())){
      Serial.println("Message that was sent via bluetooth was received and read");
    }
    sendPacket(messageReceived);
  }
}

void sendPacket(String message){
  Serial.println("Sending packet: ");
  Serial.println(message);
  LoRa.beginPacket();
  LoRa.println(message);
  LoRa.endPacket(true);
  delay(SECOND * 2);
}

void sendVialBluetooth(String message){
  SerialBT.println(message);
  Serial.print("Sending message via Bluetooth: ");
  Serial.println(message);
  lastMessageSentViaBluetooth = message;
}

String readTempSensor(){
  // read temperature & humidty sensor data
  // following two lines are used instead of just DHT.read() so
  // that no connection (unplugged sensor) can be detected easily
  DHT.requestData(); delay(100);
  DHT.readData(); DHT.convert();
  int status = DHT.readStatus();

  // update temp and humidity variables
  // check if sensor is working
  if (status >= DHT20_OK && status <= DHT20_OK_MAX){
    // convert temp from C to F
    temp = String((DHT.getTemperature() * 9/5) + 32);
    humidity = String(DHT.getHumidity());
  }
  else {
    temp = humidity = SENSOR_ERR;
  }

  tempString = "Temp: ";
  tempString += temp;
  tempString += "°F\nHumidity: ";
  tempString += humidity; 
  tempString += "%"; 

  return tempString;
}

String getHumidity(){
  // read temperature & humidty sensor data
  // following two lines are used instead of just DHT.read() so
  // that no connection (unplugged sensor) can be detected easily
  DHT.requestData(); delay(100);
  DHT.readData(); DHT.convert();
  int status = DHT.readStatus();

  if (status >= DHT20_OK && status <= DHT20_OK_MAX)
    humidity = String(DHT.getHumidity());

  return humidity;
}

String getTemp(){
  // read temperature & humidty sensor data
  // following two lines are used instead of just DHT.read() so
  // that no connection (unplugged sensor) can be detected easily
  DHT.requestData(); delay(100);
  DHT.readData(); DHT.convert();
  int status = DHT.readStatus();

  if (status >= DHT20_OK && status <= DHT20_OK_MAX)
    temp = String((DHT.getTemperature() * 9/5) + 32);

  return temp;
}

void pushToFirebase(String message){
  // Create a JSON object with "message", "temp", and "humidity" fields
  json.set(messagePath.c_str(), message);
  json.set(tempPath.c_str(), getTemp());
  json.set(humidityPath.c_str(), getHumidity());

  // Push the JSON object to Firebase
  Firebase.RTDB.pushJSON(&fbdo, "/data", &json);

  // Check if the data was successfully pushed
  if (fbdo.httpCode() == 200) {
    Serial.println("Data was successfully pushed to Firebase!");
  } else {
    Serial.println("Error pushing data to Firebase");
    Serial.println(fbdo.errorReason());
  }
}