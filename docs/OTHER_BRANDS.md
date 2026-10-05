# Other brands: local protocols and evidence

WaterCare talks to these softeners over their own local HTTP APIs. They were chosen from what the
Home Assistant community uses (reviewed 5 October 2026). WaterCare implements them from the
documents and code listed below; **none has been tested on real hardware by WaterCare yet**. They
are experimental: every write is sent once and reported as confirmed only when a fresh read
shows the requested value. Controls start switched off.

The transport is a small HTTP/1.1 client over sockets bound to the Wi-Fi network
(`network/LocalHttp.kt`), so devices on other VLANs work like the BroadLink path. These devices
only speak plain HTTP; their logins cross the local network unencrypted.

## JUDO (Connectivity Module)

* **Source:** JUDO, *API-Kommandozeilen* (judo.eu, 2024-11), the official command table per
  device type. Community cross-check: OStrama/judo_rest_api (Apache-2.0),
  mibragri/ha-judo-isoft (MIT).
* **Transport:** `GET http://<host>/api/rest/<command><00><data>` with HTTP basic auth
  (module default `admin` / `Connectivity`). Replies are `{"data":"<hex>"}`, multi-byte values
  little-endian. Firmware from 2023 ignores requests that come faster than about every 10 s;
  the driver keeps an 11 s gap, so a full read takes about a minute and the screen fills in as
  values arrive.
* **Read:** device type `FF` (decides the model and what is offered), firmware `01`, hardness
  unit `23`, target hardness `51`, salt mass and range `56`, salt warning `57`, total water `28`,
  soft water `29`.
* **Write (read back):** target hardness `30` (1 byte °dH, offered only when the unit is °dH),
  salt stock `56` (grams), salt warning `57` (days).
* **Actions (no read-back exists):** regeneration `350000`; leak-protection valve close `3C00` /
  open `3D00`, offered only on types documented "mit Leckageschutz" (i-soft SAFE+, i-soft K SAFE+,
  i-soft PRO 0x4B). SOFTwell is monitoring only. Vacation mode is not offered: its byte also
  carries leak-protection flags that cannot be read first.
* **Wi-Fi setup:** after power-up the module opens `Connectivity-…` for about 10 minutes; its
  setup page is at `http://192.168.4.1`.

## BWT Perla One / Duplex / PerlaMAXX

* **Source:** community library dkarv/bwt_api and integration dkarv/ha-bwt-perla (MIT); BWT's
  manual for the Local API switch.
* **Transport:** `GET http://<host>:8080/api/GetCurrentData` with basic auth `user:<login code>`
  (code from the registration email; *Settings → General → Connection → Local API* on). A wrong
  code is answered with an empty 404.
* **Read only.** Capacity per column (`CapacityColumnN_ml_dH / (in − out) / 1000` = litres),
  salt level and days, flow, treated volumes converted to blended water, hardness in/out,
  regeneration count, errors (`ActiveErrorIDs`), holiday and out-of-service flags.
* **Wi-Fi setup:** on the touchscreen only (*Settings → General → Wi-Fi connection*).

## Grünbeck softliQ SC / MC

* **Source:** community integrations tizianodeg/gruenbeck_softliQ_SC (MIT) and
  OhmegaStar/gruenbeck_softliq_mc-homeassistant. Only keys both describe the same way are
  relied on; SC-only keys are shown when present.
* **Transport:** `POST http://<host>/mux_http`, form body
  `id=2444&[code=<n>&][edit=<key>>value&]show=<key>|<key>~`, XML reply with one element per key.
* **Read:** flow `D_A_1_1`, maintenance days `D_A_2_2`, salt range `D_A_2_3`, raw hardness
  `D_D_1`, operating mode `D_C_5_1`, firmware `D_Y_6`; SC: regeneration step `D_Y_5`, system type
  `D_F_4` (code 290), last error `D_K_10_1` (code 245).
* **Write:** operating mode `D_C_5_1` (echo plus fresh read); manual regeneration `D_B_1>1`
  (confirmed when a regeneration step appears, otherwise reported as accepted).
* **Wi-Fi setup:** the controller's own Wi-Fi `softliQ:SC_…` (8-digit password) with its setup
  page at `http://192.168.0.1`.

## SYR NeoSoft 2500 / 5000 Connect

* **Source:** SYR's public local API documentation (iotsyrpublicapi.z1.web.core.windows.net).
  Firmware quirks from the community integration alexhass/syr_connect (MIT).
* **Transport:** `GET http://<host>:5333/neosoft/get/all` and `/neosoft/set/<key>/<value>`, no
  login. Set paths are lower case and values literal (`/set/rtm/02:30`); only characters that
  cannot appear in a path are percent-encoded. Values arrive as numbers or strings.
* **Read:** reserve capacity `RE1`/`RE2`, salt `SV1`, salt weeks `SS1`, flow `FLO`, volume `VOL`,
  hardness `IWH`/`OWH`/`WHU`, regeneration `RG1`, alarms/warnings/notifications `ALA`/`WRN`/`NOT`,
  next maintenance `SRV`, last regeneration `LAR`.
* **Write (read back):** regeneration mode `RMO` (1–4); on the single-tank 2500 also interval
  `RPD` (1–3 days) and time `RTM`. Regeneration start is not offered: it is not in SYR's NeoSoft
  documentation.
* **Wi-Fi setup in the app:** on the softener's access point (the phone's gateway), `set/wfk`
  (key) and then `set/wfc` (SSID), as SYR documents, then `get/wfs` until 2 and `get/wip` for the
  new address. The Wi-Fi password is sent only to the softener and never stored or logged.

## Not supported, by design or for now

Cloud-only: EcoWater/iQua (Rheem, Whirlpool, Kenmore), Culligan Connect, Pentair/Erie IQSoft,
RainSoft, SYR LEX Plus and other Connect models without the local API, Grünbeck softliQ:SD.
Bluetooth: Chandler Systems / Culligan CS Meter Soft, BWT AQA Perla. No connectivity: Clack,
Fleck and similar valves (community DIY sensors only). The in-app compatibility guide lists them
with links.
