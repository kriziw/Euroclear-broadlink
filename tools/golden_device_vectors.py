"""Golden vectors for the device-control path, produced by the reference code itself.

Usage (needs `pip install cryptography` for python-broadlink):
    git clone https://github.com/mjg59/python-broadlink
    git clone https://github.com/Danirv/ypsilon-local
    python tools/golden_device_vectors.py path/to/python-broadlink path/to/ypsilon-local

* BroadLink session packets: python-broadlink's `Device.auth()` / `send_packet()` run
  against a stub socket with a fixed packet counter, so the encrypted bytes are
  deterministic. The auth reply is built here and then fed back into python-broadlink's
  own `auth()` to prove it accepts it.
* Runxin F79D frames: ypsilon-local's `runxin.framing` / `runxin.f79d` modules (pure
  Python, Apache-2.0).

The printed hex is what app/src/test/.../RunxinFramesTest.kt and BroadlinkSessionTest.kt
compare against.
"""
import importlib.util
import pathlib
import socket as real_socket
import sys
import types

PYBL = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "python-broadlink")
YPS = pathlib.Path(sys.argv[2] if len(sys.argv) > 2 else "ypsilon-local")
sys.path.insert(0, str(PYBL))

import broadlink  # noqa: E402
from broadlink import device as bl_device  # noqa: E402
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes  # noqa: E402

INIT_KEY = bytes.fromhex("097628343fe99e23765c1513accf8b02")
IV = bytes.fromhex("562e17996d093d28ddb3ba695a2e6f58")
MAC = bytes.fromhex("1cd1d7123456")  # made-up MAC with a BroadLink OUI
DEVTYPE = 0x520F


def aes(key, data, encrypt):
    c = Cipher(algorithms.AES(key), modes.CBC(IV))
    op = c.encryptor() if encrypt else c.decryptor()
    return op.update(data) + op.finalize()


def checksum(data):
    return (0xBEAF + sum(data)) & 0xFFFF


class StubSocket:
    """Records the first datagram, then answers with `reply` (or times out)."""

    sent = []
    reply = None

    def __init__(self, *a, **k):
        pass

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False

    def settimeout(self, t):
        pass

    def setsockopt(self, *a):
        pass

    def sendto(self, data, addr):
        StubSocket.sent.append(bytes(data))

    def recvfrom(self, n):
        if StubSocket.reply is None:
            raise real_socket.timeout()
        return StubSocket.reply, ("192.168.1.50", 80)

    def close(self):
        pass


bl_device.socket = types.SimpleNamespace(
    socket=StubSocket,
    AF_INET=real_socket.AF_INET,
    SOCK_DGRAM=real_socket.SOCK_DGRAM,
    SOL_SOCKET=real_socket.SOL_SOCKET,
    SO_BROADCAST=real_socket.SO_BROADCAST,
    SO_REUSEADDR=real_socket.SO_REUSEADDR,
    timeout=real_socket.timeout,
)


def build_response(command, payload_plain, key, error=0):
    """A device reply shaped like python-broadlink expects: 0x38 header + encrypted body."""
    body = aes(key, payload_plain + bytes((16 - len(payload_plain) % 16) % 16), True)
    packet = bytearray(0x38) + body
    packet[0:8] = bytes.fromhex("5aa5aa555aa5aa55")
    packet[0x22:0x24] = error.to_bytes(2, "little", signed=True)
    packet[0x24:0x26] = DEVTYPE.to_bytes(2, "little")
    packet[0x26:0x28] = command.to_bytes(2, "little")
    packet[0x2A:0x30] = MAC[::-1]
    packet[0x20:0x22] = checksum(packet).to_bytes(2, "little")
    return bytes(packet)


dev = broadlink.gendevice(DEVTYPE, ("192.168.1.50", 80), MAC)
dev.timeout = 0.01

# --- auth request (counter fixed) and a reply python-broadlink accepts -----------------
dev.count = 0x8000  # send_packet increments first -> count 0x8001 on the wire
session_id = 0x11223344
session_key = bytes(range(0x10, 0x20))
auth_reply_plain = session_id.to_bytes(4, "little") + session_key + bytes(12)
StubSocket.reply = build_response(0x3E9, auth_reply_plain, INIT_KEY)
StubSocket.sent.clear()
dev.auth()
assert dev.id == session_id, "python-broadlink rejected the generated auth reply"
print("AUTH_REQUEST", StubSocket.sent[0].hex())
print("AUTH_REPLY", StubSocket.reply.hex())

# --- an 0x6A command carrying a BL3372 TFB-wrapped Runxin query -------------------------
spec = importlib.util.spec_from_file_location
pkg = YPS / "custom_components" / "ypsilon_local" / "runxin"
sys.modules["ypsrunxin"] = types.ModuleType("ypsrunxin")
sys.modules["ypsrunxin"].__path__ = [str(pkg)]
framing = importlib.import_module("ypsrunxin.framing")
f79d = importlib.import_module("ypsrunxin.f79d")

query = framing.build_query_frame([1, 34])
tfb = len(query).to_bytes(2, "little") + query
dev.count = 0x8001
StubSocket.reply = build_response(0x3EE, b"\x00" * 16, session_key)
StubSocket.sent.clear()
dev.send_packet(0x6A, tfb)
print("CMD_6A_REQUEST", StubSocket.sent[0].hex())

# --- Runxin frames ---------------------------------------------------------------------
print("QUERY_1_34", query.hex())
print("QUERY_STATE", framing.build_query_frame(list(range(1, 52))).hex())
print("WRITE_HARDNESS_150", f79d.build_write_fields({47: 150}).hex())
print("WRITE_REGENERATE", f79d.build_write_fields({34: 1}).hex())
print("WRITE_CLOCK_14_37", f79d.build_write_fields({4: (14, 37)}).hex())
print("WRITE_FLOW_OFF_200", f79d.build_write_fields({7: 200}).hex())
print("WRITE_SALT_50", f79d.build_write_fields({43: 50}).hex())
sample_tlvs = [1, 9, 0, 8, 2, 0, 11, 0x00, 0x96, 34, 0, 0, 35, 0, 1, 36, 59, 2, 47, 0x96, 0x00, 7, 0x03, 0xE8]
response = framing.build_frame(framing.QUERY_RESPONSE_CODE, sample_tlvs)
print("RESPONSE_SAMPLE", response.hex())
print("RESPONSE_SAMPLE_DECODED", f79d.decode_frame(response))
