# Repository APK updates

The app follows TaskBandit's user-facing flow: discover a GitHub release, offer an
in-app download, then use Android's installer. This implementation is independent
and scoped to `kriziw/Euroclear-broadlink`.

## Discovery and user choice

An activity-scoped update view model checks once on launch when automatic checking
is enabled (the default). No polling service, background notification or download
starts just because a release is published. The next launch discovers the release.
Manual checks are available from Home → Settings → App updates. The automatic-check preference
and dismissed version are saved in app-private preferences, excluded from backup.
Disabling automatic checks avoids future launch requests; an already running request
may finish, but cannot show a notice after automatic checks have been disabled.
Manual checks ignore the dismissed-version marker.

The public, unauthenticated GitHub releases API returns up to 100 recent releases.
Only non-draft, non-prerelease `X.Y.Z` or `vX.Y.Z` tags with a strictly greater Android
version code are eligible. Comparison matches the build's `major*1000000 + minor*1000
+ patch` scheme and rejects out-of-range components. An eligible release must have
exactly one uploaded `bl3372-wifi-setup-X.Y.Z.apk`, with the canonical repository
download URL and an expected size between 1 byte and 100 MiB. It must provide a
SHA-256 asset digest or an uploaded matching `.sha256` sidecar. The highest eligible
version wins independently of release-list order. This allows release-please to
publish metadata before its separate signing/upload job finishes without offering
a missing APK or hiding an older eligible update.

## Download and installation

The downloader uses HTTPS with bounded connection/read timeouts, response sizes and
redirect counts. Redirects may only use GitHub's API, repository or release storage
hosts. It writes `cache/updates/update.part`, checks the exact byte count and SHA-256,
and renames a verified file to `update.apk`. Incomplete downloads are deleted. A
sidecar must contain one SHA-256 and the exact expected APK filename. Downloads
run on an IO dispatcher and support progress, cancellation and retry.

Before installation the app checks the archive's application ID, version name/code
and signer against this repository's release certificate. It also requires the
installed app to have that certificate, so a debug installation is explained as
incompatible. The certificate pin matches `.github/workflows/release.yml`:

`d49dc49229e6e7b900439aee820169ffd8ba13b0704a409fabed849777790a0b`

A non-exported FileProvider exposes only the private updates cache through a
temporary read-granted content URI. The app requests Android's permission to install
from this source if needed, rechecks permission after returning, and opens the
system installer. Android performs its own signature/installation checks and asks
for user confirmation. A cancelled installer can be reopened using Install update.
Activity recreation retains state; process death requires rechecking/redownloading
rather than trusting an old cached APK. No silent install is attempted.

The provider declares `android.support.FILE_PROVIDER_PATHS` in the manifest. This
is essential with AndroidX Core's lazy path initialization: `getUriForFile` resolves
the manifest before a provider instance has initialized its path strategy, so a
constructor-only XML resource is insufficient. The installer intent grants read
access through both its URI flag and ClipData. A regression test checks the manifest
metadata and its narrowly scoped updates cache path.

## Network and privacy boundaries

Local controller sockets remain bound to their specific Wi-Fi network. Update HTTP
requests use Android's default network, which may be cellular when connected to a
controller access point without Internet. Checks send only ordinary HTTPS request
metadata and a fixed app User-Agent; there are no account tokens, controller IDs,
settings, Wi-Fi passwords or telemetry. GitHub and its storage servers see normal
network request information such as the source IP. Turning off automatic checks
does not disable explicit manual checks or downloads. Setup/control remains usable
without Internet. Privacy text in the manifest, UI and README reflects the change.

## Validation and limits

Unit tests cover numeric version order, prerelease/draft filtering, equal/older
versions, late APK uploads, duplicate/foreign/misnamed/oversized assets, checksum
fallback and sidecar filename binding, allowed redirect hosts, successful downloads,
HTTP failures, corrupt/short/oversized downloads and partial-file cleanup. Android
lint, debug compilation and release shrinking check integration.

The actual Android install-source permission, FileProvider/installer interaction,
certificate inspection and successful upgrade retaining saved devices require
physical-device validation with an official signed release. A debug APK cannot
exercise a successful signed-release upgrade. Rate limiting, offline status or an
unavailable API produces a retryable check error; automatic failures do not show
a launch dialog. The UI does not claim a future release exists until an eligible
APK appears in the response.

## Sources

- [TaskBandit](https://github.com/kriziw/TaskBandit), Android update discovery/download flow
- [GitHub Releases API](https://docs.github.com/en/rest/releases/releases)
- [Android FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [Android package installation](https://developer.android.com/reference/android/content/pm/PackageInstaller)
- [Install-source permission](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())
