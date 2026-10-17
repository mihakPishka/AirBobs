# AirBobs

**Pods** — AirPods companion app for Android.

iPhone-style AirPods experience on Android, **no root required**.

- **Pop-up on case open** with left / right / case battery, slides away when you close the lid
- **Noise control**: Off / Noise Cancellation / Transparency / Adaptive, Conversation Awareness, Adaptive strength
- **Custom stem controls** per AirPod (press 1× / 2× / 3× / hold → track, volume, noise mode…)
- **Exact battery** (1% steps) when connected, charging screen with time-to-full estimate
- Battery card in notifications and on the **lock screen**, **home-screen widget**
- Low-battery and fully-charged alerts
- English / Русский, dark & light theme, Android 8.0+

## Compatibility

| Model | Battery & pop-up | Noise control | Stem controls |
|---|---|---|---|
| AirPods 1 / 2 | ✅ | — | — |
| AirPods 3, AirPods 4 | ✅ | — | ✅ |
| AirPods 4 (ANC) | ✅ | ✅ | ✅ |
| AirPods Pro / Pro 2 / Pro 3 | ✅ | ✅ | ✅ |
| AirPods Max | ✅ | ✅ (no Adaptive) | — |

Tested on real hardware: **AirPods Pro 3 + Pixel 11 (Android 17)**. Other models follow the same protocol but are untested — reports welcome.

Battery and the pop-up work on practically any Android phone. Noise control and stem controls need the phone to allow an app-level classic L2CAP connection; some phones block it without root. The app detects this and keeps the rest working.

## How it works

- **Battery / lid state** — AirPods broadcast Apple "Proximity Pairing" BLE advertisements (manufacturer data `0x004C`, type `0x07`). Byte 8 bit `0x08` = lid closed, which is how the pop-up hides instantly.
- **Controls** — Apple Accessory Protocol (AAP) over classic L2CAP, PSM `0x1001`, opened through hidden `BluetoothDevice.createInsecureL2capSocket` via [HiddenApiBypass](https://github.com/LSPosed/AndroidHiddenApiBypass).
  Notable packets: noise mode `09 00 0D xx`, Conversation Awareness `09 00 28 xx`, Adaptive strength `09 00 2E xx`, stem-press intercept `09 00 39 mask` → events `19 00 <press> <bud>`, battery `04 00 …`. The feature-flags packet must be `4D 00 D7 …` — with `FF` the AirPods reject Adaptive mode.

Protocol research credits: [LibrePods](https://github.com/kavishdevar/librepods), [OpenPods](https://github.com/adolfintel/OpenPods), furiousMAC's continuity docs.

## Install

Grab the APK from [Releases](../../releases), open it on the phone and allow installing from that source. Then open Pods, tap the yellow **Permissions needed** card and allow everything; open the AirPods case near the phone once so the app remembers your model.

## Build

Requirements: JDK 17, Android SDK (platform 36).

```bash
./gradlew assembleDebug
```

Release signing reads `keystore.properties` in the project root (not committed):

```properties
storeFile=/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Product images

The repo ships without product photos: the app draws its own illustrations. To use your own pictures, drop transparent PNGs into `app/src/photos/res/drawable-nodpi/` (git-ignored; `-PpublicBuild` builds without them):

`img_<family>_pods.png` (pair), `img_<family>_case.png`, `img_<family>_pod.png` (single, right bud)
where `<family>` is `airpods12`, `airpods3`, `airpods4`, `pro`, `pro3` or `max`.

## License

[MIT](LICENSE)

## Disclaimer

Not affiliated with Apple Inc. AirPods is a trademark of Apple Inc.

---

### По-русски

Приложение для AirPods на Android без root. Окно с зарядом при открытии кейса, шумоподавление, свои действия на нажатия ножки, точный заряд, виджет и карточка на экране блокировки. Работает на Android 8 и новее. Проверено на AirPods Pro 3 + Pixel 11. Сборка: `./gradlew assembleDebug`.
