#!/usr/bin/env python3
"""Run proxy configuration, messaging, and routing checks against the offline build."""
import os
import pathlib
import shutil
import subprocess

root = pathlib.Path(__file__).resolve().parents[1]
kit = root.parent / 'Core/offline-build'
java_home = os.environ.get('JAVA_HOME')
javac = str(pathlib.Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
if not javac:
    raise SystemExit('Set JAVA_HOME to a JDK 11 or newer.')
java = str(pathlib.Path(javac).with_name('java'))
libs = sorted((kit / 'vendor/lib').glob('*.jar'))
classes = kit / 'work/build/PalaceBungee'
jar = pathlib.Path(os.environ.get('PALACE_BUILD_OUTPUT', str(kit / 'dist'))) / 'PalaceBungee.jar'
if not classes.is_dir() or not jar.is_file():
    raise SystemExit('Build the plugins with Core/offline-build/build.sh first.')
tests = root / 'work/config-tests'
tests.mkdir(parents=True, exist_ok=True)
cp = os.pathsep.join(map(str, [classes, kit / "work/build/Storage", *libs]))
subprocess.run([javac, '--release', '11', '-proc:none', '-cp', cp, '-d', str(tests),
                *map(str, sorted((root / 'src/test/java').rglob('*.java')))], check=True)
for artifact in (classes, jar):
    runtime = [tests, artifact, *libs]
    if artifact == classes:
        runtime.insert(2, kit / "work/build/Storage")
    cp = os.pathsep.join(map(str, runtime))
    for test in ('network.palace.bungee.utils.ConfigUtilTest', 'network.palace.bungee.messages.MessageHandlerTest', 'network.palace.bungee.utils.HubSelectorTest'):
        subprocess.run([java, '-cp', cp, test], check=True)
