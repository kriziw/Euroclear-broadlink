# Changelog

## 0.1.0 (2026-10-04)

### Features

* Local Wi-Fi provisioning of the BroadLink BL3372 module over its `WiFi-BL3372` access point, compatible with python-broadlink's `setup()`.
* Socket bound to the Wi-Fi network so the setup packet never leaves over mobile data; Android 17 local-network permission support.
* BroadLink LAN discovery after provisioning.
