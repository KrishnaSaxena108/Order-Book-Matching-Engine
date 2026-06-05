package engine;

import model.*;
import confirmation.TradeConfirmer;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

public class MatchingEngine implements Runnable {

    // Only the main thread changes this flag, while the engine thread keeps reading it.
    // volatile is enough for that simple one-writer / many-reader pattern.
    public volatile boolean marketOpen = true;

    private final BlockingQueue<Order> queue;
    private final List<Trade> tradeHistory;
    private final List<String> eventLog;
    private final ExecutorService confirmationExecutor;

    // tryLock lets the engine back off instead of waiting forever if another thread is holding the lock.
    private final ReentrantLock lock = new ReentrantLock();
    private final TradeConfirmer confirmer;

    private final List<Order> buys = new ArrayList<>();
    private final List<Order> sells = new ArrayList<>();
    private final List<CompletableFuture<Void>> confirmationFutures = new ArrayList<>();

    public int totalMatches = 0;
    private int confirmed = 0;
    private int failed = 0;

    public MatchingEngine(
            BlockingQueue<Order> queue,
            List<Trade> tradeHistory,
            List<String> eventLog,
            TradeConfirmer confirmer,
            ExecutorService confirmationExecutor
    ) {
        this.queue = queue;
        this.tradeHistory = tradeHistory;
        this.eventLog = eventLog;
        this.confirmer = confirmer;
        this.confirmationExecutor = confirmationExecutor;
    }

    @Override
    public void run() {
        log("ENGINE STARTED");

        try {
            while (marketOpen || !queue.isEmpty()) {

                Order o = queue.poll(200, TimeUnit.MILLISECONDS);
                if (o == null) continue;

                log("ENGINE RECEIVED ORDER -> " + o.traderName + " " + o.side + " " + o.price + " " + o.quantity);

                submitOrder(o);
            }
        } catch (Exception e) {
            log("ENGINE ERROR: " + e.getMessage());
        }

        log("ENGINE STOPPED");
    }

    private void submitOrder(Order order) {
        try {
            if (!lock.tryLock(50, TimeUnit.MILLISECONDS)) {
                return;
            }

            // First store the order, then see if it can match something already waiting.
            if (order.side == Order.Side.BUY) {
                buys.add(order);
            } else {
                sells.add(order);
            }

            findAndMatchOrders();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log("MATCH ERROR: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void findAndMatchOrders() {
        // Simple first-match approach: scan buys, then scan sells, and stop at the first valid pair.
        for (Iterator<Order> buyIterator = buys.iterator(); buyIterator.hasNext(); ) {
            Order buyOrder = buyIterator.next();

            for (Iterator<Order> sellIterator = sells.iterator(); sellIterator.hasNext(); ) {
                Order sellOrder = sellIterator.next();

                if (buyOrder.price >= sellOrder.price) {
                    Trade trade = new Trade(buyOrder, sellOrder, sellOrder.price, Math.min(buyOrder.quantity, sellOrder.quantity));
                    totalMatches++;

                    log("MATCH FOUND -> BUY=" + buyOrder.traderName + " SELL=" + sellOrder.traderName + " PRICE=" + sellOrder.price);
                    registerConfirmation(trade);

                    buyIterator.remove();
                    sellIterator.remove();
                    return;
                }
            }
        }
    }

    private void registerConfirmation(Trade trade) {
        // The confirmation work happens asynchronously so the engine can keep matching new orders.
        CompletableFuture<Void> future = CompletableFuture
                .supplyAsync(() -> confirmer.confirm(trade), confirmationExecutor)
                .exceptionally(ex -> {
                    markFailed();
                    log("FAILED CONFIRMATION -> " + rootMessage(ex));
                    return null;
                })
                .thenAccept(result -> {
                    if (result == null) {
                        return;
                    }

                    synchronized (tradeHistory) { // synchronized is still required because a synchronized list wrapper does not make compound write-and-iterate sequences atomic.
                        tradeHistory.add(result);
                    }
                    markConfirmed();
                    log("CONFIRMED TRADE -> " + result.buyOrder.traderName + " vs " + result.sellOrder.traderName);
                });

        synchronized (confirmationFutures) {
            confirmationFutures.add(future);
        }
    }

    public void awaitConfirmationCompletion() {
        synchronized (confirmationFutures) {
            if (confirmationFutures.isEmpty()) {
                return;
            }

            // allOf().join() waits for every confirmation together instead of one-by-one.
            CompletableFuture.allOf(confirmationFutures.toArray(new CompletableFuture[0])).join();
        }
    }

    public synchronized int getConfirmedCount() {
        return confirmed;
    }

    public synchronized int getFailedCount() {
        return failed;
    }

    public int getUnmatchedOrderCount() {
        if (!lock.tryLock()) {
            return buys.size() + sells.size();
        }

        try {
            return buys.size() + sells.size();
        } finally {
            lock.unlock();
        }
    }

    private synchronized void markConfirmed() {
        confirmed++;
    }

    private synchronized void markFailed() {
        failed++;
    }

    private void log(String msg) {
        synchronized (eventLog) {
            eventLog.add("[ENGINE] " + msg);
        }
        System.out.println("[ENGINE] " + msg);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}