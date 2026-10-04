# Third-party notices

This app is licensed under GPL-3.0 (see [LICENSE](LICENSE)). It contains or is derived from
the following third-party work.

## ypsilon-local (Apache License 2.0)

<https://github.com/Danirv/ypsilon-local>. Licence text: [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

These files are Kotlin ports of, or are based on, ypsilon-local's Python code. They are
modified: translated to Kotlin, restructured for Android, and limited to the
hardware-verified write surface.

| This project | Derived from |
|---|---|
| `app/src/main/java/.../runxin/RunxinFrames.kt` | `custom_components/ypsilon_local/runxin/framing.py` |
| `app/src/main/java/.../runxin/F79d.kt` | `runxin/fields.py`, `runxin/f79d.py`, `runxin/semantics.py` |
| `app/src/main/java/.../runxin/SoftenerClient.kt` | `transport/broadlink_bl3372.py` (TFB envelope, retry policy), `coordinator.py` (write reconciliation) |

ypsilon-local's NOTICE file reads:

> Ypsilon for Home Assistant
> Copyright 2026 Ypsilon contributors
>
> This project is an independent community integration created for interoperability
> with compatible water softeners. It is not affiliated with, endorsed by, or
> sponsored by ATH, BWT, Wenzhou Runxin Valve, BroadLink, or their affiliates.
>
> ATH, BWT, Runxin, BroadLink, Ypsilon, and any other referenced product or company
> names may be trademarks of their respective owners. They are used only to identify
> compatible hardware.

## python-broadlink (MIT)

<https://github.com/mjg59/python-broadlink>, Copyright (c) 2014 Mike Ryan, Copyright (c) 2016 Matthew Garrett.

The BroadLink packet formats (AP setup, discovery, authentication, encrypted commands) were
re-implemented in Kotlin from this library's documented behaviour. The scripts in `tools/`
*run* python-broadlink's own code to produce test vectors; that code is not included in the
app.

## Material icons (Apache License 2.0)

The vector icons in `app/src/main/res/drawable/ic_*.xml` are Google Material icons.
Licence text: [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt).

## Trademarks

Euro-Clear, Runxin, BroadLink and other names are trademarks of their respective owners and
are used only to identify compatible hardware. This project is not affiliated with any of them.
