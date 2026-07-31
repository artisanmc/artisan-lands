package fr.iban.lands.integration.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import fr.iban.lands.integration.claims.ClaimMarkerSynchronizer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BlueMapClaimVisualizationTest {
    @Test
    void synchronousEnableRegistrationAttachesWithoutRebuildingThenLaterEnableRebuilds() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        BlueMapAPI api = mock(BlueMapAPI.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        listeners.enableApiOnRegistration = api;

        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );

        verify(sink).attach(api);
        verify(synchronizer, never()).rebuild();

        listeners.onEnable.accept(api);

        verify(sink, times(2)).attach(api);
        verify(synchronizer).rebuild();
        visualization.close();
    }

    @Test
    void containsEnableFailureAndSkipsLaterEnableCallbacks() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization =
                new BlueMapClaimVisualization(sink, synchronizer, listeners, breaker);
        BlueMapAPI api = mock(BlueMapAPI.class);

        doThrow(new IllegalStateException("BlueMap unavailable"))
                .when(sink).attach(api);

        assertDoesNotThrow(() -> listeners.onEnable.accept(api));
        assertDoesNotThrow(() -> listeners.onEnable.accept(api));

        assertTrue(breaker.isOpen());
        verify(sink, times(1)).attach(api);
        verify(synchronizer, never()).rebuild();
        visualization.close();
    }

    @Test
    void attemptsDisableAfterCircuitOpensAndContainsDisableFailure() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization =
                new BlueMapClaimVisualization(sink, synchronizer, listeners, breaker);
        BlueMapAPI api = mock(BlueMapAPI.class);

        doThrow(new IllegalStateException("BlueMap unavailable"))
                .when(sink).attach(api);
        doThrow(new IllegalStateException("BlueMap cleanup unavailable"))
                .when(sink).detach(api);

        listeners.onEnable.accept(api);
        assertDoesNotThrow(() -> listeners.onDisable.accept(api));

        assertTrue(breaker.isOpen());
        verify(sink).detach(api);
        visualization.close();
    }

    @Test
    void rollsBackBothCallbacksWhenEnableRegistrationStoresThenFails() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RuntimeException failure = new IllegalStateException("enable registration failed");
        listeners.onEnableFailure = failure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new BlueMapClaimVisualization(
                        sink, synchronizer, listeners, newBreaker()
                ));

        assertSame(failure, thrown);
        assertTrue(listeners.registered.isEmpty());
        assertEquals(2, listeners.unregistered.size());
        assertNotSame(listeners.onEnable, listeners.unregistered.getFirst());
        assertSame(listeners.onEnable, listeners.unregistered.get(1));
    }

    @Test
    void rollsBackBothCallbacksInReverseOrderWhenDisableRegistrationStoresThenFails() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        LinkageError failure = new NoClassDefFoundError("disable registration failed");
        listeners.onDisableFailure = failure;

        LinkageError thrown = assertThrows(LinkageError.class,
                () -> new BlueMapClaimVisualization(
                        sink, synchronizer, listeners, newBreaker()
                ));

        assertSame(failure, thrown);
        assertTrue(listeners.registered.isEmpty());
        assertEquals(List.of(listeners.onDisable, listeners.onEnable), listeners.unregistered);
    }

    @Test
    void suppressesBothRollbackFailuresOnDisableRegistrationFailure() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RuntimeException failure = new IllegalStateException("disable registration failed");
        RuntimeException disableRollbackFailure =
                new IllegalStateException("disable rollback failed");
        LinkageError enableRollbackFailure =
                new NoClassDefFoundError("enable rollback failed");
        listeners.onDisableFailure = failure;
        listeners.unregisterFailure = disableRollbackFailure;
        listeners.secondUnregisterFailure = enableRollbackFailure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new BlueMapClaimVisualization(
                        sink, synchronizer, listeners, newBreaker()
                ));

        assertSame(failure, thrown);
        assertArrayEquals(
                new Throwable[]{disableRollbackFailure, enableRollbackFailure},
                thrown.getSuppressed()
        );
        assertEquals(2, listeners.unregistered.size());
    }

    @Test
    void rethrowsRegistrationFailureWhenRollbackThrowsTheSameInstance() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RuntimeException failure = new IllegalStateException("registration failed");
        listeners.onDisableFailure = failure;
        listeners.unregisterFailure = failure;
        listeners.secondUnregisterFailure = failure;

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> new BlueMapClaimVisualization(
                        sink, synchronizer, listeners, newBreaker()
                ));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(2, listeners.unregistered.size());
    }

    @Test
    void continuesCloseCleanupAfterEnableUnregisterFailure() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        listeners.unregisterFailure = new IllegalStateException("enable unregister failed");
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );

        assertDoesNotThrow(visualization::close);

        assertAll(
                () -> assertEquals(2, listeners.unregistered.size()),
                () -> assertSame(listeners.onEnable, listeners.unregistered.get(0)),
                () -> assertSame(listeners.onDisable, listeners.unregistered.get(1))
        );
        verify(synchronizer).close();
    }

    @Test
    void scheduledRefreshChecksForDirtyClaimsOnEveryCallback() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );

        visualization.startRefresh(scheduler, 6_000L);

        assertEquals(6_000L, scheduler.delayTicks);
        assertEquals(6_000L, scheduler.periodTicks);
        verify(synchronizer, never()).refreshIfDirty();

        scheduler.action.run();
        scheduler.action.run();

        verify(synchronizer, times(2)).refreshIfDirty();
        visualization.close();
    }

    @Test
    void containsScheduledRefreshFailureAndOpensSharedCircuit() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, new RecordingListenerRegistry(), breaker
        );
        doThrow(new IllegalStateException("refresh failed"))
                .when(synchronizer).refreshIfDirty();

        visualization.startRefresh(scheduler, 6_000L);

        assertDoesNotThrow(scheduler.action::run);
        assertDoesNotThrow(scheduler.action::run);
        assertTrue(breaker.isOpen());
        verify(synchronizer).refreshIfDirty();
        visualization.close();
    }

    @Test
    void containsScheduledRefreshLinkageError() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, new RecordingListenerRegistry(), breaker
        );
        doThrow(new NoClassDefFoundError("BlueMap API missing"))
                .when(synchronizer).refreshIfDirty();
        visualization.startRefresh(scheduler, 6_000L);

        assertDoesNotThrow(scheduler.action::run);
        assertTrue(breaker.isOpen());
        visualization.close();
    }

    @Test
    void letsFatalJvmErrorEscapeScheduledRefresh() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, new RecordingListenerRegistry(), breaker
        );
        doThrow(new OutOfMemoryError("fatal"))
                .when(synchronizer).refreshIfDirty();
        visualization.startRefresh(scheduler, 6_000L);

        assertThrows(OutOfMemoryError.class, scheduler.action::run);
        assertFalse(breaker.isOpen());
        visualization.close();
    }

    @Test
    void rethrowsRefreshSchedulingFailure() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        RuntimeException failure = new IllegalStateException("scheduler stopped");
        scheduler.scheduleFailure = failure;
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, new RecordingListenerRegistry(), newBreaker()
        );

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> visualization.startRefresh(scheduler, 6_000L)
        );

        assertSame(failure, thrown);
        visualization.close();
    }

    @Test
    void closeCancelsRefreshOnceBeforeClosingSynchronizerAndIsIdempotent() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        List<String> events = new ArrayList<>();
        scheduler.events = events;
        doAnswer(invocation -> {
            events.add("synchronizer close");
            return null;
        }).when(synchronizer).close();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );
        visualization.startRefresh(scheduler, 6_000L);

        visualization.close();
        visualization.close();

        assertEquals(List.of("refresh cancel", "synchronizer close"), events);
        assertEquals(1, scheduler.cancelCalls);
        assertEquals(2, listeners.unregistered.size());
        verify(synchronizer).close();
    }

    @Test
    void refreshCancellationFailureDoesNotSkipOtherCleanup() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        RecordingRefreshScheduler scheduler = new RecordingRefreshScheduler();
        scheduler.cancelFailure = new IllegalStateException("cancel failed");
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );
        visualization.startRefresh(scheduler, 6_000L);

        assertDoesNotThrow(visualization::close);

        assertEquals(1, scheduler.cancelCalls);
        assertEquals(2, listeners.unregistered.size());
        verify(synchronizer).close();
    }

    @Test
    void letsFatalJvmErrorsEscapeEnableCallbacks() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        BlueMapFailureCircuitBreaker breaker = newBreaker();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, breaker
        );
        BlueMapAPI api = mock(BlueMapAPI.class);

        doThrow(new OutOfMemoryError("fatal")).when(sink).attach(api);

        assertThrows(OutOfMemoryError.class, () -> listeners.onEnable.accept(api));
        assertFalse(breaker.isOpen());
        visualization.close();
    }

    @Test
    void ownsStableEnableAndDisableCallbacksUntilClose() {
        BlueMapMarkerSink sink = mock(BlueMapMarkerSink.class);
        ClaimMarkerSynchronizer synchronizer = mock(ClaimMarkerSynchronizer.class);
        RecordingListenerRegistry listeners = new RecordingListenerRegistry();
        BlueMapClaimVisualization visualization = new BlueMapClaimVisualization(
                sink, synchronizer, listeners, newBreaker()
        );
        BlueMapAPI api = mock(BlueMapAPI.class);

        listeners.onEnable.accept(api);
        listeners.onDisable.accept(api);
        visualization.close();

        var lifecycleOrder = inOrder(sink, synchronizer);
        lifecycleOrder.verify(sink).attach(api);
        lifecycleOrder.verify(synchronizer).rebuild();
        lifecycleOrder.verify(sink).detach(api);
        lifecycleOrder.verify(synchronizer).close();
        assertAll(
                () -> assertEquals(2, listeners.unregistered.size()),
                () -> assertSame(listeners.onEnable, listeners.unregistered.get(0)),
                () -> assertSame(listeners.onDisable, listeners.unregistered.get(1))
        );
    }

    private static BlueMapFailureCircuitBreaker newBreaker() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return new BlueMapFailureCircuitBreaker(logger);
    }

    private static final class RecordingListenerRegistry
            implements BlueMapClaimVisualization.ListenerRegistry {
        private Consumer<BlueMapAPI> onEnable;
        private Consumer<BlueMapAPI> onDisable;
        private final List<Consumer<BlueMapAPI>> registered = new ArrayList<>();
        private final List<Consumer<BlueMapAPI>> unregistered = new ArrayList<>();
        private Throwable onEnableFailure;
        private Throwable onDisableFailure;
        private Throwable unregisterFailure;
        private Throwable secondUnregisterFailure;
        private BlueMapAPI enableApiOnRegistration;

        @Override
        public void onEnable(Consumer<BlueMapAPI> listener) {
            onEnable = listener;
            registered.add(listener);
            throwSupported(onEnableFailure);
            if (enableApiOnRegistration != null) {
                listener.accept(enableApiOnRegistration);
            }
        }

        @Override
        public void onDisable(Consumer<BlueMapAPI> listener) {
            onDisable = listener;
            registered.add(listener);
            throwSupported(onDisableFailure);
        }

        @Override
        public void unregister(Consumer<BlueMapAPI> listener) {
            unregistered.add(listener);
            registered.remove(listener);
            throwSupported(unregistered.size() == 1
                    ? unregisterFailure
                    : secondUnregisterFailure);
        }

        private static void throwSupported(Throwable failure) {
            if (failure instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (failure instanceof LinkageError linkageError) {
                throw linkageError;
            }
        }
    }

    private static final class RecordingRefreshScheduler
            implements BlueMapClaimVisualization.RefreshScheduler {
        private Runnable action;
        private long delayTicks;
        private long periodTicks;
        private int cancelCalls;
        private RuntimeException scheduleFailure;
        private RuntimeException cancelFailure;
        private List<String> events = new ArrayList<>();

        @Override
        public BlueMapClaimVisualization.Cancellable schedule(
                Runnable action,
                long delayTicks,
                long periodTicks
        ) {
            if (scheduleFailure != null) {
                throw scheduleFailure;
            }
            this.action = action;
            this.delayTicks = delayTicks;
            this.periodTicks = periodTicks;
            return () -> {
                cancelCalls++;
                events.add("refresh cancel");
                if (cancelFailure != null) {
                    throw cancelFailure;
                }
            };
        }
    }
}
