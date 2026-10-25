#!/bin/zsh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MACOS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ROOT_DIR="$(cd "${MACOS_DIR}/.." && pwd)"
cd "${MACOS_DIR}"

APP_NAME="ArtikelMeister"
BUNDLE_ID="com.artikelmeister.macos"
BUILD_DIR="build"
APP_BUNDLE="${BUILD_DIR}/${APP_NAME}.app"
DMG_STAGING="${BUILD_DIR}/dmg_staging"
DMG_OUTPUT="${APP_NAME}.dmg"

rm -rf "${BUILD_DIR}" "${DMG_OUTPUT}"
mkdir -p "${APP_BUNDLE}/Contents/MacOS"
mkdir -p "${APP_BUNDLE}/Contents/Resources"
mkdir -p "${BUILD_DIR}/ModuleCache"

if [ -d "/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk" ]; then
    SDK_PATH="/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk"
else
    SDK_PATH="$(xcrun --show-sdk-path --sdk macosx)"
fi

echo "[1/4] Generating AppIcon.icns..."
swiftc -sdk "${SDK_PATH}" -module-cache-path "${BUILD_DIR}/ModuleCache" scripts/generate_icon.swift -o "${BUILD_DIR}/generate_icon"
"${BUILD_DIR}/generate_icon" "${BUILD_DIR}/AppIcon.iconset"
iconutil -c icns "${BUILD_DIR}/AppIcon.iconset" -o "${APP_BUNDLE}/Contents/Resources/AppIcon.icns"

echo "[2/4] Creating Info.plist..."
cat > "${APP_BUNDLE}/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>tr</string>
    <key>CFBundleExecutable</key>
    <string>${APP_NAME}</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>${BUNDLE_ID}</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>${APP_NAME}</string>
    <key>CFBundleDisplayName</key>
    <string>${APP_NAME}</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

if [ -d "/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk" ]; then
    SDK_PATH="/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk"
else
    SDK_PATH="$(xcrun --show-sdk-path --sdk macosx)"
fi

echo "[3/4] Compiling Universal macOS binary (arm64 + x86_64) using ${SDK_PATH}..."
SOURCES=(
    Sources/Models.swift
    Sources/SpeechManager.swift
    Sources/StorageManager.swift
    Sources/GeminiService.swift
    Sources/AIGeneratorSheet.swift
    Sources/SplashView.swift
    Sources/StudyView.swift
    Sources/WordListView.swift
    Sources/StatsAndSettingsView.swift
    Sources/App.swift
)

swiftc \
    -O \
    -target arm64-apple-macosx14.0 \
    -module-cache-path "${BUILD_DIR}/ModuleCache" \
    -sdk "${SDK_PATH}" \
    "${SOURCES[@]}" \
    -o "${BUILD_DIR}/${APP_NAME}_arm64"

swiftc \
    -O \
    -target x86_64-apple-macosx14.0 \
    -module-cache-path "${BUILD_DIR}/ModuleCache" \
    -sdk "${SDK_PATH}" \
    "${SOURCES[@]}" \
    -o "${BUILD_DIR}/${APP_NAME}_x86_64"

lipo -create \
    "${BUILD_DIR}/${APP_NAME}_arm64" \
    "${BUILD_DIR}/${APP_NAME}_x86_64" \
    -output "${APP_BUNDLE}/Contents/MacOS/${APP_NAME}"

# Strip extended attributes and ad-hoc code sign the clean app bundle
xattr -cr "${APP_BUNDLE}" || true
codesign --force --deep --sign - "${APP_BUNDLE}"

echo "[4/4] Creating ${DMG_OUTPUT} installer..."
mkdir -p "${DMG_STAGING}"
cp -R "${APP_BUNDLE}" "${DMG_STAGING}/"
[ -f "${ROOT_DIR}/shared/samples/examplesheet.csv" ] && cp "${ROOT_DIR}/shared/samples/examplesheet.csv" "${DMG_STAGING}/" || true
[ -f "${ROOT_DIR}/shared/samples/wortschatzkapitel7-8.csv" ] && cp "${ROOT_DIR}/shared/samples/wortschatzkapitel7-8.csv" "${DMG_STAGING}/" || true
ln -s /Applications "${DMG_STAGING}/Applications"

hdiutil create \
    -volname "${APP_NAME}" \
    -srcfolder "${DMG_STAGING}" \
    -ov \
    -format UDZO \
    "${DMG_OUTPUT}"

echo "Done! Created ${DMG_OUTPUT} and ${APP_BUNDLE}"
