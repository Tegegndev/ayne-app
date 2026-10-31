#!/usr/bin/env bash
set -e

# Ayne Release Helper Script
# Usage:
#   ./scripts/release.sh <version> [--push]
# Example:
#   ./scripts/release.sh 1.0.1
#   ./scripts/release.sh 1.0.1 --push

TARGET_VERSION="$1"
AUTO_PUSH=false

if [ "$2" = "--push" ] || [ "$1" = "--push" ]; then
  AUTO_PUSH=true
  if [ "$1" = "--push" ]; then
    TARGET_VERSION=""
  fi
fi

# Ensure git working tree is clean
if [ -n "$(git status --porcelain)" ]; then
  echo "Error: Working directory has uncommitted changes. Please commit or stash them first."
  git status --short
  exit 1
fi

GRADLE_FILE="app/build.gradle.kts"

# Extract current versionCode and versionName
CURRENT_CODE=$(grep -oE 'versionCode\s*=\s*[0-9]+' "$GRADLE_FILE" | grep -oE '[0-9]+')
CURRENT_NAME=$(grep -oE 'versionName\s*=\s*"[^"]+"' "$GRADLE_FILE" | sed -E 's/versionName\s*=\s*"([^"]+)"/\1/')

echo "======================================"
echo "Ayne Release Preparer"
echo "Current Version: $CURRENT_NAME (code: $CURRENT_CODE)"
echo "======================================"

if [ -z "$TARGET_VERSION" ]; then
  read -r -p "Enter new version (e.g. 1.0.1): " TARGET_VERSION
fi

# Clean version (strip leading 'v' if present)
VERSION="${TARGET_VERSION#v}"
TAG="v$VERSION"
NEW_CODE=$((CURRENT_CODE + 1))

if [ -z "$VERSION" ]; then
  echo "Error: Version cannot be empty."
  exit 1
fi

echo "Updating $GRADLE_FILE to version $VERSION ($NEW_CODE)..."

# Replace versionCode and versionName
sed -i -E "s/versionCode\s*=\s*[0-9]+/versionCode = $NEW_CODE/" "$GRADLE_FILE"
sed -i -E "s/versionName\s*=\s*\"[^\"]+\"/versionName = \"$VERSION\"/" "$GRADLE_FILE"

# Stage change and commit
git add "$GRADLE_FILE"
git commit -m "chore(release): bump version to $TAG"
git tag -a "$TAG" -m "Release $TAG"

CURRENT_BRANCH=$(git branch --show-current)

echo ""
echo "Successfully created release commit and tag: $TAG"
echo "To publish this release to GitHub Actions, run:"
echo ""
echo "  git push origin $CURRENT_BRANCH && git push origin $TAG"
echo ""

if [ "$AUTO_PUSH" = true ]; then
  echo "Auto-push requested. Pushing to origin..."
  git push origin "$CURRENT_BRANCH"
  git push origin "$TAG"
  echo "Pushed! Check your GitHub Actions tab to watch the build and release."
fi
