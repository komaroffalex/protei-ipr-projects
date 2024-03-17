package ru.komarov.futures;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;

public final class CustomExecutorServiceImpl {
    private final Set<ThreadWorker> workers;
    private volatile Queue<CustomFutureImpl<?>> futureQueue;
    private Thread workersMonitorThread;
    private volatile boolean isShutdown = false;


    private long pollWorkersPeriodMillis = 500L;
    private long futuresCompletnessPollMillis = 100L;

    public CustomExecutorServiceImpl(int pollSize) {
        if (pollSize == 0) {
            this.workers = null;
            return;
        }
        this.workers = new HashSet<>();
        for (int i = 0; i < pollSize; i++) {
            this.workers.add(new ThreadWorker(i));
        }
        this.futureQueue = new LinkedList<>();
        this.workersMonitorThread = new Thread(tryOffer());
        this.workersMonitorThread.start();
    }

    public void shutdown() {
        this.isShutdown = true;
        this.futureQueue.clear();
        this.workers.clear();
    }

    public CustomExecutorServiceImpl(int pollSize, long pollWorkersPeriodMillis,
                                     long futuresCompletnessPollMillis) {
        this(pollSize);
        this.pollWorkersPeriodMillis = pollWorkersPeriodMillis;
        this.futuresCompletnessPollMillis = futuresCompletnessPollMillis;
    }

    public <T> CustomFutureImpl<T> submit(Callable<T> callableTask) {
        CustomFutureImpl<T> future = new CustomFutureImpl<>(callableTask);
        this.futureQueue.offer(future);
        return future;
    }

    private Runnable tryOffer() {
        return () -> {
            do {
                offerForExecution();
                try {
                    Thread.sleep(pollWorkersPeriodMillis);
                } catch (InterruptedException e) {
                    System.out.println("failed to offer futures for execution due to" + e.getMessage());
                }
            } while (!isShutdown);
        };
    }

    private synchronized void offerForExecution() {
        if (this.futureQueue.isEmpty()) {
            System.out.println("Currently no futures to execute.");
            return;
        }
        ThreadWorker threadWorker = getAnyFreeWorker();
        if (threadWorker == null) {
            System.out.println("Currently all workers are busy.");
            return;
        }

        CustomFutureImpl<?> future = poll();
        if (future == null) {
            System.out.println("No futures could be polled for execution");
            return;

        }
        System.out.println("Starting new future execution on thread worker with id: " + threadWorker.id);
        threadWorker.init(future);
    }

    private synchronized CustomFutureImpl<?> poll() {
        for (int i = 0; i < this.futureQueue.size(); i++) {
            CustomFutureImpl<?> future = this.futureQueue.poll();
            if (future == null) {
                return null;
            }
            if (!future.isCanceled) {
                return future;
            }
        }
        return null;
    }

    private synchronized boolean isQueued(CustomFutureImpl<?> future) {
        return this.futureQueue.contains(future);
    }

    private ThreadWorker getAnyFreeWorker() {
        for (ThreadWorker worker : this.workers) {
            if (worker.isFree()) {
                return worker;
            }
        }
        return null;
    }

    private final class ThreadWorker {
        private ThreadWorker(int id) {
            this.id = id;
            this.future = null;
            this.thread = null;
        }

        private Thread thread;
        private CustomFutureImpl<?> future;
        private final int id;

        private boolean isFree() {
            return thread == null || future == null || future.isDone();
        }

        private void init(CustomFutureImpl<?> future) {
            Thread thread = new Thread(() -> {
                try {
                    future.call();
                } catch (Exception e) {
                    System.out.println("Failed to execute callable due to: " + e.getMessage());
                }
            });
            thread.start();
            this.thread = thread;
            this.future = future;
        }
    }

    public class CustomFutureImpl<T> implements Future<T> {
        private T result;
        private Callable<T> callableTask;
        private volatile boolean isDone = false;
        private boolean isCanceled = false;

        private CustomFutureImpl(Callable<T> callableTask) {
            this.callableTask = callableTask;
        }

        private void call() throws Exception {
            result = callableTask.call();
            isDone = true;
            System.out.println("Future task is complete.");
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (isDone) {
                System.out.println("Cannot cancel task that has been completed.");
                return false;
            }
            if (!CustomExecutorServiceImpl.this.isQueued(this)) {
                System.out.println("Cannot cancel task is being executed.");
                return false;
            }
            this.isCanceled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return this.isCanceled;
        }

        @Override
        public boolean isDone() {
            return isDone;
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            if (isDone) {
                return result;
            }
            while (!isDone) {
                Thread.sleep(futuresCompletnessPollMillis);
            }
            return this.result;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (isDone) {
                return result;
            }

            Thread.sleep(timeout);

            if (!isDone) {
                throw new TimeoutException("Couldn't fetch future result in time");
            }

            return this.result;
        }
    }
}