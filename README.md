# BL3372 Wi-Fi Setup

<img src="images/Background_with_icon_embedded.png" alt="" width="180" align="right">

A small Android app with one job: give the **BroadLink BL3372** Wi-Fi module in a
Runxin / Euro-Clear water-treatment controller the name and password of your home
Wi-Fi. It does this **locally**: phone → module's setup access point → done.

* no Runxin or BroadLink cloud, no account, no login, no Internet
* nothing is stored, logged or sent anywhere except to the module
* no analytics, telemetry, ads or third-party SDKs

It implements the same packet that the open-source
[python-broadlink](https://github.com/mjg59/python-broadlink) `broadlink.setup()` sends,
natively in Kotlin. See [docs/PROTOCOL.md](docs/PROTOCOL.md) for how the protocol was
derived and which sources it was checked against.

> Not affiliated with Runxin, Euro-Clear or BroadLink. Version 1 only provisions Wi-Fi.
> It does not control the softener and does not register it with any cloud.

## Using it

1. Put the water-treatment controller into **Wi-Fi setup mode**. It then broadcasts a
   network called **`WiFi-BL3372`**.
2. Open the app and tap **Open Wi-Fi networks**. Join `WiFi-BL3372`. If Android
   says the network has no Internet access, choose to **stay connected**.
3. Come back to the app. Step 1 confirms the connection:
   * If you allowed the optional *Check Wi-Fi name*, the app reads the network name
     directly.
   * Otherwise, tick the box confirming you're on `WiFi-BL3372`. Android only reveals
     Wi-Fi names to apps with Location permission, and the app doesn't otherwise need
     that permission.
4. Enter your home Wi-Fi **name** and **password**, and leave security on **WPA2**
   unless you know otherwise. It must be a **2.4 GHz** network.
5. Tap **Configure Device**.
   * The app sends the settings and listens for the module's acknowledgement.
   * It then watches the `WiFi-BL3372` network. When the module accepts the settings
     it reboots, the network disappears, and the app tells you so.
6. Your phone normally reconnects to your home Wi-Fi by itself. The app then searches
   for the module with BroadLink's local discovery and shows
   **"Device found at 192.168.x.x"**. You can also tap **Search for device** at any time.

On Android 17 the app also asks for **Nearby devices → local network access**. Android
blocks all LAN traffic without it, including traffic to the module's setup network.

## How it works (short version)

| | |
|---|---|
| Packet | 136 bytes: command `0x14`, SSID at `0x44`, password at `0x64` (32 bytes each), lengths at `0x84`/`0x85`, security at `0x86`, checksum `0xBEAF + Σbytes` (little-endian) at `0x20` |
| Transport | UDP to port **80**, sent to `255.255.255.255`, the subnet broadcast and the AP's gateway (the module) |
| Retries | up to 3 rounds, 2 s apart, stopping when the module acknowledges (`0x15` reply) or its network disappears |
| Routing | the socket is bound to the Wi-Fi `Network` with `Network.bindSocket()`, so the packet can't leave over mobile data, which Android prefers because the AP has no Internet |
| Discovery | python-broadlink's 48-byte hello broadcast on UDP 80. Runxin BL3372 modules report device type `0x520F` |

Full details, sources and the list of unverified assumptions:
[docs/PROTOCOL.md](docs/PROTOCOL.md).

## Limits you might hit

* **Password longer than 32 characters**: the BroadLink setup packet has room for 32
  bytes, and the official BroadLink app has the same limit. Use a shorter password or a
  guest network.
* **5 GHz-only or WPA3-only networks**: the BL3372 is a 2.4 GHz WPA/WPA2 device.
* **No acknowledgement**: some firmware never sends one, and python-broadlink doesn't
  wait for one either. The disappearing `WiFi-BL3372` network is the better signal.
* **Setup network stays up after 90 s**: check the name and password (case-sensitive),
  try *WPA/WPA2*, and try a letters-and-digits password. Some BroadLink firmware
  rejected special characters. Put the controller back into setup mode and send again.
* **"Android blocked local network access"**: allow *Nearby devices* for the app
  (Android 17+) and switch off any VPN while provisioning.
* **Phone keeps leaving `WiFi-BL3372`**: some phones abandon networks without
  Internet. Temporarily disable *Switch to mobile data* / *Adaptive Wi-Fi* /
  *Intelligent Wi-Fi*, or switch off mobile data during setup.
* **The setup packet is unencrypted.** This is how BroadLink's AP-mode setup works.
  The password crosses the module's (usually open) setup network once, so provision
  close to the device and don't leave the controller in setup mode.

## Building

Requirements: **JDK 17 or newer** (Android Studio's bundled JBR works), the
**Android SDK with platform 37**, and Internet access for the first Gradle run. AGP
downloads a missing platform automatically if the SDK licences are accepted.

### Android Studio

Open the folder with *File → Open*, wait for the Gradle sync, then use
*Build → Generate App Bundles or APKs → Generate APKs*. Or press *Run* with a phone
connected.

### Command line

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, run `gradlew.bat` instead. If `JAVA_HOME` isn't set, point it at Android
Studio's JDK first (for example `C:\Program Files\Android\Android Studio\jbr`). The
SDK location comes from `ANDROID_HOME` or a `local.properties` file containing
`sdk.dir=...`.

The installable APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

Install it with `adb install app/build/outputs/apk/debug/app-debug.apk`, or copy it to
the phone and open it there (allow *Install unknown apps* for your file manager).

A minified release build is `./gradlew assembleRelease`. It's unsigned until you add
your own signing config or sign it with `apksigner`.

## Project layout

```
app/src/main/java/io/github/kriziw/bl3372setup/
├── MainActivity.kt                 edge-to-edge Compose host; no autofill, no recents thumbnail
├── Permissions.kt                  runtime-permission checks and system-settings intents
├── broadlink/                      pure Kotlin, no Android dependencies (unit-tested)
│   ├── BroadlinkPackets.kt         packet building/parsing: setup, hello, ack, checksum
│   ├── BroadlinkProvisioner.kt     UDP send + retries + acknowledgement wait
│   ├── BroadlinkDiscovery.kt       hello broadcast + response collection
│   └── Udp.kt                      socket factory interface, broadcast address maths
├── network/                        Android networking
│   ├── WifiNetworkMonitor.kt       NetworkCallback: current Wi-Fi, SSID, IP, gateway
│   └── NetworkBinding.kt           Network.bindSocket() socket factory, MulticastLock
└── ui/
    ├── MainViewModel.kt            state machine for steps 1-4
    ├── MainScreen.kt               Compose UI
    └── theme/Theme.kt
docs/PROTOCOL.md                    protocol research and sources
tools/golden_vectors.py             regenerates test vectors from python-broadlink's own code
```

## Tests

`./gradlew testDebugUnitTest` runs JVM tests that need no hardware:

* **`BroadlinkPacketsTest`**:
  * the setup and hello packets match, byte for byte, the output of
    python-broadlink's own `setup()`/`scan()` (vectors from `tools/golden_vectors.py`);
  * it also checks length, every field offset, the security codes, the SSID/password
    encoding, the checksum, the 32-byte limits, ack recognition and response parsing.
* **`BroadlinkProvisionerTest` / `BroadlinkDiscoveryTest`**: the real UDP code against a
  fake BL3372 on a loopback socket. Covers ack handling, bounded retries, multiple
  destinations, the link disappearing mid-send, and discovery de-duplication.
* **`Ipv4Test`, `CredentialsFormTest`**: broadcast address maths, destination list,
  credential redaction in `toString()`.

## Privacy and security

* Credentials live only in memory, in the ViewModel. They aren't saved, aren't put in
  saved-instance state, and are redacted from `toString()`. The packet buffer is zeroed
  after sending.
* The window is excluded from autofill ("save password?") and from the recent-apps
  thumbnail (Android 13+).
* `allowBackup="false"` and data-extraction rules exclude everything.
* There's no logging of any kind, and no network access other than UDP to the local
  network.
* `INTERNET` is declared only because Android requires it for any socket.

## Credits

Protocol knowledge comes from [python-broadlink](https://github.com/mjg59/python-broadlink)
(MIT), cross-checked against [rbroadlink](https://github.com/nicholascioli/rbroadlink),
[waringer/broadlink](https://github.com/waringer/broadlink),
[broadlink-smartbulb](https://github.com/pinei/broadlink-smartbulb) and
[ypsilon-local](https://github.com/Danirv/ypsilon-local). No code was copied. Icons in
`res/drawable/ic_*.xml` are Material icons (Apache 2.0).
