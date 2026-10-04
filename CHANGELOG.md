# Changelog

## [0.3.0](https://github.com/kriziw/Euroclear-broadlink/compare/v0.2.1...v0.3.0) (2026-10-04)


### Features

* identify Runxin controllers and add a documented profile library ([#7](https://github.com/kriziw/Euroclear-broadlink/issues/7)) ([d7eac5a](https://github.com/kriziw/Euroclear-broadlink/commit/d7eac5aaa152e61b1268d9468740078394d1b946))

## [0.2.1](https://github.com/kriziw/Euroclear-broadlink/compare/v0.2.0...v0.2.1) (2026-10-04)


### Bug Fixes

* let the user change the app language from within the app ([#5](https://github.com/kriziw/Euroclear-broadlink/issues/5)) ([cb4ab6a](https://github.com/kriziw/Euroclear-broadlink/commit/cb4ab6a8bcbc577280c78e0c11ce2d59fb46120c))

## [0.2.0](https://github.com/kriziw/Euroclear-broadlink/compare/v0.1.0...v0.2.0) (2026-10-04)


### Features

* automate releases with release-please and attach the signed APK ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* change hardware-verified settings (hardness, salt added, regeneration time, clock sync, SafeHOME limits) with read-back confirmation ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* connect by IP or scan a subnet for softeners on other VLANs ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* device dashboard with live status, water use, salt, alarms and diagnostics ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* Hungarian and English user interface ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* remember multiple devices and open the saved softener on launch ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* start a regeneration from the app ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))
* step-by-step setup wizard with the Midnight manual's setup-mode steps ([e097f47](https://github.com/kriziw/Euroclear-broadlink/commit/e097f4781841984027fcb922355ad9398aaaaac1))

## 0.1.0 (2026-10-04)

### Features

* Local Wi-Fi provisioning of the BroadLink BL3372 module over its `WiFi-BL3372` access point, compatible with python-broadlink's `setup()`.
* Socket bound to the Wi-Fi network so the setup packet never leaves over mobile data; Android 17 local-network permission support.
* BroadLink LAN discovery after provisioning.
