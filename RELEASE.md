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
versionCode = 3        // must be higher than the last released build
versionName = "1.6"
```

`versionCode` is the only thing Android compares. It is not decoration: a build
that keeps the old number will not install over the old one. Releases 1.0 and
1.1 both shipped as `versionCode = 1`, which is exactly the mistake this line
is here to stop.

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
  --out Riyal-v1.6.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

Name the file `Riyal-v<version>.apk`. That is what every previous release used.

### 6. Get the code onto master and tag it

Releases are tagged on `master`, and nothing goes to `master` directly:

```bash
git push origin development
gh pr create --base master --head development --title "Release 1.6"
gh pr merge --merge
```

Then tag and push the tag:

```bash
git tag -a v1.6 -m "Riyal v1.6"
git push origin v1.6
```

### 7. Write the notes

One line per change, grouped by area. Lead with anything that costs the user
something - a wipe, a required uninstall, a permission - before the features.

### 8. Publish

```bash
gh release create v1.6 --title "Riyal v1.6" \
  --notes-file <notes>.md \
  Riyal-v1.6.apk
```

Add `--draft` to read it over on the site first, then publish from the browser.

### 9. Install it over the previous release

The only check that actually proves the signing is right:

```bash
adb install -r Riyal-v1.6.apk
```

`install -r` keeps the app's data. If Android says `INSTALL_FAILED_UPDATE_INCOMPATIBLE`,
the key is wrong - find that out here, not from someone whose records are gone.

## The signing key

Three different keys have signed a published Riyal, where one should have
signed all of them. Each change broke updates for everyone on the release
before it:

| Releases | Certificate | SHA-256 |
|---|---|---|
| 1.0, 1.1 | `CN=riyal, O=riyal, OU=riyal` | `13:83:5D:4C:...:88:9C` |
| 1.5 | `C=riyal, ST=riyal, L=riyal, CN=riyal` | `d1:98:f7:b7:...:bb:c2` |
| 1.51 onward | `CN=riyal, OU=riyal, O=riyal, L=Muscat, ST=Muscat, C=OM` | `E5:C4:F9:55:...:50:6B` |

The current key lives at `/home/linuxbrew/riyal-release.jks`, alias `riyal`,
RSA 4096, valid to January 2054. **It is the only one whose password is known.**
The 1.0/1.1 keystore was never on this machine and the 1.5 one cannot be opened,
which is why 1.51 starts a third lineage: users of 1.5 must uninstall before
they can install 1.51, and they lose their hand-filed categories doing it.

That is the whole cost of a lost keystore, paid twice. Do not let it happen to
this one.

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
