package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;

class AsyncWorker extends Worker implements Runnable {

    private final String name;
    private final int maxQueueSize;
    private final long maxFlushTimeMillis;
    private final AtomicBoolean started;
    private final BlockingQueue<InputLogEvent> queue;
    private final Object monitor;

    private Thread thread;

    AsyncWorker(AWSLogsStub awsLogsStub, String name, int maxQueueSize, long maxFlushTimeMillis) {
        super(awsLogsStub);
        this.name = name;
        this.maxQueueSize = maxQueueSize;
        this.maxFlushTimeMillis = maxFlushTimeMillis;
        started = new AtomicBoolean(false);
        queue = new LinkedBlockingDeque<InputLogEvent>();
        monitor = new Object();
    }

    @Override
    public synchronized void start() {
        super.start();
        if (started.compareAndSet(false, true)) {
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.setName(name + " Async Worker");
            thread.start();
        }
    }

    @Override
    public synchronized void stop() {
        if (started.compareAndSet(true, false)) {
            synchronized (monitor) {
                monitor.notifyAll();
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
    public void append(InputLogEvent event) {
        queue.offer(event);
        if (queue.size() >= maxQueueSize) {
            synchronized (monitor) {
                monitor.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        while (started.get()) {
            flush(false);
            try {
                synchronized (monitor) {
                    monitor.wait(maxFlushTimeMillis);
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        flush(true);
    }

    private void flush(boolean all) {
        try {
            if (!queue.isEmpty()) {
                List<InputLogEvent> events = new ArrayList<InputLogEvent>(maxQueueSize);
                while (true) {
                    queue.drainTo(events, maxQueueSize);
                    getAwsLogsStub().logEvents(events);
                    int size = queue.size();
                    if (size == 0 || (size <= maxQueueSize && !all)) {
                        break;
                    }
                    events.clear();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
