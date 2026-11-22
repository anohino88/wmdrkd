"""Run headless 26.3 protocol clients on the disposable loopback Paper server."""

import socket
import struct
import threading
import time
import uuid

# Packet IDs checked against GeyserMC/MCProtocolLib MinecraftCodec at
# 65efb9dac372709f49f4395ae4361642bc012328 (protocol 777).
PROTOCOL = 777
PORT = 25565


def varint(value):
    encoded = bytearray()
    while value >= 128:
        encoded.append((value & 127) | 128)
        value >>= 7
    encoded.append(value)
    return encoded


def read_varint(stream):
    result = 0
    for shift in range(0, 35, 7):
        byte = stream.read(1)
        if not byte:
            raise EOFError
        result |= (byte[0] & 127) << shift
        if byte[0] < 128:
            return result
    raise ValueError("Invalid VarInt")


def string(value):
    data = value.encode("utf-8")
    return varint(len(data)) + data


class Client:
    def __init__(self, number):
        self.name = f"AW{number:02}"
        self.conn = socket.create_connection(("127.0.0.1", PORT), timeout=10)
        self.conn.settimeout(None)
        self.write_lock = threading.Lock()
        self.closed = threading.Event()
        self.ready = threading.Event()
        self.failure = None
        self.phase = "login"
        self.position = (0,0,0,0,0)
        self.loading = True
        self.send(0, varint(PROTOCOL) + string("localhost") + struct.pack(">H", PORT) + varint(2))
        self.send(0, string(self.name) + uuid.UUID(int=number + 1).bytes)
        self.thread = threading.Thread(target=self.read_packets, daemon=True)
        self.thread.start()

    def send(self, packet, body=b""):
        data = varint(packet) + body
        with self.write_lock:
            self.conn.sendall(varint(len(data)) + data)

    def close(self):
        if not self.closed.is_set():
            self.closed.set()
            try:
                self.conn.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass
            self.conn.close()
            self.thread.join(timeout=2)

    def read_packets(self):
        from io import BytesIO
        try:
            with self.conn.makefile("rb") as stream:
                while not self.closed.is_set():
                    length = read_varint(stream)
                    if not 0 < length <= 8 * 1024 * 1024:
                        raise ValueError(f"Unexpected packet length {length}")
                    packet = BytesIO(stream.read(length))
                    packet_id = read_varint(packet)
                    self.handle(packet_id,packet)
        except (OSError, EOFError, ValueError, RuntimeError, struct.error) as failure:
            if not self.closed.is_set():
                self.failure = repr(failure)
                self.ready.set()

    def handle(self, packet_id, packet):
        if self.phase == "login":
            if packet_id == 0:
                raise RuntimeError(f"Login refused: {packet.read().decode(errors='replace')}")
            if packet_id != 2:
                raise RuntimeError(f"Unexpected login packet {packet_id}")
            self.send(3)  # Login acknowledged.
            self.phase = "config"
            self.send(0, string("en_us") + bytes([3, 0, 1, 127, 1, 0, 1, 0]))
        elif self.phase == "config":
            if packet_id == 2:
                raise RuntimeError("Disconnected in configuration")
            if packet_id in (4, 5):
                self.send(packet_id,packet.read())  # Keep-alive or ping.
            if packet_id == 15:
                self.send(7,b"\0")  # Request full registries without cached packs.
            if packet_id == 3:
                self.send(3)  # Finish configuration.
                self.phase = "play"
        else:
            if packet_id == 0x20:
                raise RuntimeError("Disconnected during play")
            if packet_id == 0x54:
                self.loading = True
            elif packet_id == 0x49:
                self.accept_teleport(packet)
                if self.loading:
                    self.send(0x2C)  # Initial or cross-dimension world loaded.
                    self.loading = False
                self.ready.set()
            elif packet_id == 0x2D:
                self.send(0x1C,packet.read())
            elif packet_id == 0x3E:
                self.send(0x2D,packet.read())
            elif packet_id == 0x0B:
                self.send(0x0B,struct.pack(">f",8.0))

    def accept_teleport(self, packet):
        teleport = read_varint(packet)
        x, y, z, _, _, _, yaw, pitch, relative = struct.unpack(">6d2fI",packet.read(60))
        self.position = tuple(value + self.position[i] if relative & (1 << i) else value
                              for i, value in enumerate((x,y,z,yaw,pitch)))
        self.send(0,varint(teleport) + struct.pack(">3d2f",*self.position))

    def command(self, line):
        self.send(0x07,string(line))


def exercise(command, log):
    def wait(marker, start=0, timeout=20, alternate=None):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            with log.open("rb") as handle:
                handle.seek(start)
                new = handle.read()
            if marker.encode() in new:
                return True
            if alternate and any(message.encode() in new for message in alternate):
                return False
            time.sleep(.05)
        raise RuntimeError(f"Missing after byte {start}: {marker}")

    def expect(line, marker, timeout=20):
        position = log.stat().st_size
        command(line)
        wait(marker,position,timeout)

    def start_match(count, automatic, overflow=0, practice=False):
        for _ in range(20):
            position = log.stat().st_size
            command("aw admin start")
            if wait(f"참가 대상 {count}/16명 (자동 {automatic}명) · 정원 초과 {overflow}명",position,
                    alternate=("이전 경기장 탐색이 정리될 때까지 기다리세요",)):
                break
            time.sleep(.25)
        else:
            raise RuntimeError("Chunk requests from the aborted search did not settle")
        if practice:
            wait("1인 연습 · 승패 기록 없음", position)
        if not wait("경기가 시작됐습니다",position,75,alternate=("경기 준비가 중단됐습니다","경기장 탐색 실패")):
            raise RuntimeError(log.read_bytes()[position:].decode(errors="replace"))
        expect("aw status",f"경기 단계 GATHERING | 참가자 {count}명")

    def stop_match():
        expect("aw admin stop","경기를 종료했습니다")
        expect("aw status","경기 단계 WAITING")

    clients = []
    def connect(number, wait_ready=True):
        client = Client(number)
        clients.append(client)
        if wait_ready:
            ready(client)
        return client

    def ready(client):
        if not client.ready.wait(25) or client.failure:
            raise RuntimeError(f"{client.name} did not reach play: {client.failure}")
        wait(f"{client.name} joined the game")

    wait("AW_START_FIXTURE_READY")
    try:
        command("setworldspawn 0 -60 0")
        connect(0)
        expect("tp AW00 0.5 -60 0.5","Teleported AW00")
        expect("item replace entity AW00 hotbar.0 with minecraft:diamond","AW00")
        start_match(1,1,practice=True)
        expect("execute as AW00 at @s if dimension minecraft:aw_lab run say AW_SOLO_IN_LAB","AW_SOLO_IN_LAB")
        expect("execute unless items entity AW00 hotbar.0 minecraft:diamond run say AW_SOLO_CLEARED","AW_SOLO_CLEARED")
        stop_match()
        expect("execute as AW00 at @s if dimension minecraft:overworld run say AW_RESTORED_WORLD","AW_RESTORED_WORLD")
        expect("execute if items entity AW00 hotbar.0 minecraft:diamond run say AW_RESTORED_ITEM","AW_RESTORED_ITEM")
        if (log.parent / "plugins/AugmentWild/used-regions.yml").exists():
            raise AssertionError("Solo practice wrote the production region ledger")

        second = connect(1)
        optout = log.stat().st_size
        second.command("aw leave")
        wait("AW01 issued server command: /aw leave",optout)
        expect("aw admin arena check","참가 대상 1/16명 (자동 1명)")
        optin = log.stat().st_size
        second.command("aw join")
        wait("AW01 issued server command: /aw join",optin)
        expect("aw admin arena check","참가 대상 2/16명 (자동 1명)")

        position = log.stat().st_size
        command("aw admin start")
        wait("경기장 탐색을 시작했습니다",position)
        second.close()
        wait("참가자가 경기 준비 중 접속을 종료했습니다",position)
        connect(1)
        expect("aw admin arena check","참가 대상 2/16명 (자동 2명)")
        start_match(2,2)
        connect(2)
        expect("aw status","경기 단계 GATHERING | 참가자 2명")
        stop_match()
        # This disposable fixture intentionally reuses the same 512x512 rectangle.
        ledger = log.parent / "plugins/AugmentWild/used-regions.yml"
        ledger.unlink()

        more = [connect(n,wait_ready=False) for n in range(3,16)]
        for client in more:
            ready(client)
        start_match(16,16)
        stop_match()
        ledger.unlink()
        connect(16)
        start_match(16,16,overflow=1)
        stop_match()
        if any(client.failure and not client.closed.is_set() for client in clients):
            raise RuntimeError("A fixture client disconnected unexpectedly")
        print("AW_PAPER_START_CLIENTS_PASS solo=1 normal=2 capped=16 online=17 optout=true late_join=true prep_disconnect=true restore=true",
              flush=True)
    finally:
        for client in clients:
            client.close()
