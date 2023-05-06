#include <Arduino.h>
#include <SPI.h> 
#include <LoRa.h>
#include <BluetoothSerial.h>
#include "DHT20.h"
#include <Wire.h>

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

/* variable Constanst*/
const long frequency = 915E6;

/* variables */
String temp;
String humidity;
String lastMessageSentViaBluetooth = "";
String sendPacketBack = "";
String tempString = "";

/* objects */
BluetoothSerial SerialBT;
DHT20 DHT;

/* methods */
void recieveBluetooth();
void sendPacket(String);
String readTempSensor();
void sendVialBluetooth(String);

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

void setup() {
  // initialize serial
  Serial.begin(BAUD_RATE);

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
}


void loop() {
  recieveBluetooth();

  if(strcmp(sendPacketBack.c_str(), "!temp") == 0 || strcmp(sendPacketBack.c_str(), "!temp plz") == 0){
    delay(SECOND * 2);
    sendPacket(readTempSensor());
    sendPacketBack = "";
  }
  else if(sendPacketBack.length() > 0){
    sendVialBluetooth(sendPacketBack);
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