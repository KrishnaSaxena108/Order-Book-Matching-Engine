package trader;

import model.Order;
import model.Order.Side;

import java.util.Arrays;
import java.util.List;

public class NaiveTrader {

    public static void main(String[] args) throws InterruptedException {
        List<Order> orders = Arrays.asList(
                new Order("NAIVE_A", Side.BUY, 101, 3),
                new Order("NAIVE_A", Side.BUY, 102, 2),
                new Order("NAIVE_A", Side.SELL, 100, 1)
        );

        Runnable task = () -> {
            for (Order order : orders) {
                System.out.println(Thread.currentThread().getName() + " submitting " + order.traderName + " " + order.side + " " + order.price + " " + order.quantity);
            }
        };

        Thread thread = new Thread(task, "naive-trader-thread");

        // start() creates a new thread; run() would just execute on the current thread and hide the concurrency lesson.
        // This raw thread-per-task pattern is fine for a warm-up, but it does not scale once trader count grows.
        thread.start();
        thread.join();
    }
}