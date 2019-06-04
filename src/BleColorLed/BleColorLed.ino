#include <NeoPixelAnimator.h>
#include <NeoPixelBrightnessBus.h>
#include <NeoPixelBus.h>

//#include <Adafruit_NeoPixel.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <freertos/task.h>
#include <freertos/semphr.h>
#include <esp_spi_flash.h>

#define UUID_LED_SERVICE        "5BDC13B0-F954-43D3-939B-4F701FFD80D8"
#define UUID_LED_CHAR           "5BDC13B1-F954-43D3-939B-4F701FFD80D8"

static const int PinLed = 14;
static const int NumLeds = 5;
static const int TickLengthMs = 200;
static const uint32_t ColorDataSpiAddress = 0x100000;
static const uint8_t WriteFlagValue = 0x55;
static const uint8_t DefStartCoor[] = {0x00, 0x00, 0xFF};
static const uint8_t DefEndColor[] = {0x00, 0x00, 0xFF};
static const uint8_t DefChangeCount = 50;
static const uint8_t DefTurnTickCount = 6;

static void initColorData();
static void updateColorData(uint8_t *buffer);
static void taskChangeColor(void* parameters);

typedef struct {
    uint8_t change_count;
    uint8_t start_col[3];
    uint8_t end_col[3];
    uint8_t buf;
} ColorData_t;

static uint8_t colors[] = {0, 0, 0xff};
NeoPixelBus<NeoGrbFeature, Neo800KbpsMethod>  pixels(NumLeds, PinLed);
static BLECharacteristic *my_characteristic = NULL;
static BLEServer *server = NULL;

static uint32_t change_pos = 0;
static int32_t change_dir = 1;
static uint32_t change_slow_pow = 0;
TaskHandle_t handle_task_change_color = NULL;
SemaphoreHandle_t handle_semaphore = NULL;

static ColorData_t color_data;

class MyBleCallbacks : public BLEServerCallbacks {
public:
  static bool is_connected;
  
  void onConnect(BLEServer *server) {
    is_connected = true;
    if (server != NULL) server->getAdvertising()->stop();
  }

  void onDisconnect(BLEServer *server) {
    is_connected = false;
    if (server != NULL) server->getAdvertising()->start();
  }
};
bool MyBleCallbacks::is_connected = false;


class MyBleCharCallbacks : public BLECharacteristicCallbacks {
public:

    static bool is_updated;

    void onWrite(BLECharacteristic *characteristic) {
        if (is_updated) return;

        int len = characteristic->getValue().length();
        if (len != 7) return;

        uint8_t *data = characteristic->getData();

        updateColorData(data);   
    }

    void onRead(BLECharacteristic *characteristic) {

    }
};

bool MyBleCharCallbacks::is_updated = false;

void setLedColor() {
    //pixels.Clear();

    for (int i = 0; i < NumLeds; i++) {
        pixels.SetPixelColor(i,RgbColor(colors[0], colors[1], colors[2]));
        delay(1);
        pixels.Show();
    }
}
static void initColorData() {
    color_data.change_count = DefChangeCount;
    for (int i = 0; i < 3; i++) {
        color_data.start_col[i] = DefStartCoor[i];
        color_data.end_col[i] = DefEndColor[i];
    }
}

static void updateColorData(uint8_t *buffer) {
    bool no_change = true;

    while(xSemaphoreTake(handle_semaphore, (TickType_t)(50 * portTICK_RATE_MS)) == pdTRUE);

    if (buffer != NULL) {
        memcpy(&color_data, buffer, 7);
    }

    for (int i = 0; i < 3; i++) {
        if (color_data.start_col[i] != color_data.end_col[i]) {
            no_change = false;
            break;
        }
    }

    if (!no_change) {
        change_pos = 0;

        if (handle_task_change_color == NULL) {
            xTaskCreatePinnedToCore(taskChangeColor, "hoge", 4096, NULL, 1, &handle_task_change_color, 0);
        }        
    } else {
        if (handle_task_change_color != NULL) {
            vTaskDelete(handle_task_change_color);
            handle_task_change_color = NULL;
        }

        memcpy(colors, color_data.start_col, 3);

        setLedColor();
    }

    xSemaphoreGive(handle_semaphore);
}

static void taskChangeColor(void* parameters) {
    const TickType_t TickLength = (TickType_t)(TickLengthMs * portTICK_RATE_MS);
    TickType_t tick = xTaskGetTickCount();
    
    while(true) {
        if (handle_semaphore != NULL && xSemaphoreTake(handle_semaphore, (TickType_t)(100 * portTICK_RATE_MS)) == pdTRUE) {
            for (int i = 0; i < 3; i++) {
                if (color_data.end_col[i] > color_data.start_col[i]) {
                    colors[i] = color_data.start_col[i] + (color_data.end_col[i] - color_data.start_col[i]) * change_pos / color_data.change_count;
                } else if (color_data.end_col[i] < color_data.start_col[i]) {
                    colors[i] = color_data.start_col[i] - (color_data.start_col[i] - color_data.end_col[i]) * change_pos / color_data.change_count;                  
                } else {
                    colors[i] = color_data.start_col[i];
                }
            }

            setLedColor();
            if (change_pos == 0 && change_dir < 0) {
                change_dir = 1;
            } else if (change_pos == color_data.change_count && change_dir > 0) {
                change_dir = -1;
            }

            change_pos += change_dir;

            xSemaphoreGive(handle_semaphore);
            vTaskDelayUntil(&tick, TickLength);
        } else {
            Serial.print("sem:");
            Serial.println(handle_semaphore != NULL);
        }
    }
}

void initBle() {
    BLEDevice::init("LED_CONTROLLER");

    server = BLEDevice::createServer();
    server->setCallbacks(new MyBleCallbacks);
    BLEDevice::setEncryptionLevel((esp_ble_sec_act_t)ESP_LE_AUTH_REQ_SC_BOND);

    BLEService *service = server->createService(BLEUUID(UUID_LED_SERVICE));

    my_characteristic = service->createCharacteristic(
        BLEUUID(UUID_LED_CHAR),
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_WRITE_NR);
    
    my_characteristic->setValue((uint8_t*)&color_data, 7);
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
    pixels.Begin();

    handle_semaphore = xSemaphoreCreateBinary();
    if (handle_semaphore != NULL) {
        while(xSemaphoreGive(handle_semaphore) != pdTRUE);
    }

    initColorData();

    initBle();

    updateColorData(NULL);    

    Serial.println("start>>>");
}

void loop() {
}