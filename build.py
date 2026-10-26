#!/usr/bin/env python3
"""Build without Gradle. Download Google's build tools/platform and the two Java jars if absent."""
import os
import sys
import pathlib
import subprocess
import shutil
import urllib.request
import zipfile

ROOT = pathlib.Path(__file__).resolve().parent
TOOLS = ROOT / '.toolchain'
TOOLS.mkdir(exist_ok=True)

def download(name, url):
    dest = TOOLS / name
    if not dest.exists():
        print('Downloading', name, flush=True)
        urllib.request.urlretrieve(url, dest)
    return dest

def sdk_zip(name, url, expected):
    if not (TOOLS / expected).exists():
        archive = download(name, url)
        with zipfile.ZipFile(archive) as z:
            z.extractall(TOOLS)
    return TOOLS / expected

bt = pathlib.Path(os.environ['BUILD_TOOLS']) if 'BUILD_TOOLS' in os.environ else sdk_zip(
    'build-tools.zip', 'https://dl.google.com/android/repository/build-tools_r35_linux.zip', 'android-15')
android = pathlib.Path(os.environ['ANDROID_JAR']) if 'ANDROID_JAR' in os.environ else sdk_zip(
    'platform.zip', 'https://dl.google.com/android/repository/platform-35_r02.zip', 'android-35') / 'android.jar'
ecj = pathlib.Path(os.environ['ECJ_JAR']) if 'ECJ_JAR' in os.environ else download(
    'ecj.jar', 'https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar')
xposed = pathlib.Path(os.environ['XPOSED_JAR']) if 'XPOSED_JAR' in os.environ else download(
    'xposed-api.jar', 'https://api.xposed.info/de/robv/android/xposed/api/82/api-82.jar')
for name in ('aapt2', 'zipalign', 'apksigner'):
    (bt / name).chmod(0o755)

def run(*args):
    subprocess.run([str(v) for v in args], cwd=ROOT, check=True)

out = ROOT / 'out'
if out.exists(): shutil.rmtree(out)
out.mkdir()
run('java', '-jar', ecj, '-8', '-proc:none', '-encoding', 'UTF-8', '-warn:none',
    '-classpath', str(android) + os.pathsep + str(xposed), '-d', out,
    *sorted((ROOT / 'src').rglob('*.java')))
test_out = ROOT / 'test-out'
if test_out.exists(): shutil.rmtree(test_out)
test_out.mkdir()
run('java', '-jar', ecj, '-8', '-proc:none', '-encoding', 'UTF-8', '-warn:none',
    '-classpath', str(out), '-d', test_out, *sorted((ROOT / 'tests').rglob('*.java')))
for test in ('PolicyTest', 'RegressionTest', 'DurationPolicyTest', 'IslandRefreshLoopTest', 'MonitorRecoveryTest', 'ProcessLockLeaseTest', 'RecentsPolicyTest'):
    run('java', '-cp', str(out) + os.pathsep + str(test_out), 'dev.lance.focusallowlist.' + test)
run(sys.executable, ROOT / 'tests/verify_island.py', str(out) + os.pathsep + str(test_out))
jar = ROOT / 'classes.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(out.rglob('*.class')):
        z.write(p, p.relative_to(out).as_posix())
run('java', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '28',
    '--lib', android, '--classpath', xposed, '--output', out, jar)
run(bt / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', ROOT / 'resources.zip')
run(bt / 'aapt2', 'link', '-I', android, '--manifest', ROOT / 'AndroidManifest.xml',
    '-A', ROOT / 'assets', '-o', ROOT / 'unsigned.apk', ROOT / 'resources.zip')
with zipfile.ZipFile(ROOT / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(out / 'classes.dex', 'classes.dex')
run(bt / 'zipalign', '-f', '-p', '4', ROOT / 'unsigned.apk', ROOT / 'aligned.apk')
key = ROOT / 'test-signing.p12'
if not key.exists():
    run('keytool', '-genkeypair', '-keystore', key, '-storetype', 'PKCS12', '-storepass', 'android',
        '-keypass', 'android', '-alias', 'focus-test', '-keyalg', 'RSA', '-keysize', '2048',
        '-validity', '3650', '-dname', 'CN=Focus Allowlist Test')
result = ROOT / 'FocusAllowlist-0.1.23-test.apk'
run(bt / 'apksigner', 'sign', '--ks', key, '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
    '--ks-key-alias', 'focus-test', '--out', result, ROOT / 'aligned.apk')
run(bt / 'apksigner', 'verify', '--verbose', result)
print('Built:', result)
