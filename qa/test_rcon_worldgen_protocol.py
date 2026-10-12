#!/usr/bin/env python3
"""Real RCON wire-protocol tests independent of a Minecraft server."""
import socket
import struct
import sys
import threading
import unittest
from pathlib import Path

sys.path.insert(0,str(Path(__file__).resolve().parent/"neoforge"))
from rcon_worldgen_smoke import Rcon, PASSWORD


def receive_exact(sock,n):
    data=b""
    while len(data)<n:
        part=sock.recv(n-len(data))
        if not part:raise EOFError()
        data+=part
    return data


class FakeRconServer:
    def __init__(self,command_reply):
        self.listener=socket.socket()
        self.listener.bind(("127.0.0.1",0))
        self.listener.listen(1)
        self.port=self.listener.getsockname()[1]
        self.command_reply=command_reply
        self.seen=[]
        self.thread=threading.Thread(target=self.work,daemon=True)
        self.thread.start()

    @staticmethod
    def send_packet(sock,request_id,packet_type,message):
        content=struct.pack("<ii",request_id,packet_type)+message.encode()+b"\x00\x00"
        sock.sendall(struct.pack("<i",len(content))+content)

    def work(self):
        try:
            conn,_=self.listener.accept()
            with conn:
                for i in range(2):
                    size=struct.unpack("<i",receive_exact(conn,4))[0]
                    data=receive_exact(conn,size)
                    request_id,packet_type=struct.unpack_from("<ii",data)
                    payload=data[8:-2].decode()
                    self.seen.append((packet_type,payload))
                    if i==0:
                        self.send_packet(conn,request_id,2,"")
                    else:
                        self.send_packet(conn,request_id,0,self.command_reply)
        finally:
            self.listener.close()


class RconWireTests(unittest.TestCase):
    def test_distant_forceload_command_is_sent_and_acknowledged(self):
        fake=FakeRconServer("Marked 9 chunks in minecraft:overworld to be force loaded")
        client=Rcon(port=fake.port)
        command="execute in minecraft:overworld run forceload add 224 -16 256 16"
        try:
            reply=client.send(command)
            self.assertIn("Marked 9 chunks",reply)
        finally:
            client.close()
            fake.thread.join(timeout=5)
        self.assertEqual(fake.seen,[(3,PASSWORD),(2,command)])

    def test_minecraft_command_failure_is_rejected(self):
        fake=FakeRconServer("Unknown command")
        client=Rcon(port=fake.port)
        try:
            with self.assertRaisesRegex(RuntimeError,"command rejected"):
                client.send("forceload add 224 -16 256 16")
        finally:
            client.close()
            fake.thread.join(timeout=5)


if __name__=="__main__":
    unittest.main()
