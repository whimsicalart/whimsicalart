#!/bin/sh

set -e

if ! command -v docker > /dev/null; then
  printf '!! Command docker not found!\n' >&2
  exit
fi

dir="$(dirname "$0")/.."
name=$(
  sed -E '
/^\s*applicationId\s*=/!d
s@^\s*applicationId\s*=\s*"?([^".]*\.)*([^."]+)"?\s*$@\2@
' "$dir/app/build.gradle.kts"
)
if [ -z "$name" ]; then
  name=$(basename "$(cd "$dir"; pwd)")
fi

run_test=1
run_lint=1
while [ $# -gt 0 ]; do
  case "$1" in
  notest)
    run_test=0
  ;;
  nolint)
    run_lint=0
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

printf '==> Checking image\n'
if ! docker image inspect android-sdk >/dev/null 2>&1; then
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
# https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
# https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
wget -qO cmd.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
mkdir out
cd out
jar xf ../cmd.zip
cd ..
mv -T out/cmdline-tools cmdline-tools/latest
rmdir out
chmod a+x cmdline-tools/latest/bin/sdkmanager
yes | cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1
cmdline-tools/latest/bin/sdkmanager \
  platform-tools \
  'platforms;android-34' \
  'platforms;android-35' \
  'build-tools;34.0.0' \
  'build-tools;35.0.0'
rm -rf cmdline-tools .temp cmd.zip
# install -o 2000 -g 2000 -m 755 -d /home/build /workspace
mkdir /home/build /workspace
chown 2000:2000 /home/build /workspace
install -o 0 -g 0 -m 644 /dev/stdin /workspace/gradle.properties <<'FILE'
org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8
org.gradle.daemon=false
org.gradle.parallel=false
org.gradle.workers.max=1
kotlin.daemon.jvmargs=-Xmx768m
android.useAndroidX=true
android.nonTransitiveRClass=true
gradle.user.home=/home/build/.gradle
FILE
EOF
)" 2> /dev/null
)
  docker start -ai "$did"
  docker commit \
    --change 'ENV ANDROID_HOME=/opt/android-sdk' \
    --change 'ENV HOME=/home/build' \
    --change 'ENV GRADLE_USER_HOME=/home/build/.gradle' \
    --change 'ENV ANDROID_SDK_HOME=/home/build/.android' \
    --change 'USER 2000:2000' \
    --change 'WORKDIR /workspace' \
    --change 'ENTRYPOINT []' \
    --change 'CMD ["sleep", "infinity"]' \
    "$did" \
    android-sdk > /dev/null
  docker rm "$did" > /dev/null
  docker images -qf dangling=true | xargs -r docker rmi
fi

if [ -z "$(docker ps -qaf "name=${name}-build")" ]; then
  printf '==> Starting new build instance\n'
  docker run -d --name "${name}-build" android-sdk >/dev/null
elif [ -z "$(docker ps -qf "name=${name}-build")" ]; then
  printf '==> Restarting existing build instance\n'
  docker start "${name}-build"
fi

printf '==> Syncing working tree\n'
docker exec "${name}-build" find . \
  -mindepth 1 \( \
  -name build \
  -o -name .gradle \
  -o -name .kotlin \
  -o -name gradle.properties \
  \) -prune \
  -o ! -type d -exec rm -f {} + \
  -o -exec sh -c 'rmdir -p "$@" || :' - {} +
tar -cf- -C "$dir" \
  --exclude=./.tmp \
  --exclude=./.re \
  --exclude=./.open-mem \
  --exclude=./.github \
  --exclude=./.git \
  --exclude=./.gradle \
  --exclude=./.kotlin \
  --exclude='*/.gradle' \
  --exclude='*/.kotlin' \
  --exclude='*/build' \
  --exclude='./build' \
  . |
  docker exec -i "${name}-build" tar -xf-

printf '==> Building app\n'
set --
if [ $run_test -ne 0 ]; then
  set -- testDebugUnitTest
fi
if [ $run_lint -ne 0 ]; then
  set -- "$@" lintDebug
fi
docker exec -d "${name}-build" sh -c './gradlew assembleDebug "$@" --no-daemon --max-workers=1 > /tmp/build.log 2>&1' - "$@"
attempt=0
result=
while [ $attempt -lt 80 ]; do
  sleep 15
  attempt=$((attempt + 1))
  result="$(docker exec "${name}-build" grep -oE 'BUILD (SUCCESSFUL|FAILED)' /tmp/build.log 2>/dev/null | tail -1)"
  if [ -n "$result" ]; then
    printf '>> Build finished after ~%ss: %s\n' "$((attempt * 15))" "$result"
    break
  fi
  printf '>> ...still running (%ss)\n' "$((attempt * 15))"
done

if [ "$result" = 'BUILD SUCCESSFUL' ]; then
  _dir=app/build/outputs/apk/debug
  _file="$_dir/app-debug.apk"
  if [ ! -d "$dir/$_dir" ]; then
    mkdir -p "$dir/$_dir"
  fi
  docker cp "${name}-build:/workspace/$_file" "$dir/$_file"
  printf '>> APK copied to %s\n' "$_file"
fi
