package pitfalls;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Pitfalls {

    public static void raceConditionDemo() throws Exception {
        System.out.println("\n=== RACE CONDITION DEMO ===");

        final int threads = 6;
        final int writesPerThread = 2_000;
        final int expected = threads * writesPerThread;

        List<Integer> brokenHistory = new ArrayList<>();
        runHistoryWriteDemo(brokenHistory, threads, writesPerThread, false);
        System.out.println("Broken count: " + brokenHistory.size() + " expected: " + expected);

        List<Integer> fixedHistory = Collections.synchronizedList(new ArrayList<>());
        runHistoryWriteDemo(fixedHistory, threads, writesPerThread, true);
        System.out.println("Fixed count: " + fixedHistory.size() + " expected: " + expected);
    }

    private static void runHistoryWriteDemo(List<Integer> history, int threads, int writesPerThread, boolean guarded) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < writesPerThread; j++) {
                        if (guarded) {
                            synchronized (history) {
                                history.add(j);
                            }
                        } else {
                            history.add(j);
                        }
                        if ((j & 127) == 0) {
                            Thread.yield();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            workers.add(worker);
            worker.start();
        }

        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
    }

    static final Object L1 = new Object();
    static final Object L2 = new Object();

    public static void deadlockDemo() throws InterruptedException {
        System.out.println("\n=== DEADLOCK DEMO ===");

        Thread deadlockA = new Thread(() -> {
            synchronized (L1) {
                sleepSilently(50);
                synchronized (L2) {
                    System.out.println("Deadlock demo unexpected completion A");
                }
            }
        }, "deadlock-a");

        Thread deadlockB = new Thread(() -> {
            synchronized (L2) {
                sleepSilently(50);
                synchronized (L1) {
                    System.out.println("Deadlock demo unexpected completion B");
                }
            }
        }, "deadlock-b");

        deadlockA.setDaemon(true);
        deadlockB.setDaemon(true);
        deadlockA.start();
        deadlockB.start();
        deadlockA.join(200);
        deadlockB.join(200);

        if (deadlockA.isAlive() && deadlockB.isAlive()) {
            System.out.println("Deadlock reproduced: both threads are still waiting");
        }

        Object firstLock = new Object();
        Object secondLock = new Object();
        Runnable orderedWork = () -> {
            synchronized (firstLock) {
                sleepSilently(25);
                synchronized (secondLock) {
                    // Consistent ordering removes the circular wait.
                }
            }
        };

        Thread fixedA = new Thread(orderedWork, "ordered-a");
        Thread fixedB = new Thread(orderedWork, "ordered-b");
        fixedA.start();
        fixedB.start();
        fixedA.join();
        fixedB.join();
        System.out.println("Fixed deadlock demo completed with consistent lock order");
    }

    private static void sleepSilently(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static volatile int volatileCounter = 0;

    public static void volatileNotEnough() throws Exception {
        System.out.println("\n=== COUNTER DEMO ===");

        final int iterations = 1_000;
        volatileCounter = 0;

        Runnable broken = () -> {
            for (int i = 0; i < iterations; i++) {
                int snapshot = volatileCounter;
                Thread.yield();
                volatileCounter = snapshot + 1;
                if ((i & 63) == 0) {
                    Thread.yield();
                }
            }
        };

        Thread t1 = new Thread(broken);
        Thread t2 = new Thread(broken);
        Thread t3 = new Thread(broken);

        t1.start();
        t2.start();
        t3.start();

        t1.join();
        t2.join();
        t3.join();

        System.out.println("Broken counter: " + volatileCounter + " expected: " + (iterations * 3));

        AtomicInteger fixed = new AtomicInteger();
        Runnable safe = () -> {
            for (int i = 0; i < iterations; i++) {
                fixed.incrementAndGet();
            }
        };

        Thread f1 = new Thread(safe);
        Thread f2 = new Thread(safe);
        Thread f3 = new Thread(safe);

        f1.start();
        f2.start();
        f3.start();

        f1.join();
        f2.join();
        f3.join();

        System.out.println("Fixed counter: " + fixed.get());
    }

    public static void futureGetIssue() throws Exception {
        System.out.println("\n=== FUTURE GET DEMO ===");

        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            long serializedStart = System.currentTimeMillis();
            for (int i = 0; i < 6; i++) {
                CompletableFuture.supplyAsync(() -> slowTask(150), executor).get();
            }
            long serializedElapsed = System.currentTimeMillis() - serializedStart;

            long asyncStart = System.currentTimeMillis();
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> slowTask(150), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            long asyncElapsed = System.currentTimeMillis() - asyncStart;

            System.out.println("Inline get elapsed ms: " + serializedElapsed);
            System.out.println("allOf join elapsed ms: " + asyncElapsed);
        } finally {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        }
    }

    private static Integer slowTask(long millis) {
        sleepSilently(millis);
        return 1;
    }

    public static void main(String[] args) throws Exception {
        raceConditionDemo();
        deadlockDemo();
        volatileNotEnough();
        futureGetIssue();
    }
}