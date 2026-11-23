Fake SMS Prank - local notification prank (nothing is sent over any network).

Build: push to main on GitHub. The APK appears under Releases (tag v<version>-b<run>).
Phone: app > Check for updates > Update > Install.

IMPORTANT (one time): Android only installs an update over the old app if both are signed with the SAME key.
GitHub's debug key changes, so make your own key and add 4 secrets
(Settings > Secrets and variables > Actions): KEYSTORE_B64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
Make the key on any PC with Java:
  keytool -genkeypair -v -keystore my.jks -alias prank -keyalg RSA -keysize 2048 -validity 10000
  base64 -w0 my.jks      (paste this output as KEYSTORE_B64)

Bump version: edit ONLY  def appVersionName = "..."  in app/build.gradle (and put the same number on the first line of CHANGELOG.txt).
Upload a code zip: name it like  anything_v1_0_1.zip , put it in the repo root, push. The workflow applies it, builds, releases.
Revert: Actions > Build APK > Run workflow > revert_to = previous | latest | tag | commit.
