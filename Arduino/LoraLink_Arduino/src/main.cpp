#include <Arduino.h>
#include <LoRa.h>
#include <avr/wdt.h>
#include <SoftwareSerial.h>
#include <Wire.h>
#include <SPI.h>

#define SCK 13
#define MISO 12
#define MOSI 11
#define SS 10
#define RST 9
#define DI0 2

/* methods */
void sendPacket(String message);
void resetLoRa();
void readBluetooth();

/* objects */
SoftwareSerial BTSerial(5, 4); 

/* variables */
String packet = "";
char c;
bool packetReceived = false;

// ##############################################
//                    Arduino
// ##############################################

void onReceive(int packetSize) {
  // try to parse packet
  if (packetSize) {
    // read packet
    if(LoRa.available())
      packet = LoRa.readString();

    Serial.println("Received packet -> " + packet);
    Serial.println("\n");
    packetReceived = true;
    
    resetLoRa();
  }
}

void setup() {
  Serial.begin(9600);
  while (!Serial);

  // init LoRa
  LoRa.setPins(SS, RST, DI0);

  Serial.print("\nArduino LoRa Sender/Receiver");

  if (!LoRa.begin(915E6)) {
    Serial.println("Starting LoRa failed!");
    while(true);
  }
  else{
    Serial.print(" -- LoRa Started...\n");
  }

  BTSerial.begin(9600);

  LoRa.onReceive(onReceive);
  LoRa.receive();
}

void loop() {
  if(packet.length() > 0 && packetReceived){
    // send packet to bluetooth
    BTSerial.println(packet);
    Serial.println("Packet sent to bluetooth! -> " + packet);
    delay(1000);
    packet = "";
    packetReceived = false;
  }

  readBluetooth();
}

void readBluetooth(){
  if (BTSerial.available()) {
    String receivedString = BTSerial.readString();
    Serial.println("Received String: " + receivedString);
    // check if recieved String is not empty before sending
    if(receivedString.length() > 0){
      sendPacket(receivedString);
      delay(500);
      Serial.println("Message sent!");
      //resetLoRa();
    }
  }
}

void sendPacket(String message){
  Serial.println("Sending packet: ");
  Serial.println(message);
  LoRa.beginPacket();
  LoRa.println(message);
  LoRa.endPacket(true);
  delay(2000);
}

void resetLoRa(){
  Serial.println("Resetting LoRa...\n");
  delay(100);
  wdt_enable(WDTO_2S);
  while (true) {}
}