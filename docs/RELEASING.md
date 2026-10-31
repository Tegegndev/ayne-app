# Releasing Ayne

This project uses **GitHub Actions** to automatically build, test, and package release artifacts whenever a release tag is pushed.

---

## 1. How the GitHub Actions Workflow Works

The workflow file is located at [`.github/workflows/release.yml`](../.github/workflows/release.yml).

When triggered, GitHub Actions automatically:
1. Runs all unit tests (`./gradlew testDebugUnitTest`).
2. Configures Android signing (using repository secrets or generating an installable release key).
3. Builds both:
   - **`ayne-vX.Y.Z.apk`** (Universal APK ready to install directly on Android phones)
   - **`ayne-vX.Y.Z.aab`** (Android App Bundle for Google Play Store upload)
4. Calculates SHA-256 checksums in `checksums.txt` for file integrity verification.
5. Publishes a new **GitHub Release** with auto-generated release notes and attaches the APK and AAB files.

---

## 2. Creating a Release

### Option A: Using the Release Helper Script (Recommended)

From the project root:

```bash
# 1. Ensure working directory is clean
git status

# 2. Run the script with your target version:
./scripts/release.sh 1.0.0

# 3. Push the commit and tag to GitHub:
git push origin <your-branch> && git push origin v1.0.0
```

Or do it all in one step with `--push`:
```bash
./scripts/release.sh 1.0.0 --push
```

### Option B: Manual Git Commands

```bash
# 1. Update versionCode and versionName in app/build.gradle.kts
# 2. Commit the change
git commit -am "chore(release): bump version to v1.0.0"

# 3. Tag the commit with 'v' prefix
git tag -a v1.0.0 -m "Release v1.0.0"

# 4. Push tag to GitHub
git push origin v1.0.0
```

### Option C: Trigger from GitHub Web Interface

1. Go to your repository on GitHub.
2. Click **Actions** > **Build & Publish Release**.
3. Click **Run workflow**.
4. (Optional) Provide a tag name like `v1.0.0` and click **Run workflow**.

---

## 3. (Optional) Setting Up Production Play Store Signing

By default, the GitHub Action automatically creates an installable release APK.

If you have an official Google Play release keystore and want GitHub Actions to use it:

1. Encode your `.jks` or `.keystore` file to Base64:
   ```bash
   base64 -w 0 my-upload-key.jks > keystore_base64.txt
   ```
2. Go to **GitHub Repo** > **Settings** > **Secrets and variables** > **Actions**.
3. Add the following repository secrets:
   - `KEYSTORE_BASE64`: Paste the contents of `keystore_base64.txt`
   - `STORE_PASSWORD`: Keystore password
   - `KEY_PASSWORD`: Key password
   - `KEY_ALIAS`: Key alias (defaults to `upload` if omitted)
