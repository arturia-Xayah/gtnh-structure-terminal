package dev.structureterminal.update;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;

import org.junit.Test;

public class ServerVersionProbeTest {

    static final String STATUS = "{\"modinfo\":{\"type\":\"FML\",\"modList\":[{\"modid\":\"structureterminal\",\"version\":\"0.3.3-alpha\"}]}}";

    @Test
    public void parsesOnlyOurModAndRejectsAmbiguity() throws Exception {
        assertEquals("0.3.3-alpha", ServerVersionProbe.parse(STATUS));
        assertNull(ServerVersionProbe.parse("{}"));
        assertNull(ServerVersionProbe.parse(STATUS.replace("structureterminal", "anothermod")));
        assertNull(ServerVersionProbe.parse(STATUS.replace("0.3.3-alpha", "../../payload")));
        assertNull(
            ServerVersionProbe
                .parse(STATUS.replace("}]", "},{\"modid\":\"structureterminal\",\"version\":\"0.4.0\"}]")));
    }

    @Test
    public void realLoopbackStatusRequestDoesNotAttemptLogin() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            FutureTask<Void> server = new FutureTask<>(() -> {
                try (Socket socket = listener.accept()) {
                    socket.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    int length = ServerVersionProbe.readVarInt(in);
                    byte[] handshake = new byte[length];
                    in.readFully(handshake);
                    assertEquals(1, handshake[handshake.length - 1]);
                    assertEquals(1, in.readByte());
                    assertEquals(0, in.readByte());
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    DataOutputStream body = new DataOutputStream(bytes);
                    body.writeByte(0);
                    byte[] json = STATUS.getBytes(StandardCharsets.UTF_8);
                    ServerVersionProbe.writeVarInt(body, json.length);
                    body.write(json);
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    ServerVersionProbe.writeVarInt(out, bytes.size());
                    out.write(bytes.toByteArray());
                    out.flush();
                }
                return null;
            });
            Thread thread = new Thread(server);
            thread.setDaemon(true);
            thread.start();
            assertEquals(
                "0.3.3-alpha",
                ServerVersionProbe.query(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), listener.getLocalPort()),
                    "example.test"));
            server.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
