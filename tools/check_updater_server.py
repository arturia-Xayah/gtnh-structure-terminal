#!/usr/bin/env python3
"""Boot an isolated real server, probe its public Forge version with production Java code, stop it. No client."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import time
from run_modernization_backend import prepare


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    for name in ('pack', 'java', 'javac', 'jar', 'gson', 'run-dir'):
        ap.add_argument('--' + name, type=Path, required=True)
    ap.add_argument('--expected', required=True)
    args = ap.parse_args()
    pack, java, javac, jar, gson, folder = (getattr(args, name).resolve() for name in ('pack', 'java', 'javac', 'jar', 'gson', 'run_dir'))
    if folder.exists() or pack == folder or pack in folder.parents:
        ap.error('Use a fresh disposable directory outside the source pack')
    prepare(pack, folder, jar, 'updater-status-test')
    with socket.socket() as bind:
        bind.bind(('127.0.0.1', 0))
        port = bind.getsockname()[1]
    props = folder / 'server.properties'
    props.write_text(props.read_text().replace('server-port=0', f'server-port={port}'))
    source = folder / 'UpdaterStatusSmoke.java'
    source.write_text('''import java.net.InetSocketAddress;
import dev.structureterminal.update.ServerVersionProbe;
public final class UpdaterStatusSmoke {
 public static void main(String[] args) throws Exception {
  String version = ServerVersionProbe.query(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), "localhost");
  if (!args[1].equals(version)) throw new AssertionError("Unexpected version: " + version);
  System.out.println(version);
 }
}
''')
    cp = os.pathsep.join(map(str, (jar, gson, folder)))
    subprocess.run([str(javac), '--release', '8', '-cp', cp, str(source)], check=True)
    started = time.monotonic()
    command = [str(java), '-Xms1G', '-Xmx6G', '-XX:+UseG1GC', '-Djava.awt.headless=true',
               '-Duser.language=en', '@java9args.txt', '-jar', 'lwjgl3ify-forgePatches.jar', 'nogui']
    logpath = folder / 'server.log'
    with logpath.open('w') as log:
        process = subprocess.Popen(command, cwd=folder, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = started + 600
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError(f'Server exited early: {process.returncode}')
                if 'Done (' in logpath.read_text(errors='replace'):
                    break
                time.sleep(2)
            else:
                raise RuntimeError('Server startup deadline exceeded')
            def varint(value):
                out = bytearray()
                while value > 127:
                    out.append((value & 127) | 128)
                    value >>= 7
                out.append(value)
                return bytes(out)
            def read_varint(sock):
                result = 0
                for i in range(5):
                    b = sock.recv(1)
                    if not b:
                        raise RuntimeError('EOF')
                    result |= (b[0] & 127) << (7 * i)
                    if b[0] < 128:
                        return result
                raise RuntimeError('Bad varint')
            # GTNH prints vanilla Done before post-start handlers finish; Hodgepodge still rejects status temporarily.
            for attempt in range(90):
                for label, host in [('vanilla', b'localhost'), ('fml', b'localhost\0FML\0')]:
                    with socket.create_connection(('127.0.0.1', port), timeout=5) as client:
                        handshake = b'\0\5' + varint(len(host)) + host + port.to_bytes(2, 'big') + b'\1'
                        client.sendall(varint(len(handshake)) + handshake + b'\1\0')
                        read_varint(client)
                        assert read_varint(client) == 0
                        length = read_varint(client)
                        data = bytearray()
                        while len(data) < length:
                            chunk = client.recv(length - len(data))
                            if not chunk:
                                raise RuntimeError('EOF')
                            data.extend(chunk)
                        (folder / ('status-' + label + '.json')).write_bytes(data)
                if 'modinfo' in json.loads((folder / 'status-vanilla.json').read_text()):
                    break
                time.sleep(2)
            else:
                raise RuntimeError('Server never advertised Forge modinfo')
            probe = subprocess.run([str(java), '-cp', cp, 'UpdaterStatusSmoke', str(port), args.expected], capture_output=True, text=True, timeout=20)
            if probe.returncode:
                raise RuntimeError(probe.stdout + probe.stderr)
            process.stdin.write('stop\n')
            process.stdin.flush()
            process.wait(timeout=90)
            result = dict(passed=process.returncode == 0, client_launched=False, installed_jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                          reported_version=probe.stdout.strip(), server_exit=process.returncode, elapsed_seconds=round(time.monotonic() - started, 1))
            (folder / 'updater-server.json').write_text(json.dumps(result, indent=2) + '\n')
            print(json.dumps(result), flush=True)
            if not result['passed']:
                raise RuntimeError('Server did not stop cleanly')
        finally:
            if process.poll() is None:
                try:
                    process.stdin.write('stop\n')
                    process.stdin.flush()
                    process.wait(timeout=30)
                except (BrokenPipeError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()


if __name__ == '__main__':
    main()
