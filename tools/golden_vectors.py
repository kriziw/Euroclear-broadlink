"""Generate golden packets by executing python-broadlink's own source verbatim.

Usage:
    git clone https://github.com/mjg59/python-broadlink
    python tools/golden_vectors.py path/to/python-broadlink

`setup()` and `scan()` are extracted with `ast` from the checkout and executed
against a stub socket that records `sendto` instead of transmitting, so the
`cryptography` dependency of the package is not needed. The printed hex is what
app/src/test/.../BroadlinkPacketsTest.kt compares against.
"""
import ast
import datetime as dt
import pathlib
import socket as real_socket
import sys

REPO = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "python-broadlink") / "broadlink"


def extract(path, name):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.FunctionDef) and node.name == name:
            return ast.get_source_segment(path.read_text(encoding="utf-8"), node)
    raise KeyError(name)


class StubSocket:
    sent = []

    def __init__(self, *a, **k):
        pass

    def setsockopt(self, *a):
        pass

    def bind(self, addr):
        self._bound = addr

    def getsockname(self):
        return ("0.0.0.0", 0)

    def settimeout(self, t):
        pass

    def sendto(self, data, addr):
        StubSocket.sent.append((bytes(data), addr))

    def recvfrom(self, n):
        raise real_socket.timeout()

    def close(self):
        pass


class StubSocketModule:
    AF_INET = real_socket.AF_INET
    SOCK_DGRAM = real_socket.SOCK_DGRAM
    SOL_SOCKET = real_socket.SOL_SOCKET
    SO_REUSEADDR = real_socket.SO_REUSEADDR
    SO_BROADCAST = real_socket.SO_BROADCAST
    timeout = real_socket.timeout
    socket = StubSocket
    inet_aton = staticmethod(real_socket.inet_aton)


consts = {}
exec((REPO / "const.py").read_text(encoding="utf-8"), consts)
proto = {}
exec((REPO / "protocol.py").read_text(encoding="utf-8"), proto)
Datetime = proto["Datetime"]

g = {
    "socket": StubSocketModule,
    "DEFAULT_BCAST_ADDR": consts["DEFAULT_BCAST_ADDR"],
    "DEFAULT_PORT": consts["DEFAULT_PORT"],
    "DEFAULT_RETRY_INTVL": consts["DEFAULT_RETRY_INTVL"],
    "DEFAULT_TIMEOUT": consts["DEFAULT_TIMEOUT"],
    "Datetime": Datetime,
    "Optional": object,
    "Generator": object,
    "HelloResponse": object,
}
import typing

g["Optional"] = typing.Optional
g["Generator"] = typing.Generator
g["time"] = __import__("time")

exec(extract(REPO / "__init__.py", "setup"), g)
exec(extract(REPO / "device.py", "scan"), g)

cases = [
    ("MyHomeWiFi", "correct-horse-42", 3),
    ("Net", "", 0),
    ("A" * 32, "B" * 32, 4),
    ("Kávé", "jelszó12", 2),  # Python's ord() == Latin-1 for these chars
]
for ssid, pw, mode in cases:
    StubSocket.sent.clear()
    g["setup"](ssid, pw, mode)
    data, addr = StubSocket.sent[0]
    print(f"SETUP ssid={ssid!r} pw={pw!r} mode={mode} -> {addr} len={len(data)}")
    print(data.hex())

# Hello / discovery packet with a frozen clock.
fixed = dt.datetime(2026, 10, 4, 14, 37, 0, tzinfo=dt.timezone(dt.timedelta(hours=1)))
Datetime.now = staticmethod(lambda: fixed)
StubSocket.sent.clear()
StubSocket.getsockname = lambda self: ("192.168.1.23", 51234)
gen = g["scan"](timeout=0.01, local_ip_address="192.168.1.23")
try:
    next(gen)
except StopIteration:
    pass
data, addr = StubSocket.sent[0]
print(f"HELLO time={fixed.isoformat()} local=192.168.1.23:51234 -> {addr} len={len(data)}")
print(data.hex())
