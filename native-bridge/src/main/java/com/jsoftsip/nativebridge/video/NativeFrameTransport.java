package com.jsoftsip.nativebridge.video;

import com.jsoftsip.nativebridge.baresip.BaresipLog;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TCP server that receives raw video frames from the custom
 * baresip vidisp module on loopback.
 *
 * <p>One daemon accept thread owns the ServerSocket, every
 * accepted connection gets its own daemon reader thread that
 * parses frames with {@link FrameWireReader} and forwards them to
 * the {@link FrameRouter}. EOF and malformed data close only that
 * socket and the server keeps accepting, so a baresip restart
 * heals by itself. A bind failure degrades to a not-running
 * transport instead of crashing the app.
 */
public final class NativeFrameTransport {

    private final String host;

    private final int port;

    private final FrameRouter router;

    private ServerSocket serverSocket;

    private Thread acceptThread;

    private volatile boolean running;

    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();

    public NativeFrameTransport(String host, int port, FrameRouter router) {

        this.host = host;
        this.port = port;
        this.router = router;
    }

    /**
     * Binds the server socket and starts accepting. A bind
     * failure is logged as a warning and leaves the transport
     * not running, which the UI surfaces as a placeholder.
     */
    public void start() {

        try {

            serverSocket = new ServerSocket();

            serverSocket.setReuseAddress(true);
            serverSocket.bind(new java.net.InetSocketAddress(InetAddress.getByName(host), port));

        } catch (IOException exception) {

            BaresipLog.warn("Video frame transport bind failed on " + host + ":" + port
                + " - video will show the placeholder", exception);

            running = false;
            return;
        }

        running = true;

        acceptThread = new Thread(this::acceptLoop, "video-accept");

        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** Whether the transport is bound and accepting. */
    public boolean isRunning() {

        return running;
    }

    /**
     * Actual bound port, useful when the transport was created
     * with port 0 (ephemeral). Returns -1 when not running.
     */
    public int boundPort() {

        ServerSocket socket = serverSocket;

        if (socket == null || !running) {
            return -1;
        }

        return socket.getLocalPort();
    }

    /**
     * Closes the server socket and every active connection, then
     * waits for the accept thread to finish.
     */
    public void shutdown() {

        running = false;

        ServerSocket socket = serverSocket;

        if (socket != null) {

            try {
                socket.close();
            } catch (IOException exception) {
                BaresipLog.debug("Video frame transport close failed", exception);
            }
        }

        for (Socket active : activeSockets) {

            try {
                active.close();
            } catch (IOException ignored) {
                // already closed by the reader
            }
        }

        Thread thread = acceptThread;

        if (thread != null) {

            try {
                thread.join(2_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void acceptLoop() {

        while (running) {

            final Socket socket;

            try {

                socket = serverSocket.accept();

            } catch (SocketException exception) {

                // server socket closed on shutdown
                return;

            } catch (IOException exception) {

                if (!running) {
                    return;
                }

                BaresipLog.debug("Video frame accept failed, retrying", exception);

                continue;
            }

            activeSockets.add(socket);

            Thread reader = new Thread(() -> readLoop(socket), "video-frame-reader");

            reader.setDaemon(true);
            reader.start();
        }
    }

    private void readLoop(Socket socket) {

        try (InputStream in = socket.getInputStream()) {

            while (running) {

                Optional<FrameWireReader.WireFrame> optional;

                try {

                    optional = FrameWireReader.read(in);

                } catch (IOException | WireFormatException exception) {

                    BaresipLog.debug("Video frame stream failed for " + socket.getRemoteSocketAddress()
                        + " - closing and re-accepting", exception);

                    break;
                }

                if (optional.isEmpty()) {

                    // clean EOF, the peer closed the connection
                    break;
                }

                FrameWireReader.WireFrame frame = optional.get();

                try {

                    router.route(frame.aor(), frame.width(), frame.height(), frame.pixelFormat(),
                                 frame.timestampNanos(), frame.stride(), frame.pixels());

                } catch (RuntimeException exception) {

                    // a broken router must never kill the reader
                    BaresipLog.warn("Video frame router failed for " + frame.aor(), exception);

                    break;
                }
            }

        } catch (IOException exception) {

            BaresipLog.debug("Video frame reader stopped for " + socket.getRemoteSocketAddress(), exception);

        } finally {

            try {
                socket.close();
            } catch (IOException ignored) {
                // already closed
            }

            activeSockets.remove(socket);
        }
    }
}