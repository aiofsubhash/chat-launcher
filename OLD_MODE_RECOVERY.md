# Old mode 0.5.34 candidate, source recovery

This branch is a recovery checkpoint. Its expanded source tree is still the old
wip-device-feedback tree, not the release source. Do not build that expanded tree.
The full runnable candidate source is encoded in the three files below because
the website's binary-upload handler failed. No proxy-deploy branch changes were made.

Restore in this order:

```
cat release-recovery/0.5.34/release-recovery/0.5.34/source.part1.b64 source.part2.b64 source.part3.b64 | base64 -d > source.tar.gz
sha256sum source.tar.gz
tar -xzf source.tar.gz
cd repo
```

Expected SHA256:
d3f0193c57a8b1c8977b480013e710a8da219c716a5b95c2cc62828543067634

Complete archive also backed up privately in Drive:
https://drive.google.com/file/d/1w3lMQ0pxIZwBzrvP2meSsySKpbN5NJQ3/view?usp=drivesdk&authuser=aiofsubhash%40gmail.com

## Candidate

Version0.5.34, code39, package com.subhash.chatlauncher.
Approved green LCD, multi-tap pad, filtered installed apps, delete and Send.
Settings > Home > Launcher style selects Old mode. MENU returns to Normal, opens
Settings or Fresh chat. Existing chat/router/history/profile behavior reused.
No network throttling, VPN, new Android permissions, paid services or backend edits.
New analytics records appearance changes and type/delete/commit actions, no query
text or specific character keys.

All58 test classes run across bounded batches:414 tests,412 pass,1 opt-in live test
skipped,1 inherited HistoryDisclosure failure at line82. Independent original0.5.33
archive baseline reproduces that same assertion failure. All8 new old-mode tests
pass. Native renders at411dp and320dp inspected, the small-screen key-label line
height was fixed after inspection. Physical phone installation, OEM behavior and
actual app activities are not yet verified.

## Signing and delivery

At checkpoint, final signing approval is pending. This is not a delivered release.
Never generate a new key or uninstall the existing app. The debug variant now has
no default signing config, preventing a fresh-machine default-key mismatch.
Final APK must be signed explicitly using the existing saved keystore and verified
against certificate SHA256:
3b1837cd6aab0c03bd0da2dc859df2fb3fa64ad70c537678252bb61edc49de6d

Preserve applicationId and code39 for install-over. Normal mode and local stores
are not migrated or cleared. Use private Drive delivery, not a GitHub APK asset.

Build: JDK17, Gradle8.9, Android SDK34. Configure local.properties with sdk.dir.
assembleDebug needs around768MiB heap for D8 here. Stop the idle build daemon before
running large native-render test batches with384MiB daemon heap and384MiB test heap.
No wrapper binary in source. Tests and renders are not physical-device proof.

## Publication cleanup

The recovery archive was sanitized for publication to remove a personal delivery
email. Its checksum above is for this sanitized archive. Project source and work
notes otherwise remain at the existing checkpoint, not the later APK release.
