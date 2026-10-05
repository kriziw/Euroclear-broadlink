# WaterCare

<img src="images/Background_with_icon_embedded.png" alt="" width="180" align="right">

An Android app for water softeners with a Runxin controller and a **BroadLink BL3372** Wi-Fi
module, such as the Euro-Clear Midnight series. Its controller functions work locally:

1. **Wi-Fi setup**: gives the module your home Wi-Fi name and password, step by step.
2. **Device dashboard**: shows status, water use, salt, alarms and settings, and lets you
   change the safe settings or start a regeneration.

Controller setup and operation need no Runxin or BroadLink cloud, account or Internet.
App update checks contact GitHub for public releases; APKs are downloaded only when you
choose to update. Automatic checks can be disabled. No controller information or Wi-Fi
credentials are sent to GitHub. There are no analytics, telemetry, ads or vendor SDKs.

The app is in **Hungarian** by default and in **English** when the phone is set to English.
You can pick either in the app under *Settings → Language* (the gear on the home screen).
It follows the phone's light or dark theme.

> Not affiliated with Runxin, Euro-Clear or BroadLink. Protocol details and sources:
> [docs/PROTOCOL.md](docs/PROTOCOL.md) (Wi-Fi setup) and
> [docs/DEVICE_PROTOCOL.md](docs/DEVICE_PROTOCOL.md) (dashboard and controls).

## Download

Get `bl3372-wifi-setup-<version>.apk` from
[Releases](https://github.com/kriziw/Euroclear-broadlink/releases) and open it on the phone.
Allow *Install unknown apps* for your browser or file manager when asked. Android 10 or newer
is required.

Each release lists the APK's SHA-256. All releases are signed with the same certificate:

```
SHA-256: d4:9d:c4:92:29:e6:e7:b9:00:43:9a:ee:82:01:69:ff:d8:ba:13:b0:70:4a:40:9f:ab:ed:84:97:77:79:0a:0b
```

## Setting up a new softener

The wizard has four screens. *Next* only unlocks once the current step is done.

1. **Connect to the device.**
   * Enter Wi-Fi setup mode using the device manual. On some Runxin heads, reconnect
     power and hold **Menu/OK** until it beeps.
   * Join the Wi-Fi network created by the device. Names may include **`WiFi-BL3372`**,
     `WiFi-BL…`, `BroadlinkProv` or `BroadLink_…`, but other names are accepted.
   * Tap **Open Wi-Fi networks**, join it, and choose to stay connected if Android warns
     about no Internet.
   * Confirm with the checkbox that you joined the device's Wi-Fi. The optional name
     check provides a hint; a different/hidden name or reported Internet access does
     not override your confirmation. Changing Wi-Fi connections clears confirmation.
2. **Your home Wi-Fi.** Enter the name and password of a **2.4 GHz** network. Security
   WPA2 is right for almost every router.
3. **Configure.**
   * Tap **Configure Device**.
   * If the connection changed, confirm the device's Wi-Fi directly on this screen.
   * The app sends the settings, then watches the confirmed connection. When the module accepts them it
     reboots, the network disappears, and the app moves on by itself.
4. **Find the device.** Once your phone is back on your Wi-Fi, the app searches for the
   module. Tap **Save and open** to add it to the dashboard.
   Other Broadlink devices, such as thermostats, are hidden by default. Use **Show other
   Broadlink devices** to reveal them without rescanning. Unidentified module types also
   appear there; the filter uses the reported device type rather than its name.

On Android 17 the app also asks for **Nearby devices → local network access**. Android blocks
all local-network traffic without it.

## Adding a softener that is already on your network

On the home screen, tap **Add a device** → **Add a device already on my network**, then
search the current network. If the softener is on another VLAN or subnet, use *Device on another VLAN or
subnet?* (see below).

## The dashboard

With one saved softener the app opens straight on it. With several it shows a list, and
*Add a device* is always there. The connection state sits under the device name; a card
only appears when something needs attention (no Wi-Fi, device not found, permission).

| Section | Contents |
|---|---|
| Status | valve phase with time left (in service, backwash, brine and slow rinse, …), remaining capacity against capacity per cycle, current flow, vacation state when active, **Regenerate now**, **Vacation** (experimental controllers only, see below) |
| Needs attention | alarms and reminders, shown only when there are any |
| Tiles | used today, controller weekly average, salt added ✎, regeneration mode |
| Settings | raw water hardness ✎, regeneration time ✎, controller clock *sync with phone*, SafeHOME continuous-flow limit ✎ and maximum flow ✎ |
| Details (folded) | salt and maintenance, regeneration programme, detected controller (address, module type, controller code, profile), diagnostics (raw fields) |

Tapping a setting opens an editor with the current value selected, so you can type the new
one straight away.

**How changes are made safely**
* Every change is sent **once**, then the controller is **read back**. You only see "saved"
  when the controller itself reports the new value.
* *Regenerate now* asks for confirmation first, is only offered while the softener is in
  service, and is confirmed once the valve actually starts moving.
* Only settings that were verified on real hardware can be changed.
* **Vacation mode** can be started from service and ended from the vacation pause, matching the
  controller's own ▼ button. The controller then refills the brine tank, dissolves salt for 4 h,
  runs a shortened brine draw and pauses until you end it. It is offered only on experimental
  controllers (for example the Midnight, model 12). On the verified F79D (model 9) the command is
  acknowledged but ignored, so it is not shown there. The app reports success only when the
  controller's own vacation flag changes; otherwise hold ▼ for 6 s on the controller.

**Controller models.** After connection the app reads the controller identity and loads a
matching bundled profile. Controls unlock automatically only for the combination verified
on hardware: Runxin F79D (model 9) behind a BL3372 (type `0x520F`). The detected module type,
controller code and loaded profile are shown under *Details → Detected controller*.
* For any other model the dashboard shows the values with a warning, and the controls stay
  locked.
* You can unlock experimental controls after confirming that the values match the controller's
  display. The experimental warning remains visible, and you can lock them again. The opt-in
  belongs to that exact controller code; a changed or missing code cannot reuse it. Existing
  unlocks from earlier versions require confirmation again.
* The Midnight's ECOPRO+ head has not been confirmed yet. If yours is locked, compare a few
  values (hardness, regeneration time) with the controller before unlocking.
* Other BroadLink module types remain unsupported and receive no Runxin commands.

Open **Controller compatibility** from *Settings* or *Details → Detected controller* for the official
Runxin/BroadLink portfolio guide. Wi-Fi product names are not automatically treated as local
protocol IDs. Source links open your browser and need Internet access; profile selection
itself works locally. Evidence and limits: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
The offline compatibility guide searches model names and inspected product aliases,
with separate Supported and Unverified lists. Unverified models are grouped by family;
each entry shows its documentation when expanded. Supported profiles apply across
compatible softener products reporting the same controller identity. Family membership
and manufacturer listings alone cannot enable controls.

## App updates

On launch the app checks this repository's public releases and shows a dismissible
notice for a newer stable version with an uploaded, verifiable APK. Dismissing a
version suppresses that version's automatic notice; *Settings → App updates* still
lets you check and download manually. Turn off **Check for updates
on launch** there to prevent automatic GitHub requests. Checks fail quietly at launch
when offline and do not stop local controller operation. Manual failures are shown
on the update screen.

The app downloads a chosen APK into private cache, verifies its SHA-256 against the
GitHub asset digest or the release's `.sha256` file, and checks the package name,
version and pinned release signing certificate. It then asks Android to install
the update. You may need to allow **Install unknown apps** for WaterCare, and Android
still requires your confirmation. No silent installation or background APK download
is performed. Progress, cancellation, retry and release notes are available in the app.

Signed repository releases can update earlier releases signed with the same key.
Android Studio debug builds use another key and cannot be updated by a release APK;
the app explains this instead of handing an incompatible APK to the installer.
Installation on a physical device is still required to verify the complete Android
permission/installer flow. Implementation and validation: [docs/APP_UPDATES.md](docs/APP_UPDATES.md).

## Networks with several VLANs

* **Broadcasts don't cross VLANs or routers.** The automatic search only covers the phone's
  own network.
* **Everything after discovery is unicast and routes normally.** For a softener on, for
  example, an IoT VLAN:
  1. In *Find the device*, open **Device on another VLAN or subnet?**.
  2. Enter its **IP address or hostname** and tap *Connect*, or enter its **subnet** (e.g.
     `192.168.20.0/24`, at most /22) and tap *Scan*.
  3. Allow **UDP port 80 from the phone's VLAN to the softener** in your firewall. Replies
     go back to the phone's source port; a stateful firewall allows them automatically.
* **If a saved softener's address changes**, the app sweeps the /24 around its last address
  and recognises it by MAC. You can also set the address under *⋮ → Change address*. A DHCP
  reservation for the softener avoids this.

Wi-Fi setup itself is unaffected by VLANs: it talks to the module's own access point.

## Limits you might hit

* **Password longer than 32 characters**: the BroadLink setup packet has room for 32 bytes,
  and the official app has the same limit. Use a shorter password or a guest network.
* **5 GHz-only or WPA3-only networks**: the BL3372 is a 2.4 GHz WPA/WPA2 device.
* **Setup network still up after 90 s**:
  * Check the name and password; both are case-sensitive.
  * Try *WPA/WPA2*, and try a password with only letters and digits.
  * Put the controller back into setup mode and send again.
* **"Android blocked local network access"**: allow *Nearby devices* for the app (Android
  17+) and switch off any VPN.
* **"The module refused the local login"**: this can happen after pairing with the vendor
  cloud app. Setting up the Wi-Fi again with this app usually resets it.
* **The Wi-Fi setup packet is unencrypted.** That is how BroadLink's setup works, so do the
  setup close to the device.

## Building

Requirements: **JDK 17+** (Android Studio's bundled JBR works), the **Android SDK with
platform 37**, and Internet access for the first Gradle run.

```bash
./gradlew testDebugUnitTest assembleDebug
```

On Windows, run `gradlew.bat`. The installable APK is `app/build/outputs/apk/debug/app-debug.apk`.
You can also open the folder in Android Studio and press *Run*.

### Signed release build (local)

`./gradlew assembleRelease` builds a minified APK, about 2.8 MB. To sign it, set four values as
environment variables or in `~/.gradle/gradle.properties`. Never put them in the repository.

```properties
BL3372_KEYSTORE_PATH=C:/path/to/release.jks
BL3372_KEYSTORE_PASSWORD=...
BL3372_KEY_ALIAS=...
BL3372_KEY_PASSWORD=...
```

Without them, the release APK is left unsigned.

### Releases (release-please)

Releases are automated with [release-please](https://github.com/googleapis/release-please):

1. Merge pull requests into `main` with **conventional titles**: `feat: …` for features,
   `fix: …` for bug fixes, or `chore:`, `docs:`, `ci:` and so on. Use **Squash and merge**.
   The *PR Title* check enforces the format.
2. On every push to `main`, release-please opens or updates a **release PR**. That PR bumps
   `version.txt` (which sets the app's `versionName`) and adds the new entries to
   `CHANGELOG.md`.
   * Before 1.0, `feat` bumps the minor version and `fix` bumps the patch.
   * `versionCode` is derived from the version (`1.2.3` → `1002003`), so it always increases.
3. **Merge the release PR.** release-please then creates the `vX.Y.Z` tag and the GitHub
   release. The same workflow builds and signs the APK, checks the certificate fingerprint,
   and attaches `bl3372-wifi-setup-X.Y.Z.apk` and its SHA-256 to the release.

*Actions → Release APK → Run workflow* builds a signed APK from any branch as a downloadable
artifact, without making a release.

Signing uses four repository secrets under *Settings → Secrets and variables → Actions*:

| Secret | Value |
|---|---|
| `BL3372_KEYSTORE_BASE64` | the release keystore (`.jks`) file, base64-encoded |
| `BL3372_KEYSTORE_PASSWORD` | the keystore password |
| `BL3372_KEY_ALIAS` | the key alias |
| `BL3372_KEY_PASSWORD` | the key password (for a PKCS12 keystore, the same as the keystore password) |

release-please also needs permission to open its PR. Use one of these:

* Enable *Settings → Actions → General → Workflow permissions → **Allow GitHub Actions to
  create and approve pull requests***.
* Add a `RELEASE_PLEASE_TOKEN` secret holding a fine-grained personal access token for this
  repository, with *Contents* and *Pull requests* read/write. With a token, CI also runs on
  the release PR.

## Project layout

```
app/src/main/java/io/github/kriziw/bl3372setup/
├── MainActivity.kt, Bl3372App.kt   Compose host; app-wide Wi-Fi monitor and device store
├── Permissions.kt                  runtime permissions and system-settings intents
├── broadlink/                      pure Kotlin, unit-tested
│   ├── BroadlinkPackets.kt         AP setup, hello, ack, checksum
│   ├── BroadlinkProvisioner.kt     setup packet transmission
│   ├── BroadlinkDiscovery.kt       broadcast and unicast (cross-VLAN) discovery
│   ├── BroadlinkCommand.kt         encrypted auth/command packets (AES-128-CBC)
│   ├── BroadlinkSession.kt         authenticated session over UDP
│   └── Udp.kt                      socket factory, IPv4/subnet helpers
├── runxin/                         pure Kotlin, unit-tested
│   ├── RunxinFrames.kt             5A 5C / DF FD frame codec
│   ├── F79d.kt                     field decoding and verified settings
│   └── SoftenerClient.kt           BL3372 envelope, retry policy, write-and-verify
├── network/                        Wi-Fi tracking, Network.bindSocket(), error mapping
├── devices/DeviceStore.kt          saved devices (MAC, name, address; no credentials)
└── ui/                             AppRoot (navigation), home/, setup/ (wizard), device/, common/
app/src/main/res/values/            Hungarian strings (default)
app/src/main/res/values-en/         English strings
docs/                               protocol notes and sources
tools/                              golden-vector generators that run the reference implementations
```

## Tests

`./gradlew testDebugUnitTest` runs JVM tests that need no hardware:

* **Golden vectors.** These are checked byte for byte against the reference code:
  * setup, hello, encrypted auth and command packets, generated by python-broadlink's own code;
  * Runxin query/write frames and decoding, generated by ypsilon-local's own code.
* **A simulated BL3372 + F79D on loopback UDP.** It drives the whole path: authentication,
  reads, verified writes, lost acknowledgements, ignored writes, forced regeneration,
  transient `-5` errors and key expiry.
* **UDP behaviour.** Setup retries, unicast subnet sweeps that stop early, IPv4/subnet
  parsing, and credential redaction.
* **App updates.** Stable version selection, asset identity, checksums, download
  corruption, HTTP/redirect failures, cancellation and partial-file cleanup.

## Privacy and security

* Wi-Fi credentials stay in memory only. They are never stored, never put in saved state,
  and redacted from `toString()`. The packet buffer is zeroed after sending.
* Saved devices hold identity/addressing information and any experimental-control opt-in,
  including the controller code it applies to. They never hold credentials or session keys.
* Autofill is excluded, and so is the recent-apps thumbnail (Android 13+). Backups are
  disabled.
* Controller commands use local UDP. Update checks use HTTPS to GitHub, and
  user-initiated downloads use GitHub release storage. Update preferences and verified
  APKs stay in app-private preferences/cache. There is no telemetry or credential logging.

## License

GPL-3.0. See [LICENSE](LICENSE). Parts of the controller protocol code are ported from
[ypsilon-local](https://github.com/Danirv/ypsilon-local) (Apache-2.0). Third-party details are
in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Credits

Protocol knowledge comes from [python-broadlink](https://github.com/mjg59/python-broadlink)
(MIT) and [ypsilon-local](https://github.com/Danirv/ypsilon-local) (Apache-2.0). It was
cross-checked against [rbroadlink](https://github.com/nicholascioli/rbroadlink),
[waringer/broadlink](https://github.com/waringer/broadlink) and
[broadlink-smartbulb](https://github.com/pinei/broadlink-smartbulb). The Euro-Clear Midnight
manual provided the setup-mode procedure and the Hungarian terminology.
