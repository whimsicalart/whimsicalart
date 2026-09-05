#!/bin/sh

set -e

if ! command -v docker > /dev/null; then
  printf '!! Command docker not found!\n' >&2
  exit
fi

dir="$(dirname "$0")/.."
package=$(
  sed -E '
/^\s*applicationId\s*=/!d
s/^\s*applicationId\s*=\s*"?([^" ]+)"?\s*$/\1/
' "$dir/app/build.gradle.kts"
)
name="${package##*.}"
if [ -z "$name" ]; then
  name=$(basename "$(cd "$dir"; pwd)")
fi

skin=1080x2340
dpi=440
with_vnc=0
action=start
while [ $# -gt 0 ]; do
  case "$1" in
  tablet)
    skin=1600x2560
    dpi=320
  ;;
  phone)
    skin=1080x2340
    dpi=440
  ;;
  pocket|small)
    skin=720x1280
    dpi=320
  ;;
  vnc)
    with_vnc=1
  ;;
  stop)
    action=stop
  ;;
  *)
    printf '!! Unknown argument "%s" (expected: notest | nolint)\n' "$1" >&2
    exit 1
  ;;
  esac
  shift
done

printf '==> Checking Docker daemon\n'
if ! timeout 20 docker ps >/dev/null 2>&1; then
  printf '!! Docker daemon not reachable. Start dockerd on the host (or reboot if the host locked up).\n' >&2
  exit 1
fi

if [ "$action" = stop ]; then
  if [ -z "$(docker ps -qaf "name=${name}-emu")" ]; then
    printf '>> No container running.\n'
    exit
  fi
  if [ -n "$(docker ps -qf "name=${name}-emu")" ]; then
    printf '==> Gracefully shutting down the emulator (adb emu kill)...\n'
    docker exec "${name}-emu" adb emu kill >/dev/null 2>&1 ||
      docker exec "${name}-emu" adb shell reboot -p >/dev/null 2>&1 || :
    i=0
    while [ "$i" -lt 60 ]; do
      if [ -z "$(docker ps -qf "name=${name}-emu")" ]; then
        printf '    emulator exited after ~%ss\n' "$i"
        break
      fi
      sleep 1
      i=$((i + 1))
    done
  fi
  docker stop -t 10 "${name}-emu" >/dev/null 2>&1 || :
  docker rm "${name}-emu" >/dev/null 2>&1 || :
  printf '>> Emulator shut down.\n'
  exit
fi

_file="$dir/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$_file" ]; then
  printf '!! No APK found. Build first (files: e.g. scripts/build.sh)\n' >&2
  exit 1
fi

printf '==> Checking image\n'
if ! docker image inspect android-emu >/dev/null 2>&1; then
  printf '!! Image not found. Building it (this downloads the Android SDK, may take a while)...\n' >&2
  did=$(
docker create \
  --pull=always \
  -i \
  -u 0:0 \
  --entrypoint '' \
  eclipse-temurin:17-jdk \
  sh -ec "$(
cat <<'EOF'
mkdir -p /opt/android-sdk/cmdline-tools
cd /opt/android-sdk
wget -qO cmd.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir out
cd out
jar xf ../cmd.zip
cd ..
mv -T out/cmdline-tools cmdline-tools/latest
rmdir out
chmod a+x cmdline-tools/latest/bin/sdkmanager cmdline-tools/latest/bin/avdmanager
yes | cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1
cmdline-tools/latest/bin/sdkmanager \
  platform-tools \
  emulator \
  'system-images;android-26;default;x86_64'
# FIXME: coreutils fails: install -o 2000 -g 2000 -m 755 -d /home/build
mkdir /home/build
chown 2000:2000 /home/build
# FIXME: coreutils fails: nsenter --setuid=2000 --setgid=2000 --no-fork sh -es
HOME=/home/build setpriv --reuid=2000 --regid=2000 --clear-groups sh -es <<'CMD'
printf 'no\n' |
  cmdline-tools/latest/bin/avdmanager create avd -n app -k 'system-images;android-26;default;x86_64' -f
sed -Ei 's|^disk\.dataPartition\.size\s*=.*|disk.dataPartition.size = 1610612736|' "$HOME/.android/avd/app.avd/config.ini" # 1536*1024*1024
mkdir -p "$HOME/.config/Android Open Source Project"
cat > "$HOME/.config/Android Open Source Project/Emulator.conf" <<'FILE'
[General]
showCompatibilityWarning_app=false
showNestedWarning=false
[set]
autoFindAdb=true
clipboardSharing=true
crashReportPreference=0
disableMouseWheel=false
disablePinchToZoom=false
savePath=/home/build/Desktop
FILE
CMD
rm -rf cmdline-tools .temp cmd.zip /root/.android /home/build/.android/cache
install -o 0 -g 0 -m 755 /dev/stdin /usr/local/bin/emulator-run <<'FILE'
#!/bin/sh

set -e

skin=$1
dpi=$2
vnc=$3

set --
if [ "$vnc" = vnc ]; then
  export DISPLAY=:99
  width="${skin%x*}"
  height="${skin#*x}"
  nohup setsid Xvfb :99 -screen 0 "$((width + 52))x${height}x24" -nolisten tcp >/dev/null 2>&1 < /dev/null &
  sleep 1
  nohup setsid x11vnc -display :99 -rfbport 5900 -forever -shared -nopw -quiet >/dev/null 2>&1 < /dev/null &
  sleep 1
  nohup setsid sh -ec "$(
cat <<'CMD'
sleep 2
timeout 60 adb wait-for-device || :
wid=$(timeout 30 xdotool search --sync --onlyvisible --name 'Android Emulator' 2>/dev/null | head -1)
if [ -n "$wid" ]; then
  sleep 5
  xdotool windowsize --sync "$wid" "$1" "$2" 2>/dev/null || :
  xdotool windowmove --sync "$wid" 0 0 2>/dev/null || :
fi
CMD
)" - "$width" "$height" >/dev/null 2>&1 < /dev/null &
else
  set -- -no-window
fi

accel=off
if [ -r /dev/kvm ]; then
  accel=auto
fi

adb start-server >/dev/null 2>&1 || :

exec emulator -avd app \
  "$@" \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -wipe-data \
  -no-metrics \
  -partition-size 1536 \
  -gpu software \
  -accel "$accel" \
  -memory 1536 \
  -skin "$skin" \
  -dpi-device "$dpi"
FILE
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
  libgl1 \
  libegl1 \
  libx11-6 \
  libx11-xcb1 \
  libxcb1 \
  libxcomposite1 \
  libxcursor1 \
  libxdamage1 \
  libxext6 \
  libxi6 \
  libxrandr2 \
  libxrender1 \
  libxtst6 \
  libnss3 \
  libnspr4 \
  libpulse0 \
  libosmesa6 \
  libcups2 \
  libdbus-1-3 \
  libfontconfig1 \
  libfreetype6 \
  xvfb \
  x11vnc \
  xauth \
  xdotool \
  x11-utils
# apt-get autoclean
rm -rf /var/lib/apt/lists /var/cache/apt /var/lib/dpkg/lock /var/lib/dpkg/updates/*
EOF
)" 2> /dev/null
)
  docker start -ai "$did"
  docker commit \
    --change 'ENV ANDROID_HOME=/opt/android-sdk' \
    --change 'ENV HOME=/home/build' \
    --change 'ENV ANDROID_SDK_HOME=/home/build/.android' \
    --change 'ENV PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/opt/android-sdk/emulator:/opt/android-sdk/platform-tools' \
    --change 'USER 2000:2000' \
    --change 'WORKDIR /home/build' \
    --change 'ENTRYPOINT ["emulator-run"]' \
    --change 'CMD []' \
    "$did" \
    android-emu > /dev/null
  docker rm "$did" > /dev/null
  docker images -qf dangling=true | xargs -r docker rmi
fi

if [ -n "$(docker ps -qaf "name=${name}-emu")" ]; then
  printf '>> Emulator shut down.\n'
  docker rm -f "${name}-emu" >/dev/null 2>&1 || :
fi

set --
if docker run --rm --device /dev/kvm --entrypoint '' android-emu test -c /dev/kvm 2>/dev/null; then
  if ! docker run --rm --device /dev/kvm --entrypoint '' android-emu test -r /dev/kvm 2>/dev/null; then
    docker run --rm --device /dev/kvm --entrypoint '' -u 0:0 android-emu sh -ec '
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -yqq acl
setfacl -m u:2000:rw /dev/kvm
'
  fi
  set -- --device /dev/kvm
fi

if [ $with_vnc -ne 0 ]; then
  set -- "$@" -p 5900:5900
fi

set -- "$@" android-emu "$skin" "$dpi"

if [ $with_vnc -ne 0 ]; then
  set -- "$@" vnc
fi

printf '==> Starting emulator container\n'
docker run -d --name "${name}-emu" "$@" >/dev/null

printf '==> Waiting for the emulator to boot (up to 600s)...\n'
boot=0
elapsed=0
while [ "$elapsed" -lt 600 ]; do
  boot=$(
    docker exec "${name}-emu" adb wait-for-device shell getprop sys.boot_completed 2>/dev/null
  )
  if [ "$boot" = 1 ]; then
    printf '    emulator booted after ~%ss\n' "$elapsed"
    break
  fi
  sleep 5
  elapsed=$((elapsed + 5))
  if [ $((elapsed % 30)) -eq 0 ]; then
    printf '    ...still booting (%ss of 600s)\n' "$elapsed"
  fi
done

if [ "$boot" -ne 1 ]; then
  printf '!! Emulator did not finish booting in 600s. Stopping to free memory.\n' >&2
  sh "$0" stop || :
  exit 1
fi

printf '==> Installing APK\n'
docker exec -i "${name}-emu" sh -ec '
cat > /tmp/app.apk
adb install -r /tmp/app.apk
rm -f /tmp/app.apk
' < "$_file"

printf '==> Granting app runtime permissions (avoids first-launch prompt)\n'
docker exec "${name}-emu" sh -ec "$(
cat <<'EOF'
pkg=$1
adb shell dumpsys package "$pkg" |
  grep -oE 'android.permission.[A-Z_]+' |
  sort -u |
  while IFS= read -r perm; do
    adb shell -Tn pm grant "$pkg" "$perm" >/dev/null 2>&1 < /dev/null || :
  done
EOF
)" - "$package.debug"

_dir="$dir/scripts/assets"
if [ -d "$_dir" ]; then
  while IFS= read -r file; do
    if [ -z "$file" -o ! -f "$file" ]; then
      continue
    fi
    _name=$(basename "$file")
    printf '==> Copying example image "%s" to the device gallery\n' "$_name"
    docker exec -i "${name}-emu" sh -ec '
name=$1
cat > "/tmp/$name"
adb shell mkdir -p /sdcard/Pictures
adb push "/tmp/$name" "/sdcard/Pictures/$name" >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Pictures/$name"
rm -f "/tmp/$name"
' - "$_name" < "$file" >/dev/null
  done <<EOF
$(
  find "$_dir" -mindepth 1 -type f -print
)
EOF
fi

activity=$(
sed -E '
/<activity/,/^[^>]*>/!d
/android:name="/!d
s/^.*android:name="([^"]*).*$/\1/
' "$dir/app/src/main/AndroidManifest.xml"
)
printf '==> Launching %s/%s\n' "$package.debug" "$package$activity"
docker exec "${name}-emu" adb shell am start -n "$package.debug/$package$activity" >/dev/null

printf '==> DONE\n'
