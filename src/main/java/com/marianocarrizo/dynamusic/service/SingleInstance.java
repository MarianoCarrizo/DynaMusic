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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SingleInstance implements AutoCloseable {

    public enum Role { PRIMARY, SECONDARY }

    static final String LOCK_FILE = "instance.lock";
    static final String PORT_FILE = "instance.port";
    private static final String ACKNOWLEDGEMENT = "OK";
    private static final int DEFAULT_ATTEMPTS = 15;
    private static final long DEFAULT_RETRY_DELAY_MS = 200;
    private static final int SOCKET_TIMEOUT_MS = 2000;
    private static final long DEFAULT_CONNECTION_DEADLINE_MS = 2000;
    private static final int WORKER_THREADS = 2;
    private static final String LOCK_FOLDER_PREFIX = "DynaMusic-";
    private static final int BACKLOG = 4;
    private static final int MAX_PORT = 65535;
    private static final byte[] LOOPBACK = {127, 0, 0, 1};

    private final Path folder;
    private final int attempts;
    private final long retryDelayMs;
    private final long connectionDeadlineMs;
    private ExecutorService workers;
    private FileChannel lockChannel;
    private FileLock lock;
    private ServerSocket server;

    public SingleInstance(Path folder) {
        this(folder, DEFAULT_ATTEMPTS, DEFAULT_RETRY_DELAY_MS, DEFAULT_CONNECTION_DEADLINE_MS);
    }

    SingleInstance(Path folder, int attempts, long retryDelayMs) {
        this(folder, attempts, retryDelayMs, DEFAULT_CONNECTION_DEADLINE_MS);
    }

    SingleInstance(Path folder, int attempts, long retryDelayMs, long connectionDeadlineMs) {
        this.folder = folder;
        this.attempts = attempts;
        this.retryDelayMs = retryDelayMs;
        this.connectionDeadlineMs = connectionDeadlineMs;
    }

    public static SingleInstance forCurrentUser() {
        return new SingleInstance(lockFolder(System.getProperty("java.io.tmpdir"), System.getProperty("user.name")));
    }

    static Path lockFolder(String tempDirectory, String userName) {
        String safeUser = userName == null ? "" : userName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return Path.of(tempDirectory, LOCK_FOLDER_PREFIX + safeUser);
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
        workers = newWorkers();
        ExecutorService pool = workers;
        Thread thread = new Thread(() -> acceptLoop(accepting, pool, onRequest), "single-instance-listener");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        closeQuietly(server);
        server = null;
        if (workers != null) {
            workers.shutdownNow();
            workers = null;
        }
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

    private static ExecutorService newWorkers() {
        return new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread worker = new Thread(runnable, "single-instance-worker");
                    worker.setDaemon(true);
                    return worker;
                });
    }

    private void acceptLoop(ServerSocket accepting, ExecutorService pool, Consumer<OpenRequest> onRequest) {
        while (!accepting.isClosed()) {
            try {
                Socket client = accepting.accept();
                submit(pool, client, onRequest);
            } catch (IOException e) {
                if (accepting.isClosed()) return;
            }
        }
    }

    private void submit(ExecutorService pool, Socket client, Consumer<OpenRequest> onRequest) {
        try {
            pool.execute(() -> handle(client, onRequest));
        } catch (RejectedExecutionException e) {
            closeQuietly(client);
        }
    }

    private void handle(Socket client, Consumer<OpenRequest> onRequest) {
        try (client) {
            serve(client, onRequest);
        } catch (IOException ignored) {
        }
    }

    private void serve(Socket client, Consumer<OpenRequest> onRequest) throws IOException {
        if (!client.getInetAddress().isLoopbackAddress()) return;
        byte[] message = DeadlineReader.read(client, OpenRequest.MAX_BYTES + 1, connectionDeadlineMs);
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
