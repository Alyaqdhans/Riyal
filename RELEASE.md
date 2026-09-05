# Releasing Riyal

A checklist for cutting a release and putting it on GitHub. Every step here
exists because something went wrong without it once.

## Before you start

You need two things that are deliberately not in this repository:

- **The signing keystore.** Every published build must be signed with the same
  key or Android refuses to install it as an update. See [The signing key](#the-signing-key).
- **A GitHub login.** `gh auth login`, answering yes to authenticating Git as
  well; otherwise `git push` hangs on a credential prompt with no error.

## The checklist

### 1. Pick the version, and raise versionCode

In `app/build.gradle.kts`:

```kotlin
versionCode = 4        // must be higher than the last released build
versionName = "1.6.0"
```

`versionCode` is the only thing Android compares. It is not decoration: a build
that keeps the old number will not install over the old one. Releases 1.0 and
1.1 both shipped as `versionCode = 1`, which is exactly the mistake this line
is here to stop.

`versionName` is what the in-app update check compares, and it reads a tag as
the numbers it is made of - `1.51` is minor 51, not "5.1". So `1.6` is *older*
than `1.51` to the app, however it looks to a person. Give every version three
places from here on (`1.6.0`, `1.6.1`, `1.7.0`) and the two readings agree.
`VersionTest` asserts this, including the case that forced the rule.

The tag on GitHub and this `versionName` must be the same version, or the app
will keep offering an update to the release it is already running.

### 2. Merge what is going out

Feature branches live off `development`. Merge the ones that are finished:

```bash
git checkout development
git merge --no-ff feature/<name>
```

Check first that a merge is clean, before committing to it:

```bash
git merge-tree --write-tree development feature/<name> >/dev/null && echo clean
```

### 3. Prove it still works

```bash
./gradlew test assembleRelease
```

Tests must be green. Read the count, not just the exit code - a suite that
silently stopped running also passes.

### 4. Check the APK is what you think it is

```bash
SDK=~/Android/Sdk/build-tools/36.0.0
$SDK/aapt2 dump badging app/build/outputs/apk/release/*.apk | head -1
```

Confirm `versionCode` and `versionName` are the ones you set in step 1.

### 5. Sign it

With `riyal.storeFile` and its passwords in `local.properties`, step 3 already
produced `app-release.apk`, signed. Confirm who signed it:

```bash
$SDK/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Without a configured keystore the build produces `app-release-unsigned.apk`
instead - by design, so an unsignable build fails loudly rather than shipping
under the wrong key. Sign it by hand:

```bash
$SDK/apksigner sign --ks /path/to/riyal.jks \
  --out Riyal-v1.6.0.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Name the file `Riyal-v<version>.apk`. That is what every previous release used.

### 6. Get the code onto master and tag it

Releases are tagged on `master`, and nothing goes to `master` directly:

```bash
git push origin development
gh pr create --base master --head development --title "Release 1.6.0"
gh pr merge --merge
```

Then tag and push the tag:

```bash
git tag -a v1.6.0 -m "Riyal v1.6.0"
git push origin v1.6.0
```

### 7. Write the notes

One line per change, grouped by area. Lead with anything that costs the user
something - a wipe, a required uninstall, a permission - before the features.

### 8. Publish

```bash
gh release create v1.6.0 --title "Riyal v1.6.0" \
  --notes-file <notes>.md \
  Riyal-v1.6.0.apk
```

Add `--draft` to read it over on the site first, then publish from the browser.

### 9. Install it over the previous release

The only check that actually proves the signing is right:

```bash
adb install -r Riyal-v1.6.0.apk
```

`install -r` keeps the app's data. If Android says `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
the key is wrong - find that out here, not from someone whose records are gone.

## The signing key

Two keys have signed a published Riyal, where one should have signed both. The
change between 1.1 and 1.5 broke updates for everyone on 1.1:

| Releases | Keystore | Certificate | SHA-256 |
|---|---|---|---|
| 1.0, 1.1 | lost | `CN=riyal, O=riyal, OU=riyal` | `13:83:5D:4C:...:88:9C` |
| 1.5, 1.6.0 onward | `/home/linuxbrew/riyal.jks` | `C=riyal, ST=riyal, L=riyal, O=riyal, OU=riyal, CN=riyal` | `D1:98:F7:B7:...:BB:C2` |

**The release key is `/home/linuxbrew/riyal.jks`**, PKCS#12, alias `key0`,
valid to January 3026. It signed 1.5 and it signs everything after, so 1.6.0
installs over 1.5 as an ordinary update with the user's records intact.

There is a third keystore, `/home/linuxbrew/riyal-release.jks`, alias `riyal`,
`E5:C4:F9:55:...:50:6B`. It signed 1.51, which was never published, so nobody
is on it. It is not the release key. Building 1.6.0 with it would have forced
every 1.5 user to uninstall, and that was nearly shipped: check the fingerprint
in step 5 against the table above before publishing, every time.

The 1.0/1.1 keystore was never on this machine and its users are stranded.
That is the cost of a lost keystore, and it has been paid once. Back
`riyal.jks` up somewhere that survives losing this machine.

Check a keystore is the right one before trusting it:

```bash
keytool -list -v -keystore /path/to/riyal.jks | grep -i "SHA256:"
```

Point the build at it through `local.properties`, which is gitignored. The
keystore and its passwords must never be committed:

```properties
riyal.storeFile=/path/to/riyal.jks
riyal.storePassword=...
riyal.keyAlias=...
riyal.keyPassword=...
```

**If the key is ever lost**, no future build can update an installed copy of
the app. Everyone has to uninstall first, losing their records. Back it up
somewhere that survives losing this machine.

## Things worth knowing

**A schema bump wipes stored data.** `Store.loadLocked` deletes the file when
`v` does not match `SCHEMA_VERSION`, because transactions are rebuilt from the
inbox anyway. Rebuilding is cheap, but hand-filed categories and learned rules
are not in the inbox - they are gone. Any release that raises `SCHEMA_VERSION`
must say so at the top of its notes.

**The build caches are on.** `org.gradle.caching` and
`org.gradle.configuration-cache` in `gradle.properties` take a clean release
build from 64s to 2s when nothing changed. They do not alter the APK's
contents, only how the classes are packed between dex files.

**R8 is off.** `isMinifyEnabled = false`, so the APK is around 33 MB. Turning
it on would shrink that a lot, but it needs a full pass on a real device first
- reflection and JSON round-trips are where it usually breaks.
