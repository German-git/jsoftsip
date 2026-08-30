package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PactlDeviceListerTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesSinkNamesFromShortListing() {

        String output = "54\talsa_output.pci-0000_00_1f.3.analog-stereo\tPipeWire\ts32le 2ch 48000Hz\tSUSPENDED\n"
            + "55\tbluez_output.44_78_3E_EE_15_01.1\tPipeWire\ts16le 2ch 48000Hz\tRUNNING\n";

        List<String> sinks = PactlDeviceLister.parseShortListing(output);

        assertEquals(List.of("alsa_output.pci-0000_00_1f.3.analog-stereo", "bluez_output.44_78_3E_EE_15_01.1"), sinks);
    }

    @Test
    void parsesSourceNamesFromShortListing() {

        String output = "61\talsa_input.usb-046d_0825.mono-fallback\tPipeWire\ts16le 1ch 48000Hz\tRUNNING\n";

        List<String> sources = PactlDeviceLister.parseShortListing(output);

        assertEquals(List.of("alsa_input.usb-046d_0825.mono-fallback"), sources);
    }

    @Test
    void emptyOutputYieldsEmptyList() {

        // companion tests above prove non-empty parsing works, so
        // empty here means the parser really processed the input
        assertTrue(PactlDeviceLister.parseShortListing("").isEmpty());
        assertTrue(PactlDeviceLister.parseShortListing("\n\n").isEmpty());
    }

    @Test
    void malformedLinesAreSkipped() {

        String output = "garbage\n" + "\n" + "42\tvalid.name\tPipeWire\ts32le 2ch 48000Hz\tIDLE\n" + "singlefield\n";

        assertEquals(List.of("valid.name"), PactlDeviceLister.parseShortListing(output));
    }

    @Test
    void listSinksUsesFixedArgv() {

        List<String> captured = new ArrayList<>();

        PactlDeviceLister lister = new PactlDeviceLister() {

            @Override
            String run(String... args) {

                captured.addAll(List.of(args));

                return "1\tsink_a\tdriver\tfmt\tIDLE\n";
            }
        };

        assertEquals(List.of("sink_a"), lister.listSinks());

        // injection guard: the argv must stay a fixed literal,
        // never user controlled input
        assertEquals(List.of("pactl", "list", "sinks", "short"), captured);
    }

    @Test
    void listSourcesUsesFixedArgv() {

        List<String> captured = new ArrayList<>();

        PactlDeviceLister lister = new PactlDeviceLister() {

            @Override
            String run(String... args) {

                captured.addAll(List.of(args));

                return "7\tsource_a\tdriver\tfmt\tIDLE\n";
            }
        };

        assertEquals(List.of("source_a"), lister.listSources());

        assertEquals(List.of("pactl", "list", "sources", "short"), captured);
    }

    @Test
    void missingPactlYieldsNullNotCrash() {

        PactlDeviceLister lister = new PactlDeviceLister() {

            @Override
            String run(String... args) throws IOException {

                throw new IOException("Cannot run program \"pactl\"");
            }
        };

        assertNull(lister.listSinks());
        assertNull(lister.listSources());
    }

    @Test
    void nonZeroExitYieldsNull() {

        // run returns null when pactl exits non-zero, and the
        // lister must propagate that as a degraded null result
        PactlDeviceLister lister = new PactlDeviceLister() {

            @Override
            String run(String... args) {

                return null;
            }
        };

        assertNull(lister.listSinks());
    }

    @Test
    void realProcessShortListingIsParsedEndToEnd() throws IOException, InterruptedException {

        // real /bin/sh child exercises the actual run() code
        // path instead of a stubbed override
        Path script = executableScript("#!/bin/sh\nprintf '54\\tsink_a\\n55\\tsink_b\\n'\n");

        PactlDeviceLister lister = new PactlDeviceLister() {

            @Override
            String run(String... args) throws IOException, InterruptedException {

                return super.run(script.toString(), "list", "sinks", "short");
            }
        };

        assertEquals(List.of("sink_a", "sink_b"), lister.listSinks());
    }

    @Test
    void nonZeroExitRealProcessYieldsNull() throws IOException, InterruptedException {

        Path script = executableScript("#!/bin/sh\necho boom\nexit 4\n");

        PactlDeviceLister lister = new PactlDeviceLister(250);

        assertNull(lister.run(script.toString()));
    }

    @Test
    void wedgedProcessTimesOutReturnsNullAndKillsChild() throws IOException, InterruptedException {

        // exec replaces the shell so destroyForcibly kills the
        // sleeper itself rather than only the wrapper script
        Path pidFile = tempDir.resolve("wedged.pid");

        Path script = executableScript("#!/bin/sh\necho $$ > " + pidFile + "\nexec sleep 30\n");

        PactlDeviceLister lister = new PactlDeviceLister(250);

        long startNanos = System.nanoTime();

        String output = lister.run(script.toString());

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;

        assertNull(output, "a wedged pactl must degrade exactly like an unavailable one");

        assertTrue(elapsedMillis < 5_000, "must give up well under the 30s sleep, took " + elapsedMillis + " ms");

        assertKilled(pidFile);
    }

    private Path executableScript(String body) throws IOException {

        Path script = tempDir.resolve("fake-pactl.sh");

        Files.writeString(script, body);

        Files.setPosixFilePermissions(script,
                                      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                                                 PosixFilePermission.OWNER_EXECUTE));

        return script;
    }

    private static void assertKilled(Path pidFile) throws IOException, InterruptedException {

        long pid = Long.parseLong(Files.readString(pidFile).trim());

        long deadline = System.currentTimeMillis() + 2_000;

        while (ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)) {

            if (System.currentTimeMillis() > deadline) {
                fail("the wedged child must be destroyed after the timeout");
            }

            Thread.sleep(10);
        }
    }
}
