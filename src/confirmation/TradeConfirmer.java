package confirmation;

import model.Trade;

import java.util.Random;

public class TradeConfirmer {
    private final Random random = new Random();

    public Trade confirm(Trade trade) {
        try {
            // Sleep a little so confirmation looks like a separate background task.
            Thread.sleep(50 + random.nextInt(50));
            // About 10% of confirmations fail so the error path is visible during training.
            if (random.nextDouble() < 0.1) {
                throw new RuntimeException("random confirmation failure");
            }
            return trade;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("confirmation interrupted", e);
        }
    }
}