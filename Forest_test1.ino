#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include "esp_camera.h"
#include "time.h"
#include <TinyGPSPlus.h>
#include "soc/rtc_cntl_reg.h"
#include "soc/soc.h"

// ---------------------- CONFIG ----------------------
#define PIR_PIN 13
#define LED_PIN 4 // Flash LED (used for flash + feedback)

const char* WIFI_SSID = "vivo Y02t";
const char* WIFI_PASSWORD = "edith123";

// Firebase credentials
#define API_KEY "AIzaSyALbxkNP1Z7Y_y3HCEGh8-m36A1ttAiHV0"
#define DATABASE_URL "https://foresteye-699bb-default-rtdb.asia-southeast1.firebasedatabase.app/"
#define STORAGE_BUCKET "foresteye-699bb.firebasestorage.app"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

// ---------------------- CAMERA PINS (AI-THINKER) ----------------------
#define PWDN_GPIO_NUM 32
#define RESET_GPIO_NUM -1
#define XCLK_GPIO_NUM 0
#define SIOD_GPIO_NUM 26
#define SIOC_GPIO_NUM 27
#define Y9_GPIO_NUM 35
#define Y8_GPIO_NUM 34
#define Y7_GPIO_NUM 39
#define Y6_GPIO_NUM 36
#define Y5_GPIO_NUM 21
#define Y4_GPIO_NUM 19
#define Y3_GPIO_NUM 18
#define Y2_GPIO_NUM 5
#define VSYNC_GPIO_NUM 25
#define HREF_GPIO_NUM 23
#define PCLK_GPIO_NUM 22

// ---------------------- GPS ----------------------
#define GPS_RX 15
#define GPS_TX 14
HardwareSerial gpsSerial(1);
TinyGPSPlus gps;
String latitude = "", longitude = "";
bool gpsReady = false;     // true once we have either real or default coords

// ---------------------- LED FEEDBACK ----------------------
void blinkLED(int count, int delayMs = 200) {
  for (int i = 0; i < count; i++) {
    digitalWrite(LED_PIN, HIGH);
    delay(delayMs);
    digitalWrite(LED_PIN, LOW);
    delay(delayMs);
  }
}

// ---------------------- ERROR LOGGER ----------------------
void logError(String type, String message) {
  FirebaseJson errorLog;
  errorLog.set("type", type);
  errorLog.set("message", message);
  errorLog.set("timestamp", String(time(nullptr)));
  Firebase.RTDB.pushJSON(&fbdo, "/logs/errors", &errorLog);
}

// ---------------------- CAMERA INIT ----------------------
void startCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;
  config.fb_count = 1;

  if (psramFound()) {
    config.jpeg_quality = 10;
    config.fb_count = 2;
    config.grab_mode = CAMERA_GRAB_LATEST;
    config.frame_size = FRAMESIZE_VGA;
  } else {
    config.frame_size = FRAMESIZE_QVGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
  }

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("❌ Camera init failed 0x%x\n", err);
    logError("Camera Init Error", "ESP_ERR 0x" + String(err, HEX));
    blinkLED(2, 150);
  } else {
    Serial.println("✅ Camera initialized");
    blinkLED(2, 300);
  }
}

// ---------------------- FIREBASE CALLBACK ----------------------
void tokenStatusCallback(TokenInfo info) {
  if (info.status == token_status_ready) {
    Serial.println("✅ Firebase token ready");
  }
}

// ---------------------- GPS LOCATION ----------------------
bool getGPSLocation() {
  Serial.println("📡 Reading GPS...");
  unsigned long start = millis();
  while (millis() - start < 15000) {  // wait up to 15s
    while (gpsSerial.available() > 0) {
      gps.encode(gpsSerial.read());
      if (gps.location.isUpdated()) {
        latitude = String(gps.location.lat(), 6);
        longitude = String(gps.location.lng(), 6);
        Serial.println("✅ GPS Fix: " + latitude + ", " + longitude);
        blinkLED(1, 200);
        gpsReady = true;
        return true;
      }
    }
    delay(10);
  }

  // Timeout → use default once
  if (!gpsReady) {
    Serial.println("⚠️ GPS timeout - using default coordinates");
    latitude = "11.158182";
    longitude = "77.282081";
    gpsReady = true;
    blinkLED(2, 200);

    FirebaseJson fallbackLog;
    fallbackLog.set("type", "GPS Timeout Fallback");
    fallbackLog.set("message", "Used default coordinates due to GPS timeout");
    fallbackLog.set("latitude", latitude);
    fallbackLog.set("longitude", longitude);
    fallbackLog.set("timestamp", String(time(nullptr)));
    Firebase.RTDB.pushJSON(&fbdo, "/logs/events", &fallbackLog);

    Firebase.RTDB.setBool(&fbdo, "/alerts/current_status/locationRequired", false);
  }

  return false;
}

// ---------------------- STORAGE UPLOAD ----------------------
String uploadToStorage(camera_fb_t* fb) {
  String filename = "images/image_" + String(millis()) + ".jpg";
  Serial.println("📤 Uploading image to Firebase Storage...");

  if (Firebase.Storage.upload(&fbdo,
                              STORAGE_BUCKET,
                              fb->buf,
                              fb->len,
                              filename.c_str(),
                              "image/jpeg")) {
    Serial.println("✅ Uploaded to Storage: " + filename);
    blinkLED(3, 150);

    String url = "https://firebasestorage.googleapis.com/v0/b/" +
                 String(STORAGE_BUCKET) + "/o/" + filename + "?alt=media";
    return url;
  } else {
    String reason = fbdo.errorReason();
    Serial.println("❌ Upload failed: " + reason);
    logError("Storage Upload Error", reason);
    blinkLED(2, 150);
    return "";
  }
}

// ---------------------- SETUP ----------------------
void setup() {
#ifdef RTC_CNTL_BROWN_OUT_REG
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
#elif defined(SOC_RTC_CNTL_BROWN_OUT_REG)
  WRITE_PERI_REG(SOC_RTC_CNTL_BROWN_OUT_REG, 0);
#endif

  Serial.begin(115200);
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX, GPS_TX);

  pinMode(PIR_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.print("Connecting to WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  unsigned long wifiStart = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - wifiStart < 20000) {
    delay(500);
    Serial.print(".");
  }
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n❌ WiFi connection failed");
    logError("WiFi Error", "Failed to connect to WiFi");
  } else {
    Serial.println("\n✅ WiFi connected: " + WiFi.localIP().toString());
    blinkLED(1, 400);
  }

  configTime(19800, 0, "pool.ntp.org", "time.nist.gov");
  time_t now = time(nullptr);
  while (now < 100000) {
    delay(500);
    now = time(nullptr);
  }
  Serial.println("✅ Time synced");

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;
  config.token_status_callback = tokenStatusCallback;

  if (Firebase.signUp(&config, &auth, "", "")) {
    Serial.println("✅ Firebase signUp success");
  } else {
    Serial.printf("❌ Firebase signUp failed: %s\n", config.signer.signupError.message.c_str());
    logError("Firebase SignUp Error", config.signer.signupError.message.c_str());
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  startCamera();

  Serial.println("⏳ Fetching GPS location...");
  getGPSLocation();   // ✅ Run only once — no infinite retry loop
  Serial.println("✅ GPS ready (real or default). System ready for motion detection.");
  blinkLED(3, 200);
}

// ---------------------- LOOP ----------------------
void loop() {
  if (!gpsReady) {
    getGPSLocation(); // re-check only if never set
    delay(1000);
    return;
  }

  if (digitalRead(PIR_PIN) == HIGH && WiFi.status() == WL_CONNECTED) {
    Serial.println("🚨 Motion detected!");
    digitalWrite(LED_PIN, HIGH);
    delay(100);

    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("❌ Camera capture failed");
      logError("Camera Capture Error", "Failed to capture image");
      blinkLED(2, 150);
      digitalWrite(LED_PIN, LOW);
      return;
    }

    String imageUrl = uploadToStorage(fb);
    esp_camera_fb_return(fb);

    if (imageUrl != "") {
      FirebaseJson json;
      String path = "/images/" + String(millis());
      json.set("imageUrl", imageUrl);
      json.set("timestamp", String(time(nullptr)));
      json.set("status", "pending");
      json.set("latitude", latitude);
      json.set("longitude", longitude);

      if (Firebase.RTDB.setJSON(&fbdo, path.c_str(), &json)) {
        Serial.println("✅ Metadata saved: " + path);

        // ✅ Mark locationRequired = false
        Firebase.RTDB.setBool(&fbdo, "/alerts/current_status/locationRequired", false);

        // ✅ Push live location
        FirebaseJson gpsJson;
        gpsJson.set("lat", latitude);
        gpsJson.set("lon", longitude);
        Firebase.RTDB.setJSON(&fbdo, "/alerts/current_status/location", &gpsJson);

      } else {
        Serial.println("❌ Metadata save failed: " + fbdo.errorReason());
        logError("RTDB Write Error", fbdo.errorReason());
        blinkLED(2, 150);
      }
    }

    digitalWrite(LED_PIN, LOW);
    delay(7000);
  }

  delay(100);
}
