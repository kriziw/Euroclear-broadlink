# Reading and controlling the softener — protocol notes

Companion to [PROTOCOL.md](PROTOCOL.md), which covers Wi-Fi provisioning and discovery. This
document covers what happens after the module is on your network: the encrypted BroadLink
session, the BL3372 envelope, the Runxin F79D frames and fields, and the safety rules for
writes.

The layers are separate in the code, and each is unit-tested against vectors produced by the
reference implementations themselves (`tools/golden_device_vectors.py`).

```text
DeviceViewModel ─ SoftenerClient (read state, write + verify)        runxin/SoftenerClient.kt
                   └ F79D codec (fields ↔ values)                    runxin/F79d.kt
                     └ Runxin frame (5A 5C … DF FD … DE … A5)         runxin/RunxinFrames.kt
                       └ BL3372 "TFB" envelope + retry policy         runxin/SoftenerClient.kt (Bl3372Transport)
                         └ BroadLink session (auth 0x65, cmd 0x6A)    broadlink/BroadlinkSession.kt
                           └ AES-128-CBC packets on UDP 80            broadlink/BroadlinkCommand.kt
```

## 1. BroadLink session (python-broadlink `Device.auth()` / `send_packet()`)

Every packet has a 0x38-byte header followed by an AES-128-CBC encrypted body, zero-padded to
16 bytes.

| Offset | Content |
|---|---|
| `0x00–0x07` | `5A A5 AA 55 5A A5 AA 55` |
| `0x20–0x21` | checksum of the whole packet (`0xBEAF` + Σ bytes, LE) |
| `0x22–0x23` | (replies) signed error code, LE: -1 auth failed, -5 busy, -7 key expired … |
| `0x24–0x25` | device type, LE (`0x520F` for the Runxin BL3372) |
| `0x26–0x27` | packet type: `0x65` authenticate, `0x6A` command |
| `0x28–0x29` | counter, LE: random start, `+1` per packet, top bit always set |
| `0x2A–0x2F` | device MAC, reversed |
| `0x30–0x33` | session id, LE (0 before authentication) |
| `0x34–0x35` | checksum of the *unencrypted* payload |

* The initial AES key is `097628343fe99e23765c1513accf8b02` and the IV is
  `562e17996d093d28ddb3ba695a2e6f58`.
* The authentication payload is 0x50 bytes: `0x31` at 0x04–0x13, `01` at 0x1E and 0x2D,
  and `"Test 1"` at 0x30.
* The reply body, decrypted with the initial key, holds the session id (bytes 0–3) and the
  session key (bytes 4–19). Every later packet uses both.

The unit tests compare the app's encrypted auth request and 0x6A command byte for byte with the
packets python-broadlink produces (fixed counter), and parse an auth reply that python-broadlink's
own `auth()` accepts.

## 2. BL3372 envelope (ypsilon-local `transport/broadlink_bl3372.py`)

A Runxin frame travels inside command `0x6A`, prefixed with its length as two bytes, little-endian
("TFB"). The reply's decrypted body has the same prefix; anything after the declared length is
AES padding.

Retry rules, following ypsilon-local:

* **Reads** may be retried. Error -1 or -7 gets one re-authentication. Error -5 is
  empirically transient on the BL3372 and gets two retries, about 0.4 s and 0.8 s apart with
  jitter. Lost UDP packets are resent every second, like python-broadlink.
* **Writes are sent exactly once.** A missing reply is ambiguous: the controller may already
  have acted. The app never resends a write blindly. It reads the controller back instead
  (see §4).

## 3. Runxin frames and F79D fields (ypsilon-local `runxin/`)

```
5A 5C <len> 00×9 01 00 12 <innerLen> 00 | DF FD <innerLen> <opcode> <payload…> <sum> DE | <sum> A5
```

* Opcodes: `09` query (payload = field ids), `19` write (payload = `[id, b1, b2]` triples).
  Replies use `C9` and `D9`.
* Both checksums are the additive 8-bit sum of everything before them.
* On a new connection the app first queries field 1 to select a bundled controller profile.
  The F79D profile reads fields 1–51 every poll, and field 52 (a slow-changing service interval)
  once. Unmatched identities use this map as an explicitly experimental fallback.

Fields the app shows, with their encodings. The **W** column marks settings the app can change;
each of those was write-verified on real hardware by ypsilon-local (Ypsilon G6, F79D, model 9).

| ID | Meaning | Encoding | W |
|---:|---|---|:-:|
| 1 | controller model (9 = F79D) | u8 | |
| 4 | controller clock | hour, minute | ✔ |
| 6 | SafeHOME continuous-flow limit, min (0–120, 0 = off) | u8 | ✔ |
| 7 | SafeHOME flow shutoff, hundredths of m³/h (0–1000, 0 = off) | **u16 big-endian** | ✔ (unit 2 only) |
| 8 | volume unit: 0 gal, 1 L, 2 m³ | u8 | |
| 10 | regeneration time | hour, minute | ✔ |
| 11 | current flow, hundredths of the flow unit | u16 big-endian | |
| 12 | valve-closed reason: 257 manual, 513 leak, 769 continuous flow, 1025 flow exceeded | u16 LE | |
| 15–22 | phase durations and remaining times (backwash, brine/slow rinse, refill, fast rinse) | minutes, seconds | |
| 27–33 | faults, low brine, resin reminder, salt/filter reminder flags | bool / two flags | |
| 34 | valve phase: 0 service, 1 backwash, 2 brine draw, 3 refill, 4 fast rinse, 5 closed, 6 salt dissolving, 7 pause 1, 8 pause 2 | u8 | ✔ (1 = regenerate) |
| 35/37/39/41 | remaining capacity, today, weekly average, capacity per cycle | 3-byte volume over the field and its successor, decoded per unit | |
| 43 | salt added (kg, 0–100; bookkeeping, not a level sensor) | u8 | ✔ |
| 44/45/46 | service days, days remaining, regeneration by volume (0) or time (1) | u8 | |
| 47 | raw-water hardness, mg/l (50–1500) | u16 LE | ✔ |
| 49 | vacation flag | bool | read-only |
| 50/51 | salt dissolving / pause remaining, min | u8 | |
| 52 | filter media interval, days | u16 LE | |

**Vacation mode is read-only on purpose.** ypsilon-local showed that a direct field-49 write is
acknowledged but not applied on current F79D firmware. The Midnight manual's method (hold ▼ for
6 s while in service) is shown in the app instead.

## 4. Write rule: send once, then prove it by reading

1. Send the write frame once.
2. Read the controller back: every 0.5 s for up to 5 s for settings, every 1 s for up to 15 s
   for regeneration.
3. Report success only when a fresh read shows the requested value. Regeneration counts as
   confirmed once the valve phase leaves service.
4. Otherwise report "not confirmed". If the write itself got no reply, the result is flagged
   ambiguous.

Forced regeneration is only offered while the valve is in service and vacation mode is off. It
asks for confirmation first.

## 5. Model gating

Controls are enabled automatically only for the verified combination: BroadLink type `0x520F`
reporting controller model 9 (F79D).

The Euro-Clear Midnight's ECOPRO+ head uses the same "Water device" vendor app and the same
features: SafeHOME limits, hardness in mg/l, and the identical vacation sequence. Its manual
names the head type with an example of "F136". Its model number has **not** been confirmed.

For an unmatched model the app shows fallback readings with a persistent experimental warning
and keeps controls locked. You can unlock them after confirming that the displayed values match
the controller's own display. The opt-in is scoped to the reported controller code; old unscoped
opt-ins require confirmation again. You can relock controls. Writes are still verified by read-back.
Other module types receive no Runxin commands. Official portfolio research and profile-loading
rules are in [COMPATIBILITY.md](COMPATIBILITY.md).

## 6. VLANs and routed networks

* Discovery by broadcast (`255.255.255.255` or the subnet broadcast) only reaches the phone's own
  network.
* Everything else is unicast and routes normally: the directed hello, authentication and
  commands. So a softener on another VLAN works as long as UDP port 80 is allowed from the
  phone to the device. Replies go back to the phone's source port; stateful firewalls allow
  them.
* The app can add a device by IP or hostname (resolved through the Wi-Fi network), or sweep a
  subnet of /22 or smaller with unicast hellos, in paced batches of 32.
* If a saved device's address changes on another subnet, the app sweeps the /24 around its
  last address and recognises it by MAC. A DHCP reservation for the softener avoids this.

## Sources

* python-broadlink: `broadlink/device.py` (`Device.auth`, `send_packet`, `encrypt`/`decrypt`),
  `broadlink/exceptions.py` (error codes).
* Danirv/ypsilon-local (Apache-2.0): `runxin/framing.py`, `runxin/fields.py`, `runxin/f79d.py`,
  `runxin/semantics.py`, `transport/broadlink_bl3372.py`, `coordinator.py`, `button.py`,
  `docs/protocol.md`, `docs/f79d.md`, `docs/f79d-settings.md`, `docs/hardware-verification.md`.
* Euro-Clear *Midnight* manual (2025, HU): SafeHOME settings, hardness entry (nk° × 10 = mg/l),
  vacation procedure, Wi-Fi setup-mode procedure.
* Euro-Clear/Runxin RX-67/68/69/71/116/117 valve manual: parameter names (backwash,
  brine and slow rinse, refill, fast rinse, rinsing frequency, b-01/b-02 output modes).
