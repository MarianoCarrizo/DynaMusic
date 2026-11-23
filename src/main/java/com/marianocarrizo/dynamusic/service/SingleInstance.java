package com.marianocarrizo.dynamusic.service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;

public final class SingleInstance implements AutoCloseable {

    public enum Role { PRIMARY, SECONDARY }

    static final String LOCK_FILE = "instance.lock";
    static final String PORT_FILE = "instance.port";
    private static final String ACKNOWLEDGEMENT = "OK";
    private static final int DEFAULT_ATTEMPTS = 15;
    private static final long DEFAULT_RETRY_DELAY_MS = 200;
    private static final int SOCKET_TIMEOUT_MS = 2000;
    private static final int BACKLOG = 4;
    private static final int MAX_PORT = 65535;
    private static final byte[] LOOPBACK = {127, 0, 0, 1};

    private final Path folder;
    private final int attempts;
    private final long retryDelayMs;
    private FileChannel lockChannel;
    private FileLock lock;
    private ServerSocket server;

    public SingleInstance(Path folder) {
        this(folder, DEFAULT_ATTEMPTS, DEFAULT_RETRY_DELAY_MS);
    }

    SingleInstance(Path folder, int attempts, long retryDelayMs) {
        this.folder = folder;
        this.attempts = attempts;
        this.retryDelayMs = retryDelayMs;
    }

    public static SingleInstance inAppDataFolder() {
        return new SingleInstance(YouTubeDownloader.appDir());
    }

    public Role claim() {
        try {
            Files.createDirectories(folder);
            lockChannel = FileChannel.open(folder.resolve(LOCK_FILE),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        } catch (IOException e) {
            System.err.println("Single instance check unavailable: " + e.getMessage());
            closeQuietly(lockChannel);
            lockChannel = null;
            return Role.PRIMARY;
        }
        if (lock == null) {
            closeQuietly(lockChannel);
            lockChannel = null;
            return Role.SECONDARY;
        }
        deletePortFile();
        return Role.PRIMARY;
    }

    public boolean forward(OpenRequest request) {
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (deliver(request)) return true;
            if (!pause()) return false;
        }
        return false;
    }

    public void listen(Consumer<OpenRequest> onRequest) {
        if (lock == null || server != null) return;
        try {
            server = new ServerSocket(0, BACKLOG, InetAddress.getByAddress(LOOPBACK));
            Files.writeString(folder.resolve(PORT_FILE), Integer.toString(server.getLocalPort()));
        } catch (IOException e) {
            System.err.println("Could not listen for other launches: " + e.getMessage());
            closeQuietly(server);
            server = null;
            return;
        }
        ServerSocket accepting = server;
        Thread thread = new Thread(() -> acceptLoop(accepting, onRequest), "single-instance-listener");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        closeQuietly(server);
        server = null;
        if (lock != null) {
            deletePortFile();
            try {
                lock.release();
            } catch (IOException ignored) {
            }
            lock = null;
        }
        closeQuietly(lockChannel);
        lockChannel = null;
    }

    private void acceptLoop(ServerSocket accepting, Consumer<OpenRequest> onRequest) {
        while (!accepting.isClosed()) {
            try (Socket client = accepting.accept()) {
                serve(client, onRequest);
            } catch (IOException e) {
                if (accepting.isClosed()) return;
            }
        }
    }

    private void serve(Socket client, Consumer<OpenRequest> onRequest) throws IOException {
        if (!client.getInetAddress().isLoopbackAddress()) return;
        client.setSoTimeout(SOCKET_TIMEOUT_MS);
        byte[] message = client.getInputStream().readNBytes(OpenRequest.MAX_BYTES + 1);
        OpenRequest.decode(message).ifPresent(request -> {
            if (acknowledge(client)) onRequest.accept(request);
        });
    }

    private static boolean acknowledge(Socket client) {
        try {
            OutputStream out = client.getOutputStream();
            out.write(ACKNOWLEDGEMENT.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private boolean deliver(OpenRequest request) {
        int port = readPort();
        if (port < 0) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByAddress(LOOPBACK), port), SOCKET_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(request.encode());
            out.flush();
            socket.shutdownOutput();
            byte[] reply = socket.getInputStream().readNBytes(ACKNOWLEDGEMENT.length());
            return ACKNOWLEDGEMENT.equals(new String(reply, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }

    private int readPort() {
        try {
            int port = Integer.parseInt(Files.readString(folder.resolve(PORT_FILE)).trim());
            return port > 0 && port <= MAX_PORT ? port : -1;
        } catch (IOException | NumberFormatException e) {
            return -1;
        }
    }

    private boolean pause() {
        try {
            Thread.sleep(retryDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void deletePortFile() {
        try {
            Files.deleteIfExists(folder.resolve(PORT_FILE));
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) return;
        try {
            resource.close();
        } catch (Exception ignored) {
        }
    }
}
