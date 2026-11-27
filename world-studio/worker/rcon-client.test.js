import test from "node:test";
import assert from "node:assert/strict";
import net from "node:net";
import { RconClient, encodeRconPacket } from "./rcon-client.js";

function readPacket(socket, buffer, callback) {
  socket.on("data", function onData(chunk) {
    buffer.value = Buffer.concat([buffer.value, chunk]);
    if (buffer.value.length < 4) return;
    const length = buffer.value.readInt32LE(0);
    if (buffer.value.length < length + 4) return;
    const payload = buffer.value.subarray(4, length + 4);
    buffer.value = buffer.value.subarray(length + 4);
    socket.off("data", onData);
    callback({
      requestId: payload.readInt32LE(0),
      type: payload.readInt32LE(4),
      body: payload.subarray(8, payload.length - 2).toString("utf8")
    });
  });
}

async function createFakeRcon({ rejectAuth = false } = {}) {
  const server = net.createServer(socket => {
    const buffer = { value: Buffer.alloc(0) };
    const next = () => readPacket(socket, buffer, packet => {
      if (packet.type === 3) {
        const id = rejectAuth ? -1 : packet.requestId;
        const response = encodeRconPacket(id, 2, "");
        socket.write(response.subarray(0, 3));
        setTimeout(() => socket.write(response.subarray(3)), 2);
      } else {
        const response = encodeRconPacket(packet.requestId, 0, "OK:" + packet.body);
        socket.write(response);
      }
      next();
    });
    next();
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  return {
    port: server.address().port,
    close: () => new Promise(resolve => server.close(resolve))
  };
}

test("RCON client authenticates and handles fragmented TCP packets", async t => {
  const fake = await createFakeRcon();
  t.after(fake.close);
  const client = await RconClient.connect({ port: fake.port, password: "test", timeoutMs: 1000 });
  t.after(() => client.close());
  assert.equal(await client.command("seed"), "OK:seed");
});

test("RCON client rejects a failed authentication handshake", async t => {
  const fake = await createFakeRcon({ rejectAuth: true });
  t.after(fake.close);
  await assert.rejects(
    RconClient.connect({ port: fake.port, password: "wrong", timeoutMs: 1000 }),
    /authentication was rejected/
  );
});

test("RCON client rejects multiline commands", async t => {
  const fake = await createFakeRcon();
  t.after(fake.close);
  const client = await RconClient.connect({ port: fake.port, password: "test", timeoutMs: 1000 });
  t.after(() => client.close());
  await assert.rejects(client.command("seed\nstop"), /single non-empty line/);
});

test("RCON packet encoder writes the Minecraft little-endian framing", () => {
  const packet = encodeRconPacket(17, 2, "seed");
  assert.equal(packet.readInt32LE(0), packet.length - 4);
  assert.equal(packet.readInt32LE(4), 17);
  assert.equal(packet.readInt32LE(8), 2);
  assert.equal(packet.subarray(12, -2).toString("utf8"), "seed");
  assert.deepEqual([...packet.subarray(-2)], [0, 0]);
});
