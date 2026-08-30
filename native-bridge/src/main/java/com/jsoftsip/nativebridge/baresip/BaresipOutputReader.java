package com.jsoftsip.nativebridge.baresip;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BaresipOutputReader {

    private final BufferedReader reader;

    private final List<BaresipOutputListener> listeners = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    private Thread worker;

    public BaresipOutputReader(BufferedReader reader) {

        this.reader = reader;
    }

    public void addListener(BaresipOutputListener listener) {

        listeners.add(listener);
    }

    public synchronized void start() {

        if (running) {
            return;
        }

        running = true;

        worker = Thread.ofVirtual().name("baresip-output-reader").start(this::runLoop);
    }

    public synchronized void stop() {

        running = false;

        if (worker != null) {
            worker.interrupt();
        }
    }

    private void runLoop() {

        while (running) {

            try {

                String line = reader.readLine();

                if (line == null) {
                    return;
                }

                listeners.forEach(listener -> listener.onLine(line));

            } catch (IOException exception) {

                // Closing the underlying stream while stopping is
                // the normal shutdown path (the process pipe closes
                // on exit), not a failure. Only report unexpected
                // I/O errors while the reader was still active.
                if (!running) {
                    return;
                }

                BaresipLog.error("Baresip output reader I/O error", exception);

                return;
            }
        }
    }
}