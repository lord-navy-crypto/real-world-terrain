import net from "node:net";

function encodePacket(requestId, type, body) {
  const text = Buffer.from(String(body), "utf8");
  const payload = Buffer.allocUnsafe(8 + text.length + 2);
  payload.writeInt32LE(requestId, 0);
  payload.writeInt32LE(type, 4);
  text.copy(payload, 8);
  payload[text.length + 8] = 0;
  payload[text.length + 9] = 0;
  const packet = Buffer.allocUnsafe(payload.length + 4);
  packet.writeInt32LE(payload.length, 0);
  payload.copy(packet, 4);
  return packet;
}

export class RconClient {
  constructor(socket, { timeoutMs = 30_000 } = {}) {
    this.socket = socket;
    this.timeoutMs = timeoutMs;
    this.sequence = 1000;
    this.buffer = Buffer.alloc(0);
    this.readers = [];
    this.closed = false;
    socket.on("data", chunk => {
      this.buffer = this.buffer.length ? Buffer.concat([this.buffer, chunk]) : chunk;
      this.flushReaders();
    });
    socket.on("error", error => this.failReaders(error));
    socket.on("close", () => {
      this.closed = true;
      this.failReaders(new Error("RCON connection closed"));
    });
  }

  static async connect({ host = "127.0.0.1", port, password, timeoutMs = 30_000 }) {
    if (!Number.isInteger(port) || port < 1 || port > 65535) {
      throw new TypeError("RCON port must be a valid TCP port");
    }
    if (typeof password !== "string" || !password) {
      throw new TypeError("RCON password is required");
    }

    const socket = net.createConnection({ host, port });
    await new Promise((resolve, reject) => {
      const onError = error => {
        socket.off("connect", onConnect);
        reject(error);
      };
      const onConnect = () => {
        socket.off("error", onError);
        resolve();
      };
      socket.once("error", onError);
      socket.once("connect", onConnect);
      socket.setTimeout(timeoutMs, () => socket.destroy(new Error("RCON socket timed out")));
    });

    const client = new RconClient(socket, { timeoutMs });
    const reply = await client.request(3, password);
    if (reply.requestId === -1 || reply.type !== 2) {
      client.close();
      throw new Error("Minecraft RCON authentication was rejected");
    }
    return client;
  }

  flushReaders() {
    while (this.readers.length > 0) {
      const reader = this.readers[0];
      if (this.buffer.length < reader.length) break;
      this.readers.shift();
      const value = this.buffer.subarray(0, reader.length);
      this.buffer = this.buffer.subarray(reader.length);
      reader.resolve(value);
    }
  }

  failReaders(error) {
    while (this.readers.length) this.readers.shift().reject(error);
  }

  readExact(length) {
    if (this.closed) return Promise.reject(new Error("RCON connection is closed"));
    if (this.buffer.length >= length) {
      const value = this.buffer.subarray(0, length);
      this.buffer = this.buffer.subarray(length);
      return Promise.resolve(value);
    }
    return new Promise((resolve, reject) => {
      this.readers.push({ length, resolve, reject });
      this.flushReaders();
    });
  }

  async readPacket() {
    const header = await this.readExact(4);
    const length = header.readInt32LE(0);
    if (length < 10 || length > 1_000_000) {
      throw new Error("Invalid RCON packet length: " + length);
    }
    const payload = await this.readExact(length);
    return {
      requestId: payload.readInt32LE(0),
      type: payload.readInt32LE(4),
      body: payload.subarray(8, Math.max(8, payload.length - 2)).toString("utf8")
    };
  }

  async request(type, body) {
    if (this.closed || this.socket.destroyed) throw new Error("RCON connection is closed");
    const requestId = ++this.sequence;
    this.socket.write(encodePacket(requestId, type, body));

    const deadline = Date.now() + this.timeoutMs;
    while (Date.now() < deadline) {
      const remaining = deadline - Date.now();
      let timer;
      const timeout = new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error("RCON response timed out")), remaining);
        timer.unref?.();
      });
      let packet;
      try {
        packet = await Promise.race([this.readPacket(), timeout]);
      } finally {
        clearTimeout(timer);
      }
      if (packet.requestId === -1) throw new Error("Minecraft RCON authentication was rejected");
      if (packet.requestId !== requestId) continue;
      return packet;
    }
    throw new Error("RCON response timed out");
  }

  async command(command) {
    if (typeof command !== "string" || !command.trim() || /[\r\n\u0000]/u.test(command)) {
      throw new TypeError("RCON command must be a single non-empty line");
    }
    const response = await this.request(2, command);
    return response.body;
  }

  close() {
    if (this.closed) return;
    this.closed = true;
    this.socket.end();
    this.socket.destroy();
    this.failReaders(new Error("RCON connection closed"));
  }
}

export { encodePacket as encodeRconPacket };
