package com.gtnewhorizons.angelica.debug.profiling;

import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder.Session;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder.StopResult;
import com.gtnewhorizons.angelica.debug.profiling.AsprofRecorder.StopStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsprofRecorderTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "default opts, 'event=wall,interval=5ms,alloc=512k,lock=10ms', /abs/x.jfr, 'start,jfr,event=wall,interval=5ms,alloc=512k,lock=10ms,file=/abs/x.jfr'",
        "custom opts, 'event=cpu,interval=1ms', /abs/y.jfr, 'start,jfr,event=cpu,interval=1ms,file=/abs/y.jfr'",
        "empty opts, '', /abs/z.jfr, 'start,jfr,file=/abs/z.jfr'",
    })
    void buildsStartCommand(String name, String opts, String file, String expected) {
        assertEquals(expected, AsprofRecorder.command(opts, file));
    }

    @Test
    void fileNameMatchesShape() {
        final long fixedMillis = 1_700_000_000_000L;
        final String circuit = AsprofRecorder.fileName("circuit", "jfr", fixedMillis);
        final String manual = AsprofRecorder.fileName("manual", "jfr", fixedMillis);
        assertTrue(circuit.matches("angelica-circuit-\\d{8}-\\d{6}\\.jfr"));
        assertTrue(manual.matches("angelica-manual-\\d{8}-\\d{6}\\.jfr"));
    }

    static Stream<Arguments> firstLines() {
        return Stream.of(
            Arguments.of("null", null, ""),
            Arguments.of("empty", "", ""),
            Arguments.of("single line", "Profiler already started", "Profiler already started"),
            Arguments.of("multi line", "Profiler already started\nat some.Stack\nat other.Stack", "Profiler already started"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstLines")
    void firstLine(String name, String input, String expected) {
        assertEquals(expected, AsprofRecorder.firstLine(input));
    }

    private static final class Commands implements Session.CommandExecutor {
        final List<String> calls = new ArrayList<>();
        Throwable failure;

        @Override
        public String execute(String command) throws Throwable {
            calls.add(command);
            if (failure != null) throw failure;
            return "native status";
        }
    }

    private static long start(Session session, String path) throws Throwable {
        return session.start(path, AsprofRecorder.command("event=cpu", path));
    }

    @Test
    void successfulStartsAcquireIncreasingOwnershipAndDispatchCommands() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        assertEquals(0, session.recordingId());
        final long first = start(session, "flyby.jfr");
        assertTrue(first > 0);
        assertEquals(first, session.recordingId());
        assertEquals("flyby.jfr", session.outputPath());
        final StopResult stopped = session.stopIfRecording(first);
        assertEquals(StopStatus.STOPPED, stopped.status());
        assertEquals("flyby.jfr", stopped.path());
        assertNull(stopped.failure());
        assertEquals(0, session.recordingId());
        assertNull(session.outputPath());
        final long second = start(session, "manual.jfr");
        assertTrue(second > first);
        assertEquals(List.of("start,jfr,event=cpu,file=flyby.jfr", "stop", "start,jfr,event=cpu,file=manual.jfr"), commands.calls);
    }

    @Test
    void failedStartAcquiresNoOwnershipAndConsumesNoIdentity() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final IllegalStateException failure = new IllegalStateException("native start failed");
        commands.failure = failure;
        assertSame(failure, assertThrows(IllegalStateException.class, () -> start(session, "failed.jfr")));
        assertEquals(0, session.recordingId());
        assertNull(session.outputPath());
        assertEquals(StopStatus.NO_MATCH, session.stop().status());
        commands.failure = null;
        assertEquals(1, start(session, "valid.jfr"));
        assertEquals(2, commands.calls.size());
    }

    @Test
    void activeStartDoesNotDispatchOrTransferOwnership() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final long id = start(session, "manual.jfr");
        assertThrows(IllegalStateException.class, () -> start(session, "flyby.jfr"));
        assertEquals(id, session.recordingId());
        assertEquals("manual.jfr", session.outputPath());
        assertEquals(1, commands.calls.size());
    }

    @Test
    void finishAndCancelStopOnlyOnce() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final long id = start(session, "flyby.jfr");
        assertEquals(StopStatus.STOPPED, session.stopIfRecording(id).status());
        assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(id).status());
        assertEquals(StopStatus.NO_MATCH, session.stop().status());
        assertEquals(List.of("start,jfr,event=cpu,file=flyby.jfr", "stop"), commands.calls);
    }

    @Test
    void manualRestartSurvivesStaleFinishEvenWhenPathIsReused() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final long flyby = start(session, "same.jfr");
        assertEquals(StopStatus.STOPPED, session.stop().status());
        final long manual = start(session, "same.jfr");
        assertTrue(manual > flyby);
        assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(flyby).status());
        assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(0).status());
        assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(manual + 1).status());
        assertEquals(manual, session.recordingId());
        assertEquals(3, commands.calls.size());
        final StopResult shutdown = session.stop();
        assertEquals(StopStatus.STOPPED, shutdown.status());
        assertEquals("same.jfr", shutdown.path());
        assertEquals(4, commands.calls.size());
    }

    @Test
    void failedStopReturnsNativeFailureAndReleasesOwnership() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final long id = start(session, "flyby.jfr");
        final UnsupportedOperationException failure = new UnsupportedOperationException("native stop failed\nmore detail");
        commands.failure = failure;
        final StopResult result = session.stopIfRecording(id);
        assertEquals(StopStatus.FAILED, result.status());
        assertSame(failure, result.failure());
        assertEquals("native stop failed", result.error());
        assertEquals("flyby.jfr", result.path());
        assertEquals(0, session.recordingId());
        assertNull(session.outputPath());
        assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(id).status());
        commands.failure = null;
        assertTrue(start(session, "manual.jfr") > id);
    }

    @Test
    void statusDispatchDoesNotChangeOwnership() throws Throwable {
        final Commands commands = new Commands();
        final Session session = new Session(commands);
        final long id = start(session, "manual.jfr");
        assertEquals("native status", session.status());
        assertEquals(id, session.recordingId());
        final LinkageError failure = new LinkageError("status unavailable");
        commands.failure = failure;
        assertSame(failure, assertThrows(LinkageError.class, session::status));
        assertEquals(id, session.recordingId());
    }

    @Test
    void conditionalStopAndReplacementStartSerializeNativeDispatch() throws Throwable {
        final CountDownLatch stopping = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Commands commands = new Commands();
        final Session session = new Session(command -> {
            if (command.equals("stop")) {
                stopping.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("stop timeout");
            }
            return commands.execute(command);
        });
        final long id = start(session, "flyby.jfr");
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<StopResult> stopped = executor.submit(() -> session.stopIfRecording(id));
            assertTrue(stopping.await(5, TimeUnit.SECONDS));
            final Future<Long> replacement = executor.submit(() -> {
                try {
                    return start(session, "manual.jfr");
                } catch (Throwable t) {
                    throw new IllegalStateException(t);
                }
            });
            assertFalse(replacement.isDone());
            release.countDown();
            assertEquals(StopStatus.STOPPED, stopped.get(5, TimeUnit.SECONDS).status());
            final long manual = replacement.get(5, TimeUnit.SECONDS);
            assertTrue(manual > id);
            assertEquals(StopStatus.NO_MATCH, session.stopIfRecording(id).status());
            assertEquals(List.of("start,jfr,event=cpu,file=flyby.jfr", "stop", "start,jfr,event=cpu,file=manual.jfr"), commands.calls);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
