#include <Servo.h>

const int sensorPin = A0;
Servo myservo;

// Variables PID y sistema
double Kp = 7.12; 
double Ki = 0.016; 
double Kd = 25; 
double setpoint = 26; // Setpoint inicial

const unsigned long interval = 15; // Intervalo en ms
unsigned long previous_time = 0;

double input, output, last_error = 0, accumulated_error = 0;
const double min_output = 10, max_output = 180;

void setup() {
  Serial.begin(9600);  
  myservo.attach(9);  
  myservo.write(95); 
  pinMode(sensorPin, INPUT);  
}

void loop() {
  // Leer comandos desde el puerto serie
  if (Serial.available() > 0) {
    String inputString = Serial.readStringUntil('#'); // Leer hasta el delimitador '#'
    
    // Separar los valores por ':' y procesar cada uno
    if (inputString.length() > 0) {
      String kpStr = getValue(inputString, "Kp:");
      if (kpStr != "") Kp = kpStr.toDouble();
      
      String kiStr = getValue(inputString, "Ki:");
      if (kiStr != "") Ki = kiStr.toDouble();
      
      String kdStr = getValue(inputString, "Kd:");
      if (kdStr != "") Kd = kdStr.toDouble();
      
      String setpointStr = getValue(inputString, "Setpoint:");
      if (setpointStr != "") setpoint = setpointStr.toDouble();
      
      // Imprimir valores recibidos para verificar
      Serial.print("Kp: "); Serial.println(Kp);
      Serial.print("Ki: "); Serial.println(Ki);
      Serial.print("Kd: "); Serial.println(Kd);
      Serial.print("Setpoint: "); Serial.println(setpoint);
    }
  }

  // Control PID
  unsigned long current_time = millis();
  if (current_time - previous_time >= interval) {
    previous_time = current_time;
    input = get_dist(100); // Leer distancia
    Serial.println(input);
    double error = setpoint - input;
    double P = Kp * error;
    double I = Ki * accumulated_error;
    double D = Kd * (error - last_error);
    accumulated_error += error;
    last_error = error;

    double antiwindup = P + I + D;
    output = constrain(map(antiwindup, -160, 160, min_output, max_output), min_output, max_output);
    myservo.write(output);
  }
}

// Función para extraer valores de la cadena
String getValue(String str, String key) {
  int startIndex = str.indexOf(key);
  if (startIndex != -1) {
    startIndex += key.length(); // Mover después de "Kp:", "Ki:", etc.
    int endIndex = str.indexOf(":", startIndex);
    if (endIndex == -1) {
      endIndex = str.length(); // Si no hay más ":", tomar el final de la cadena
    }
    return str.substring(startIndex, endIndex);
  }
  return ""; // Si no se encuentra el valor, devolver vacío
}

float get_dist(int n) {
  long sum = 0;
  for (int i = 0; i < n; i++) {
    sum += analogRead(sensorPin);
  }
  float adc = sum / n;
  float distance_cm = 17569.7 * pow(adc, -1.2062);
  return distance_cm;
}
