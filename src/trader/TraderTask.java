package trader;

import model.Order;

import java.util.Random;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class TraderTask implements Runnable {
    private final String traderName;
    private final List<Order> orders;
    private final BlockingQueue<Order> queue;
    private final Random random = new Random();

    public TraderTask(String traderName, List<Order> orders, BlockingQueue<Order> queue) {
        this.traderName = traderName;
        this.orders = orders;
        this.queue = queue;
    }

    @Override
    public void run() {
        for (Order o : orders) {
            try {
                // Put the order into the shared queue so the engine can pick it up.
                System.out.println(Thread.currentThread().getName() + " is handling " + traderName + " -> " + o.side + " " + o.price + " x " + o.quantity);
                queue.put(o);
                Thread.sleep(10 + random.nextInt(25));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}