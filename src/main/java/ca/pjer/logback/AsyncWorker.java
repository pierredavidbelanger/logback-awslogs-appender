package ca.pjer.logback;

import static ca.pjer.logback.AwsLogsAppender.MAX_BATCH_LOG_EVENTS;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.amazonaws.services.logs.model.InputLogEvent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

class AsyncWorker<E> extends Worker<E> implements Runnable {

    private final int maxBatchLogEvents;
    private final int discardThreshold;
    private final AtomicBoolean running;
    private final BlockingQueue<InputLogEvent> queue;
    private final AtomicLong lostCount;

    private Thread thread;

    AsyncWorker(AwsLogsAppender<E> awsLogsAppender) {
        super(awsLogsAppender);
        maxBatchLogEvents = awsLogsAppender.getMaxBatchLogEvents();
        discardThreshold = (int) Math.ceil(maxBatchLogEvents * 1.5);
        running = new AtomicBoolean(false);
        queue = new ArrayBlockingQueue<>(maxBatchLogEvents * 2);
        lostCount = new AtomicLong(0);
    }

    @Override
    public synchronized void start() {
        super.start();
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.setName(getAwsLogsAppender().getName() + " Async Worker");
            thread.start();
        }
    }

    @Override
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            synchronized (running) {
                running.notifyAll();
            }
            if (thread != null) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    thread.interrupt();
                }
                thread = null;
            }
            queue.clear();
        }
        super.stop();
    }

    @Override
    public void append(E event) {
        // don't log if discardThreshold is met and event is not important (< WARN)
        if (queue.size() >= discardThreshold && !isImportant(event)) {
            lostCount.incrementAndGet();
            synchronized (running) {
                running.notifyAll();
            }
            return;
        }
        InputLogEvent logEvent = asInputLogEvent(event);
        // are we allowed to block ?
        if (getAwsLogsAppender().getMaxBlockTimeMillis() > 0) {
            // we are allowed to block, offer uninterruptibly for the configured maximum blocking time
            boolean interrupted = false;
            long until = System.currentTimeMillis() + getAwsLogsAppender().getMaxBlockTimeMillis();
            try {
                long now = System.currentTimeMillis();
                while (now < until) {
                    try {
                        if (!queue.offer(logEvent, until - now, TimeUnit.MILLISECONDS)) {
                            lostCount.incrementAndGet();
                        }
                        break;
                    } catch (InterruptedException e) {
                        interrupted = true;
                        now = System.currentTimeMillis();
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // we are not allowed to block, offer without blocking
            if (!queue.offer(logEvent)) {
                lostCount.incrementAndGet();
            }
        }
        // trigger a flush if queue is full
        if (queue.size() >= maxBatchLogEvents) {
            synchronized (running) {
                running.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            flush(false);
            try {
                synchronized (running) {
                    if (running.get()) {
                        running.wait(getAwsLogsAppender().getMaxFlushTimeMillis());
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        flush(true);
    }

    private boolean isImportant(E event) {
        if (event instanceof ILoggingEvent) {
            return ((ILoggingEvent) event).getLevel().isGreaterOrEqual(Level.WARN);
        } else {
            return false;
        }
    }

    private void flush(boolean all) {
        try {
            long lostCount = this.lostCount.getAndSet(0);
            if (lostCount > 0) {
                getAwsLogsAppender().addWarn(lostCount + " events lost");
            }
            if (!queue.isEmpty()) {
                do {
                    Collection<InputLogEvent> batch = drainBatchFromQueue();
                    getAwsLogsAppender().getAwsLogsStub().logEvents(batch);
                } while (queue.size() >= maxBatchLogEvents || (all && !queue.isEmpty()));
            }
        } catch (Exception e) {
            getAwsLogsAppender().addError("Unable to flush events to AWS", e);
        }
    }

    // See http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
    private static final int MAX_BATCH_SIZE = 1048576;

    private Collection<InputLogEvent> drainBatchFromQueue() {
        Deque<InputLogEvent> batch = new ArrayDeque<>(maxBatchLogEvents);
        queue.drainTo(batch, MAX_BATCH_LOG_EVENTS);
        int batchSize = batchSize(batch);
        while (batchSize > MAX_BATCH_SIZE) {
            InputLogEvent removed = batch.removeLast();
            batchSize -= eventSize(removed);
            if (!queue.offer(removed)) {
                getAwsLogsAppender().addWarn("Failed requeing message from too big batch");
            }
        }
        return batch;
    }

    private static int batchSize(Collection<InputLogEvent> batch) {
        int size = 0;
        for (InputLogEvent event : batch) {
            size += eventSize(event);
        }
        return size;
    }
}
