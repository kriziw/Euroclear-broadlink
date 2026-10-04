# Controller compatibility and official portfolio review

Reviewed 2026-10-04. Product documentation and local protocol compatibility are
different kinds of evidence. This review adds no guessed controller codes or commands.

## Runxin portfolio

The [official manual directory](https://run-xin.com/en/category126.htm) separates
manual valves, automatic filter and softener valves, Q/P series, duplex systems,
floating-bed valves and other specialist products. It lists a dedicated Wi-Fi manual
for F79A/F79B/F82A/F82B LCD models and separate RS-485 supplements for P-series,
F111/F95/N77, F96/F112 and F74/F75. Interface availability depends on the variant.

| Family | Documentation establishes | App consequence |
|---|---|---|
| F79/F82 LCD Wi-Fi | A dedicated Wi-Fi product family | Candidate family; names are not field-1 protocol codes |
| F105/F136 | Wi-Fi-board-dependent functions and user settings | Candidate family; no new local mapping registered |
| P/Q and other automatic valves | Different interfaces and configurations | Do not assume the F79D map or BL3372 transport |
| Manual and filter valves | Distinct product categories | No automatic softener profile inferred |
| Duplex, floating-bed and specialist valves | Different system categories | Need their own proven transport and semantics |

The [manufacturer's download centre](https://manufacturervalve.com/download-center.html)
is linked as ETW from Runxin's official site. Its
[2023-05 catalogue](https://manufacturervalve.com/pdf/download-center_02.pdf), contents
spread, also separates manual/filter/softener, duplex, P/Q, residential D-series,
floating-bed and accessory families. It is a dated catalogue, not proof that every
current variant has the same interface.

The [official F105/F136 manual](https://manufacturervalve.com/pdf/download-center_22.pdf)
(0WRX.466.598), cover and printed pages 2, 13-15, identifies F105AHW,
F105BHW and F136BHW among its variants. Some functions require a Wi-Fi board.
It documents Water device pairing and settings including hardness 50-1500 mg/L,
continuous flow 0-120 minutes and shutoff flow 0-10 m³/h. These agree with the
existing app ranges but do not establish binary field IDs, model codes or command
encodings. No F105/F136 wire profile is added on that basis.

The F79/F82 Wi-Fi manual is listed in the official directory; its large PDF could
not be retrieved for full inspection. No additional claim relies on its contents.
The RS-485 supplements were identified in the directory, not audited as an
implementation specification. An RS-485 transport is outside this update.

## BroadLink portfolio and control boundaries

[BroadLink's official product downloads](https://www.ibroadlink.com/downloads)
cover remotes, smart plugs, lights and other product types. These are not Runxin
controllers simply because they use BroadLink connectivity.

The [official device-management SDK documentation](https://docs.ibroadlink.com/public/appsdk_en/appsdk_05/),
sections 2.6-2.7, distinguishes passthrough commands from product-specific scripts
and profiles associated with product IDs. It does not supply a Runxin field-1 model
table or establish that all BroadLink modules share the same controller commands.
This is evidence for separating the transport from the controller profile, not an
instruction to add the SDK or download unknown profiles.

The [SDK overview](https://docs.ibroadlink.com/public/appsdk_en/) requires a licence
for SDK integration. The app continues using its existing local protocol; no vendor
account, SDK, cloud dependency, BLE setup or firmware update is introduced.

## Automatic profile loading

1. Discovery identifies the module type. Only the existing Runxin type `0x520F`
   proceeds to Runxin commands; other BroadLink types show an unsupported message.
2. After authentication, a read-only field-1 query obtains the controller identity.
3. `ControllerProfiles` matches the module type plus controller code to a bundled
   profile. The selected profile determines the state query and optional fields.
4. The established `0x520F` + code `9` profile loads automatically. Its F79D mapping
   remains based on the existing hardware-verified ypsilon-local evidence documented
   in [DEVICE_PROTOCOL.md](DEVICE_PROTOCOL.md); it is not newly certified by Runxin.
5. An unmatched or missing controller code has no registered profile. Existing
   F79D fallback decoding is labelled experimental. Controls remain locked until
   the user explicitly confirms readings and opts in; no code means no opt-in.
6. An experimental opt-in is stored against the exact controller code. A changed
   identity, a different module type or an old unscoped unlock cannot enable it.
   The experimental warning remains visible and the user can relock controls.
7. A model change in a later state response resolves the profile again and clears
   cached optional fields. Device lifecycle/reconnection starts a new identification.

Profiles are bundled with app releases, not fetched from the Internet. Catalogue
names such as F79, F105 and F136 are deliberately not treated as numeric protocol
identities. Future profiles need a documented identity mapping, field encodings,
units, write ranges and confirmation behavior before registration. A manual saying
that a setting exists does not establish its network command.

## Bundled documentation library

`ControllerCatalogue` is a separate, searchable offline library, exposed in the
compatibility screen. It contains 81 entries: the existing F79D local profile and
80 reference-only entries. These comprise four explicitly
listed F79/F82 LCD Wi-Fi variants, seven F105/F136 variants with exact old/new
product aliases from the inspected manual cover, and the named D-series,
automatic-softener, duplex and Q/P variants in Runxin's official manual directory.
Manual filters, manual valves, unrelated BroadLink appliances and accessories are
not added as softener profiles. Product aliases are included only where inspected;
abbreviated directory names are expanded only when the suffix is explicitly shown.

Each entry records its source and evidence level. Directory-only entries explicitly
say that the individual manual and interface have not been audited. The F79/F82
entries establish only that the Wi-Fi manual is listed; the F105/F136 entries have
the inspected manual's shared settings summary, with the Wi-Fi-board caveat.
These reference entries have **no protocol profile link, field IDs, guessed model
code or write commands** and cannot participate in automatic profile resolution.
They help identify a printed model and locate its documentation, but cannot make
an unfamiliar connected controller trusted. The screen has **Supported** and
**Unverified** lists, with unverified variants grouped by controller family and
case-insensitive model/alias searches. Supported means a registered command mapping
is implemented in WaterCare; it is not a count of physically tested softener units.
The same profile applies to compatible products reporting its module/controller
identity. A family can contain both supported and unverified variants, and family
grouping never promotes the latter. Documentation sources are inside each entry.
The UI does not use tested/pending-validation tiers or request user testing/feedback.

The broader public protocol review found no additional executable Runxin profile.
[ypsilon-local's profile contribution guide](https://github.com/Danirv/ypsilon-local/blob/main/docs/adding-a-device-profile.md)
explicitly requires identity, field encoding and hardware evidence rather than
copying F79D's mapping under another name. Its
[Water device audit](https://github.com/Danirv/ypsilon-local/blob/main/docs/waterdevice-audit.md)
also records model-dependent codec behavior. Neither a catalogue number nor a
documented front-panel feature supplies the missing network specification.

## Validation

Simulated-controller tests check identity-first queries, automatic profile loading,
unknown-code fallback and model changes. Policy tests check locked defaults,
identity-scoped experimental access, legacy opt-ins and unrelated module types.
Existing protocol/write-reconciliation tests remain in place. Android lint and the
debug APK build validate integration; new physical controller support is not claimed.
