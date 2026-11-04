package dev.structureterminal.update;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Vanilla status protocol, no login and no game-world packets; reads Forge's public modinfo. */
public final class ServerVersionProbe {

    private ServerVersionProbe() {}

    public static String query(InetSocketAddress address, String virtualHost) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address, 4000);
            socket.setSoTimeout(4000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(buffer);
            writeVarInt(handshake, 0);
            writeVarInt(handshake, 5);
            byte[] host = virtualHost.getBytes(StandardCharsets.UTF_8);
            if (host.length > 255) throw new IOException("Server host too long");
            writeVarInt(handshake, host.length);
            handshake.write(host);
            handshake.writeShort(address.getPort());
            writeVarInt(handshake, 1);
            writeVarInt(out, buffer.size());
            out.write(buffer.toByteArray());
            out.write(new byte[] { 1, 0 });
            out.flush();
            final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            DataInputStream in = new DataInputStream(new java.io.FilterInputStream(socket.getInputStream()) {

                private void timeout() throws IOException {
                    long remaining = (deadline - System.nanoTime()) / 1000000;
                    if (remaining <= 0) throw new java.net.SocketTimeoutException("Status deadline exceeded");
                    socket.setSoTimeout((int) Math.min(4000, Math.max(1, remaining)));
                }

                @Override
                public int read() throws IOException {
                    timeout();
                    return in.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) throws IOException {
                    timeout();
                    return in.read(bytes, offset, length);
                }
            });
            int size = readVarInt(in);
            if (size < 3 || size > 1024 * 1024) throw new IOException("Invalid status packet size");
            byte[] packet = new byte[size];
            in.readFully(packet);
            DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet));
            if (readVarInt(data) != 0) throw new IOException("Not a status packet");
            int len = readVarInt(data);
            if (len < 0 || len != data.available()) throw new IOException("Invalid status JSON size");
            byte[] json = new byte[len];
            data.readFully(json);
            return parse(new String(json, StandardCharsets.UTF_8));
        }
    }

    public static String parse(String json) throws IOException {
        try {
            JsonObject root = new JsonParser().parse(json)
                .getAsJsonObject();
            if (!root.has("modinfo")) return null;
            JsonObject info = root.getAsJsonObject("modinfo");
            if (!info.has("modList")) return null;
            String version = null;
            for (JsonElement entry : info.getAsJsonArray("modList")) {
                JsonObject mod = entry.getAsJsonObject();
                if (!"structureterminal".equals(
                    mod.get("modid")
                        .getAsString()))
                    continue;
                String value = mod.get("version")
                    .getAsString();
                if (!ReleaseCatalog.validVersion(value) || version != null) return null;
                version = value;
            }
            return version;
        } catch (RuntimeException e) {
            throw new IOException("Invalid Forge status", e);
        }
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        for (int i = 0; i < 5; i++) {
            int b = in.readUnsignedByte();
            result |= (b & 127) << (7 * i);
            if ((b & 128) == 0) return result;
        }
        throw new IOException("Invalid VarInt");
    }

    static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            int b = value & 127;
            value >>>= 7;
            out.writeByte(value == 0 ? b : b | 128);
        } while (value != 0);
    }
}
