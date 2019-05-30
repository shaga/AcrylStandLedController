#include <Adafruit_NeoPixel.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>

#define UUID_LED_SERVICE        "5BDC13B0-F954-43D3-939B-4F701FFD80D8"
#define UUID_LED_CHAR           "5BDC13B1-F954-43D3-939B-4F701FFD80D8"

static const int PinLed = 14;
static const int NumLeds = 5;

static uint8_t colors[] = {0, 0, 0};
Adafruit_NeoPixel pixels(NumLeds, PinLed, NEO_GRB + NEO_KHZ800);

class MyBleCallbacks : public BLEServerCallbacks {
public:
  static bool is_connected;
  
  void onConnect(BLEServer *server) {
    is_connected = true;
  }

  void onDisconnect(BLEServer *server) {
    is_connected = false;
  }
};

bool MyBleCallbacks::is_connected = false;
static BLECharacteristic *my_characteristic = NULL;

class MyBleCharCallbacks : public BLECharacteristicCallbacks {
public:

    static bool is_updated;

    void onWrite(BLECharacteristic *characteristic) {
        Serial.println("fuga");
        if (is_updated) return;

        int len = characteristic->getValue().length();
        Serial.print("hoge:");
        Serial.println(len);
        if (len != 3) return;

        uint8_t *data = characteristic->getData();
        colors[0] = data[0];
        colors[1] = data[1];
        colors[2] = data[2];

        is_updated = true;
    }

    void onRead(BLECharacteristic *characteristic) {

    }
};

bool MyBleCharCallbacks::is_updated = false;

void setLedColor() {
    pixels.clear();

    for (int i = 0; i < NumLeds; i++) {
        pixels.setPixelColor(i, pixels.Color(colors[0], colors[1], colors[2]));
    }

    pixels.show();
}

void initBle() {
    BLEDevice::init("LED_CONTROLLER");

    BLEServer *server = BLEDevice::createServer();
    server->setCallbacks(new MyBleCallbacks);
    BLEDevice::setEncryptionLevel((esp_ble_sec_act_t)ESP_LE_AUTH_REQ_SC_BOND);

    BLEService *service = server->createService(BLEUUID(UUID_LED_SERVICE));

    my_characteristic = service->createCharacteristic(
        BLEUUID(UUID_LED_CHAR),
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE_NR);
    
    my_characteristic->setValue(colors, 3);
    my_characteristic->setCallbacks(new MyBleCharCallbacks());

    service->start();


    BLESecurity *security = new BLESecurity();
    security->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
    security->setCapability(ESP_IO_CAP_NONE);
    security->setInitEncryptionKey(ESP_BLE_ENC_KEY_MASK | ESP_BLE_ID_KEY_MASK);

    server->getAdvertising()->addServiceUUID(UUID_LED_SERVICE);
    server->getAdvertising()->start();
}

void setup() {
    Serial.begin(115200);
    pixels.begin();
    delay(100);
    setLedColor();
    setLedColor();
    
    initBle();

    Serial.println("start>>>");
}

void loop() {
    if (!MyBleCharCallbacks::is_updated) return;
    Serial.println("update");
    setLedColor();
    MyBleCharCallbacks::is_updated = false;
}