# BroadLink AP-mode Wi-Fi provisioning — protocol notes

This document records how the provisioning packet sent by this app was derived,
which sources it was checked against, and which details remain unverified for
the BL3372 module in a Runxin / Euro-Clear water-treatment controller.

Every number below was checked against code. Where sources disagree, or where
something couldn't be verified, this document says so.

---

## 1. Primary reference: `python-broadlink`

Repository: <https://github.com/mjg59/python-broadlink>
Revision inspected: `730853e` (tag 0.19.0, 2024-04-17). `master`, `dev` and
`new_product_ids` all point at this commit. Nothing newer touches `setup()`.

### Call chain

```
broadlink.setup(ssid, password, security_mode, ip_address="255.255.255.255")
  └─ broadlink/__init__.py :: setup()          ← builds AND sends the packet (no helpers)
       ├─ const.py :: DEFAULT_BCAST_ADDR = "255.255.255.255"
       └─ const.py :: DEFAULT_PORT       = 80
```

`setup()` is self-contained. It doesn't use the encrypted `Device.send_packet()`
path, the AES key, or a device MAC. The packet is plaintext. The function body
(abridged):

```python
payload = bytearray(0x88)
payload[0x26] = 0x14                      # "This seems to always be set to 14"
for i, letter in enumerate(ssid):     payload[68  + i] = ord(letter)
for i, letter in enumerate(password): payload[100 + i] = ord(letter)
payload[0x84] = len(ssid)
payload[0x85] = len(password)
payload[0x86] = security_mode
checksum = sum(payload, 0xBEAF) & 0xFFFF
payload[0x20] = checksum & 0xFF
payload[0x21] = checksum >> 8
sock = socket.socket(AF_INET, SOCK_DGRAM)
sock.setsockopt(SOL_SOCKET, SO_REUSEADDR, 1)
sock.setsockopt(SOL_SOCKET, SO_BROADCAST, 1)
sock.sendto(payload, (ip_address, 80))
sock.close()
```

The CLI wrapper `cli/broadlink_cli --joinwifi SSID PASS` calls
`broadlink.setup(ssid, pass, 4)` (WPA1/2 mixed). The README example uses `3` (WPA2).

---

## 2. The setup ("join Wi-Fi") packet

Total length: **136 bytes (0x88)**. All bytes not listed are `0x00`.

| Offset (hex) | Offset (dec) | Size | Content |
|---|---|---|---|
| `0x00–0x1F` | 0–31   | 32 | zero |
| `0x20–0x21` | 32–33  | 2  | checksum, **little-endian** uint16 |
| `0x22–0x25` | 34–37  | 4  | zero |
| `0x26`      | 38     | 1  | command `0x14` ("join") |
| `0x27–0x43` | 39–67  | 29 | zero |
| `0x44–0x63` | 68–99  | 32 | SSID bytes, zero padded |
| `0x64–0x83` | 100–131| 32 | password bytes, zero padded |
| `0x84`      | 132    | 1  | SSID length in bytes |
| `0x85`      | 133    | 1  | password length in bytes |
| `0x86`      | 134    | 1  | security mode |
| `0x87`      | 135    | 1  | zero |

### Security mode (`0x86`)

| Value | Meaning | Offered in app |
|---|---|---|
| 0 | none / open | yes ("Open") |
| 1 | WEP | no (obsolete) |
| 2 | WPA1 | yes ("WPA") |
| 3 | WPA2 | yes, **default** |
| 4 | WPA1/WPA2 mixed | yes ("WPA/WPA2") |

Sources that agree: python-broadlink comment and README, `rbroadlink`
(`WirelessConnection::to_message`), and BroadLink's own App SDK docs for
`deviceAPConfig(ssid, password, type, …)`, which define `type` as
"0 none, 1 WEP, 2 WPA1, 3 WPA2, 4 WPA/WPA2 mixed".
`waringer/broadlink` (Go) also mentions `6 = wpa1/2 TKIP`. No other source
confirms this, so the app doesn't offer it.

**Unverified:** whether the BL3372 firmware actually uses this field, or detects
the security type from its own scan, is unknown. WPA3-only networks aren't
covered by any known code. WPA2/WPA3 "transition" networks should accept a WPA2
client.

### Checksum

```
checksum = (0xBEAF + Σ all 136 bytes, with bytes 0x20/0x21 = 0) & 0xFFFF
packet[0x20] = checksum & 0xFF
packet[0x21] = checksum >> 8
```

Identical in python-broadlink (`sum(payload, 0xBEAF) & 0xFFFF`),
`rbroadlink` (`network/util.rs::checksum`), `waringer/broadlink`
(`makeChecksum`, uint16 wrap-around) and `broadlink-smartbulb` (Node).
This is the same "BEAF" checksum used on every BroadLink local packet.

### Field limits and string encoding

* **SSID**: 1–32 bytes (802.11 limit and the field size).
* **Password**: 0–32 bytes. The field ends at `0x83` and `0x84` is the SSID
  length, so **WPA passphrases longer than 32 characters can't be sent**.
  * python-broadlink doesn't check this. 33–36 characters silently corrupt the
    length bytes, and 37 or more raise `IndexError`.
  * `rbroadlink` would panic on an out-of-bounds index.
  * python-broadlink issue #235 and #814 confirm the limit, and #814 adds that
    the **official BroadLink app also caps the password at 32 characters**.
  * The app rejects longer passwords with an explanation.
* **WPA/WPA2**: 802.11i passphrases are 8–63 characters, so the app requires
  8–32 bytes.
* **Encoding**: the app writes **UTF-8** bytes and the byte length.
  * For ASCII input this is byte-identical to every reference implementation.
    The unit tests check this against golden packets produced by python-broadlink's
    own code.
  * For non-ASCII characters the references differ. python-broadlink writes
    `ord(char)`, which is Latin-1 for U+0080–U+00FF and an exception above that.
    `rbroadlink` (`as_bytes()`) and `waringer/broadlink` (`[]byte(ssid)`) write
    UTF-8.
  * Routers and Android advertise non-ASCII SSIDs as UTF-8, so UTF-8 is the
    encoding the module has to match. The app still warns about non-ASCII
    characters.
  * python-broadlink `TROUBLESHOOTING.md` notes that some BroadLink firmware
    failed with non-alphanumeric passwords (fixed in later firmware).

### Golden example (generated by running python-broadlink's own `setup()`)

`setup("MyHomeWiFi", "correct-horse-42", 3)`:

```
0000000000000000000000000000000000000000000000000000000000000000
71c8000000001400000000000000000000000000000000000000000000000000
000000004d79486f6d6557694669000000000000000000000000000000000000
00000000636f72726563742d686f7273652d3432000000000000000000000000
000000000a100300
(272 hex digits = 136 bytes; one row = 32 bytes)
```

Checksum `0xC871`, stored `71 c8`. Four such vectors are checked in
`app/src/test/.../BroadlinkPacketsTest.kt`. The generator script is
`tools/golden_vectors.py`. It extracts `setup()`/`scan()` from a
python-broadlink checkout with `ast` and runs them against a stub socket.

---

## 3. Transport

| Item | Value | Source |
|---|---|---|
| Protocol | UDP/IPv4 | all implementations |
| Destination port | **80** | `const.DEFAULT_PORT`, rbroadlink, Go, Node |
| Destination address | `255.255.255.255` by default | python, rbroadlink |
| Alternative destinations | subnet broadcast (e.g. `192.168.10.255`) | python README ("You may need to specify a broadcast address if setup is not working") |
| | unicast to the device's IP | `waringer/broadlink` `Join(..., deviceIP)` |
| Source port | any (ephemeral) | all implementations |
| Socket options | `SO_BROADCAST` (python also sets `SO_REUSEADDR`) | python |
| Transmissions | python: **exactly one**, no wait. rbroadlink: one, then waits up to 10 s for one reply. Go: one, then waits for a `0x15` reply | |

### Device acknowledgement

`waringer/broadlink` documents the reply it expects to a join packet:

```
0000000000000000000000000000000000000000000000000000000000000000c4be0000000015000000000000000000
```

That is a 48-byte packet, all zero except the checksum `0xBEC4` (= `0xBEAF + 0x15`)
at `0x20` and command **`0x15`** at `0x26`. `rbroadlink` also waits for "a"
response without parsing it. The app treats a 48+-byte packet with `0x15` at
`0x26` and a valid checksum as an acknowledgement. **A missing ack isn't treated
as failure**, because python-broadlink never waits for one.

### The SoftAP

On BroadLink's own products the AP is named `BroadlinkProv` (python-broadlink
README) or `BroadLink_Device_Wifi` (rbroadlink README). The device sits at
`192.168.10.1` and gives the phone a `192.168.10.x` address (python-broadlink
PR #53 and issue #844). **For the BL3372 AP (`WiFi-BL3372`) the addressing is
unverified.** The app reads it from Android's `LinkProperties` at runtime and
doesn't hard-code it.

python-broadlink issue #844 (2026-09) shows the main pitfall. An unbound socket
sends `255.255.255.255` out via the **default route**. On a host with another
active uplink, that is the wrong interface. The call "succeeds" and the device
never receives anything. On Android the other uplink is cellular data, because
an AP without Internet is never the default network. That is why this app binds
its socket to the Wi-Fi `Network` (see §5).

---

## 4. Discovery ("hello") packet — used after provisioning

From `broadlink/device.py :: scan()` and `protocol.py :: Datetime.pack()`.
48 bytes (0x30), sent to `255.255.255.255:80`. python-broadlink resends it every
1 s (`DEFAULT_RETRY_INTVL`) until a 10 s timeout (`DEFAULT_TIMEOUT`).

| Offset | Size | Content |
|---|---|---|
| `0x08–0x0B` | 4 | UTC offset in **hours**, signed int32 LE (python uses the *standard* offset, `-time.timezone`, without DST) |
| `0x0C–0x0D` | 2 | year, uint16 LE |
| `0x0E` | 1 | minute |
| `0x0F` | 1 | hour |
| `0x10` | 1 | year % 100 |
| `0x11` | 1 | ISO weekday (Mon = 1 … Sun = 7) |
| `0x12` | 1 | day of month |
| `0x13` | 1 | month |
| `0x18–0x1B` | 4 | local IPv4 address, **reversed** byte order |
| `0x1C–0x1D` | 2 | local UDP port, uint16 LE |
| `0x20–0x21` | 2 | checksum (same algorithm as above) |
| `0x26` | 1 | command `0x06` |

`protocol.md` in python-broadlink has an older, off-by-one timestamp table
(seconds/minutes/hours). The code above is what python-broadlink actually sends,
and the app follows the code. `waringer/broadlink` notes the device replies to
the packet's *source* IP and port, not to the fields inside it.

Response fields read by python-broadlink: device type at `0x34–0x35` (LE), MAC at
`0x3A–0x3F` (reversed), name at `0x40…` (NUL-terminated UTF-8), lock flag at `0x7F`.

### BL3372 / Runxin specifics

[`Danirv/ypsilon-local`](https://github.com/Danirv/ypsilon-local) is a local
Home Assistant integration for Runxin F79D controllers with a BroadLink BL3372.
It talks to the module through python-broadlink 0.19.0 and documents:

* BroadLink device type **`0x520F`** for the BL3372 in a Runxin F79D,
* standard `hello` / `auth` / command `0x6A` on UDP port 80,
* in issue #17, a `hello()` reply naming the device `润新水处理器` ("Runxin water
  processor"), MAC prefix `1C:D1:D7`.

So the BL3372 in these controllers runs BroadLink's standard local LAN stack, and
the discovery step will recognise it. The app marks devices with type `0x520F`.

---

## 5. Android networking

* The Wi-Fi network is found with `ConnectivityManager.registerNetworkCallback()`
  using `TRANSPORT_WIFI` with `NET_CAPABILITY_INTERNET` removed, so an AP without
  Internet still matches.
* **`Network.bindSocket(DatagramSocket)`** is called on the provisioning socket
  and nothing else. This is the narrowest option: it sets that socket's routing
  mark to the Wi-Fi network, so the limited broadcast (`255.255.255.255`) leaves
  via `wlan0` even when cellular is the default network.
  * `bindProcessToNetwork()` would also work, but it re-routes **all** of the
    app's traffic (including DNS) and has to be undone. That isn't needed here.
  * Binding to the local IP address alone doesn't select Android's per-network
    routing table.
* The socket gets `setBroadcast(true)`. A `WifiManager.MulticastLock` is held only
  while waiting for replies, so the Wi-Fi driver's packet filter doesn't drop
  broadcast replies.
* The setup packet is sent to `255.255.255.255:80`, to the subnet broadcast
  derived from `LinkProperties`, and to the IPv4 gateway (the AP itself).

### Permissions

| Permission | Why | Prompt? |
|---|---|---|
| `INTERNET` | Required to open *any* socket. | no |
| `ACCESS_NETWORK_STATE` | `NetworkCallback`, `LinkProperties`, `NetworkCapabilities`. | no |
| `ACCESS_WIFI_STATE` | Wi-Fi state and, on Android 10/11, `WifiManager.getConnectionInfo()`. | no |
| `CHANGE_WIFI_MULTICAST_STATE` | `MulticastLock` while listening for broadcast replies. | no |
| `ACCESS_LOCAL_NETWORK` | **Android 17+.** Apps targeting API 37 need it for any LAN unicast/broadcast/multicast; without it `sendto` fails with `EPERM`. Requested at runtime (Nearby devices group) only on API 37+. | yes |
| `ACCESS_FINE_LOCATION` (+ `ACCESS_COARSE_LOCATION`) | **Optional.** Android 10+ only reveals the connected SSID to apps with precise location and the Location toggle on (`WifiManager.getConnectionInfo()` is `@RequiresPermission(ACCESS_FINE_LOCATION)`; on 12+ `NetworkCallback.FLAG_INCLUDE_LOCATION_INFO` redacts `WifiInfo` without it). Used only to confirm the phone is on `WiFi-BL3372`. If refused, the user confirms the network manually. | optional |

Not requested:

* **`NEARBY_WIFI_DEVICES`**. None of the APIs that need it (Wi-Fi Aware, P2P,
  RTT, local-only hotspot) are used, and it doesn't reveal the connected SSID.
* **`CHANGE_WIFI_STATE`**. The app never connects to networks itself, because
  the system Wi-Fi picker is more reliable across vendors than
  `WifiNetworkSpecifier`.

---

## 6. Assumptions that could not be verified without hardware

1. **The BL3372 in AP mode accepts the classic `0x14` join packet.** Evidence
   for it:
   * the module runs BroadLink's standard LAN protocol (ypsilon-local);
   * the BL3372-P datasheet lists "station and soft AP, SmartConfig and AP
     configuration";
   * BroadLink's SDK `deviceAPConfig()` uses the same security codes and "sends
     device network information via UDP".

   No public report shows `broadlink.setup()` against a `WiFi-BL3372` AP. The
   OEM-specific AP name suggests customised firmware.
2. Whether the BL3372 sends the `0x15` acknowledgement is unknown. The app works
   either way.
3. The AP's address plan (classic BroadLink: `192.168.10.1`) is unverified and
   read at runtime.
4. Resending is an app decision, not upstream behaviour. Up to 3 sends, 2 s
   apart, stopping on ack or when the AP disappears. Re-applying identical
   credentials is assumed to be harmless.
5. The security-mode byte's effect on BL3372 firmware is unknown. WPA2 is the
   default; WPA/WPA2 mixed (python CLI default) is offered as a fallback.

---

## 7. Sources

| Source | What was used |
|---|---|
| [mjg59/python-broadlink](https://github.com/mjg59/python-broadlink) `broadlink/__init__.py::setup`, `const.py`, `device.py::scan`, `protocol.py::Datetime`, `protocol.md`, `TROUBLESHOOTING.md`, `cli/broadlink_cli` | primary reference |
| python-broadlink [PR #53](https://github.com/mjg59/python-broadlink/pull/53) | origin of AP setup, AP addressing |
| python-broadlink issues [#235](https://github.com/mjg59/python-broadlink/issues/235), [#814](https://github.com/mjg59/python-broadlink/issues/814), [#844](https://github.com/mjg59/python-broadlink/issues/844) | 32-char limit, unbound-socket pitfall |
| [nicholascioli/rbroadlink](https://github.com/nicholascioli/rbroadlink) `src/network/wireless_connection.rs`, `src/network/util.rs`, `src/device.rs::connect_to_network` | independent Rust implementation |
| [waringer/broadlink](https://github.com/waringer/broadlink) `broadlinkrm/broadlinkrm.go::Join`, `Hello` | independent Go implementation, ack format |
| [pinei/broadlink-smartbulb](https://github.com/pinei/broadlink-smartbulb) `index.js::setup` | independent Node implementation (per-interface subnet broadcast) |
| [Danirv/ypsilon-local](https://github.com/Danirv/ypsilon-local) `docs/broadlink-bl3372.md`, `const.py`, issue #17 | BL3372 in Runxin F79D: devtype `0x520F`, standard LAN stack |
| BroadLink App SDK docs, `deviceAPConfig` (docs.ibroadlink.com, appsdk_05) | official security-type codes |
| BroadLink BL3372-P datasheet (manuals.plus / FCC filings) | module supports SoftAP + AP configuration |
| Android SDK 34 sources: `WifiInfo.getSSID`, `WifiManager.getConnectionInfo`, `ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO` | SSID permission rules |
| [developer.android.com — Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) | `ACCESS_LOCAL_NETWORK` for targetSdk 37 |
| [developer.android.com — Wi-Fi permissions](https://developer.android.com/develop/connectivity/wifi/wifi-permissions) | `NEARBY_WIFI_DEVICES` scope |

No Java/Kotlin/Android open-source implementation of the join packet was found.
GitHub code search found Java BroadLink *control/discovery* code (openHAB
`BroadlinkProtocol.java`, `a1aw/broadlink-java-api`, `dezi/TVPush`), but none of
it implements AP setup.
