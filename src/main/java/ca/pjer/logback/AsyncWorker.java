package ca.pjer.logback;

import com.amazonaws.services.logs.model.InputLogEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class AsyncWorker extends Worker implements Runnable {

    private final String name;
    private final int maxQueueSize;
    private final long maxFlushTimeMillis;
    private final AtomicBoolean started;
    private final List<InputLogEvent> queue;

    private Thread thread;

    AsyncWorker(AWSLogsStub awsLogsStub, String name, int maxQueueSize, long maxFlushTimeMillis) {
        super(awsLogsStub);
        this.name = name;
        this.maxQueueSize = maxQueueSize;
        this.maxFlushTimeMillis = maxFlushTimeMillis;
        started = new AtomicBoolean(false);
        queue = new ArrayList<InputLogEvent>(maxQueueSize);
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
            synchronized (queue) {
                queue.notifyAll();
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
        synchronized (queue) {
            queue.add(event);
            if (queue.size() >= maxQueueSize) {
                queue.notifyAll();
            }
        }
    }

    @Override
    public void run() {
        while (started.get()) {
            try {
                synchronized (queue) {
                    if (!queue.isEmpty()) {
                        getAwsLogsStub().logEvents(queue);
                        queue.clear();
                    }
                    queue.wait(maxFlushTimeMillis);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        synchronized (queue) {
            if (!queue.isEmpty()) {
                getAwsLogsStub().logEvents(queue);
                queue.clear();
            }
        }
    }
}
