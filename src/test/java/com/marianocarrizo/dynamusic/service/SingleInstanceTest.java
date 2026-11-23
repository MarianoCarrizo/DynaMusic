package com.marianocarrizo.dynamusic.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceTest {

    private static final long WAIT_SECONDS = 5;

    @TempDir Path dir;

    private final BlockingQueue<OpenRequest> received = new LinkedBlockingQueue<>();
    private SingleInstance first;
    private SingleInstance second;

    @AfterEach
    void closeInstances() {
        if (first != null) first.close();
        if (second != null) second.close();
    }

    private SingleInstance instance() {
        return new SingleInstance(dir, 2, 20);
    }

    private File song(String name) throws IOException {
        return Files.createFile(dir.resolve(name)).toFile().getAbsoluteFile();
    }

    @Test
    void firstClaimIsPrimaryAndSecondIsSecondary() {
        first = instance();
        second = instance();

        assertEquals(SingleInstance.Role.PRIMARY, first.claim());
        assertEquals(SingleInstance.Role.SECONDARY, second.claim());
    }

    @Test
    void releasedLockCanBeClaimedAgain() {
        first = instance();
        first.claim();
        first.close();
        second = instance();

        assertEquals(SingleInstance.Role.PRIMARY, second.claim());
    }

    @Test
    void claimingRemovesAStalePortFileLeftByACrash() throws IOException {
        Files.writeString(dir.resolve(SingleInstance.PORT_FILE), "49999");
        first = instance();

        first.claim();

        assertFalse(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
    }

    @Test
    void unusedLockFileFromACrashDoesNotBlockTheNextLaunch() throws IOException {
        Files.writeString(dir.resolve(SingleInstance.LOCK_FILE), "left over");
        first = instance();

        assertEquals(SingleInstance.Role.PRIMARY, first.claim());
    }

    @Test
    void forwardedRequestReachesThePrimaryInstance() throws Exception {
        File track = song("hello.mp3");
        first = instance();
        first.claim();
        first.listen(received::add);
        second = instance();
        second.claim();

        boolean delivered = second.forward(new OpenRequest(List.of(track)));

        assertTrue(delivered);
        OpenRequest request = received.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertEquals(List.of(track), request.files());
    }

    @Test
    void forwardFailsWhenNobodyListens() {
        first = instance();

        assertFalse(first.forward(new OpenRequest(List.of())));
    }

    @Test
    void forwardFailsAgainstAStalePortFile() throws IOException {
        Files.writeString(dir.resolve(SingleInstance.PORT_FILE), "1");
        first = instance();

        assertFalse(first.forward(new OpenRequest(List.of())));
    }

    @Test
    void forwardFailsOnAGarbagePortFile() throws IOException {
        Files.writeString(dir.resolve(SingleInstance.PORT_FILE), "not a port");
        first = instance();

        assertFalse(first.forward(new OpenRequest(List.of())));
    }

    @Test
    void listenerIgnoresMessagesWithoutTheExpectedHeader() throws Exception {
        File track = song("hello.mp3");
        first = instance();
        first.claim();
        first.listen(received::add);

        String reply = sendRaw("RUN calc.exe\n" + track.getAbsolutePath() + "\n");

        assertEquals("", reply);
        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void listenerIgnoresOversizedMessages() throws Exception {
        first = instance();
        first.claim();
        first.listen(received::add);

        String reply = sendRaw(OpenRequest.HEADER + "\n" + "x".repeat(OpenRequest.MAX_BYTES * 2));

        assertEquals("", reply);
        assertNull(received.poll(300, TimeUnit.MILLISECONDS));
    }

    @Test
    void listenerKeepsServingAfterAMalformedMessage() throws Exception {
        File track = song("hello.mp3");
        first = instance();
        first.claim();
        first.listen(received::add);
        sendRaw("garbage");
        second = instance();

        assertTrue(second.forward(new OpenRequest(List.of(track))));
        assertEquals(List.of(track), received.poll(WAIT_SECONDS, TimeUnit.SECONDS).files());
    }

    @Test
    void listenWithoutHoldingTheLockDoesNothing() {
        first = instance();

        first.listen(received::add);

        assertFalse(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
    }

    @Test
    void portFileIsRemovedWhenTheInstanceCloses() {
        first = instance();
        first.claim();
        first.listen(received::add);
        assertTrue(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));

        first.close();

        assertFalse(Files.exists(dir.resolve(SingleInstance.PORT_FILE)));
    }

    @Test
    void slowClientIsCutOffAtTheConnectionDeadlineWhileOthersStillGetServed() throws Exception {
        File track = song("hello.mp3");
        first = new SingleInstance(dir, 2, 20, 400);
        first.claim();
        first.listen(received::add);
        int port = Integer.parseInt(Files.readString(dir.resolve(SingleInstance.PORT_FILE)).trim());

        try (Socket trickler = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
            trickler.setSoTimeout(3000);
            trickler.getOutputStream().write('D');
            second = instance();

            assertTrue(second.forward(new OpenRequest(List.of(track))));
            assertEquals(List.of(track), received.poll(WAIT_SECONDS, TimeUnit.SECONDS).files());
            assertEquals(-1, trickler.getInputStream().read());
        }
    }

    @Test
    void connectionsBeyondTheWorkerLimitAreClosed() throws Exception {
        first = new SingleInstance(dir, 2, 20, 1500);
        first.claim();
        first.listen(received::add);
        int port = Integer.parseInt(Files.readString(dir.resolve(SingleInstance.PORT_FILE)).trim());
        List<Socket> holders = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                Socket holder = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
                holder.getOutputStream().write('D');
                holders.add(holder);
            }
            Thread.sleep(200);
            try (Socket extra = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
                extra.setSoTimeout(1000);
                assertEquals(-1, extra.getInputStream().read());
            }
        } finally {
            for (Socket holder : holders) holder.close();
        }
    }

    @Test
    void lockFolderIsPerUserAndOutsideTheDataFolder() {
        Path folder = SingleInstance.lockFolder(dir.toString(), "ana maria\\x");

        assertEquals(dir.resolve("DynaMusic-ana_maria_x"), folder);
    }

    private String sendRaw(String text) throws IOException {
        int port = Integer.parseInt(Files.readString(dir.resolve(SingleInstance.PORT_FILE)).trim());
        try (Socket socket = new Socket(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
            socket.shutdownOutput();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
