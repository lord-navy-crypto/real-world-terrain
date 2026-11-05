"""Minimal local Minecraft Java RCON commands for disposable GitHub CI server."""
import socket
import struct
import time

HOST = "127.0.0.1"
PORT = 25575
PASSWORD = "realscale-ci-only"

def _exact(sock, amount):
    result = bytearray()
    while len(result) < amount:
        piece = sock.recv(amount - len(result))
        if not piece:
            raise ConnectionError("RCON connection closed")
        result.extend(piece)
    return bytes(result)

def _send(sock, packet_id, packet_type, message):
    body = struct.pack("<ii", packet_id, packet_type) + message.encode() + b"\x00\x00"
    sock.sendall(struct.pack("<i",len(body)) + body)

def _receive(sock):
    length = struct.unpack("<i", _exact(sock,4))[0]
    if not 10 <= length <= 4096:
        raise ValueError("Bad RCON frame length: " + str(length))
    raw = _exact(sock,length)
    packet_id,kind = struct.unpack("<ii",raw[:8])
    return packet_id,kind,raw[8:-2].decode("utf-8","replace")

def command(message):
    last = None
    for attempt in range(12):
        try:
            with socket.create_connection((HOST,PORT),timeout=8) as sock:
                sock.settimeout(20)
                _send(sock,101,3,PASSWORD)
                # Vanilla may send an empty response-value packet before
                # the actual type=2 authentication acknowledgement.
                for _ in range(3):
                    authid,auth_type,_ = _receive(sock)
                    if auth_type == 2:
                        if authid != 101: raise ValueError("RCON auth failure")
                        break
                else:
                    raise ValueError("No RCON auth acknowledgement received")
                _send(sock,102,2,message)
                command_id,_,reply = _receive(sock)
                if command_id != 102: raise ValueError("RCON command failure")
                print("RCON",message,repr(reply[:400]),flush=True)
                return reply
        except (ConnectionError,TimeoutError,OSError) as exc:
            last = exc
            time.sleep(1)
    raise RuntimeError("RCON command failed: "+message) from last
