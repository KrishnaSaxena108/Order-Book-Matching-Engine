import model.*;
import model.Order.Side;
import trader.TraderTask;
import engine.MatchingEngine;
import confirmation.TradeConfirmer;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("STOCK EXCHANGE ORDER BOOK SYSTEM");
        System.out.println("--------------------------------");

        // Shared queue: traders put orders here, and the engine reads from here.
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        // Shared trade history: confirmations add to this list after a trade succeeds.
        List<Trade> tradeHistory = Collections.synchronizedList(new ArrayList<>());
        // Event log: useful for printing a small trace at the end.
        List<String> eventLog = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<Order>> groupedOrders = readOrders(args);

        if (groupedOrders.isEmpty()) {
            System.out.println("No valid orders were supplied.");
            System.out.println("Example order: TRADER_A BUY 102 10");
            return;
        }

        System.out.println("Loaded orders for " + groupedOrders.size() + " traders");

        // One worker per trader keeps the example simple and matches the training requirement.
        ExecutorService traderPool = newFixedThreadPool(groupedOrders.size(), "TraderWorker");
        // Separate pool for confirmations so confirmation work does not block the engine.
        ExecutorService confirmPool = newFixedThreadPool(4, "ConfirmWorker");

        TradeConfirmer confirmer = new TradeConfirmer();

        MatchingEngine engine = new MatchingEngine(queue, tradeHistory, eventLog, confirmer, confirmPool);
        Thread engineThread = new Thread(engine);
        List<Future<?>> traderFutures = new ArrayList<>();

        try {
            engineThread.start();

            for (Map.Entry<String, List<Order>> entry : groupedOrders.entrySet()) {
                System.out.println("Starting trader thread for " + entry.getKey());
                traderFutures.add(traderPool.submit(new TraderTask(entry.getKey(), entry.getValue(), queue)));
            }

            for (Future<?> future : traderFutures) {
                future.get();
            }

            System.out.println("All trader threads completed.");

            // volatile is enough here because the main thread is the only writer and the engine only needs to observe the flag change.
            engine.marketOpen = false;
            engineThread.join();

            engine.awaitConfirmationCompletion();

            System.out.println();
            System.out.println("FINAL SUMMARY");
            System.out.println("Orders Submitted: " + countOrders(groupedOrders));
            System.out.println("Trades Matched: " + engine.totalMatches);
            System.out.println("Confirmations Succeeded: " + engine.getConfirmedCount());
            System.out.println("Confirmations Failed: " + engine.getFailedCount());
            System.out.println("Unmatched Orders Remaining: " + engine.getUnmatchedOrderCount());

            synchronized (tradeHistory) {
                System.out.println("Trade History Size: " + tradeHistory.size());
            }

            System.out.println();
            System.out.println("DETAILED EVENT LOG");
            synchronized (eventLog) {
                for (String event : eventLog) {
                    System.out.println(event);
                }
            }

            System.out.println("Shutdown complete");
        } finally {
            traderPool.shutdown();
            confirmPool.shutdown();

            traderPool.awaitTermination(1, TimeUnit.MINUTES);
            confirmPool.awaitTermination(1, TimeUnit.MINUTES);
        }
    }

    private static Map<String, List<Order>> readOrders(String[] args) throws IOException {
        Map<String, List<Order>> grouped = new LinkedHashMap<>();

        if (args.length > 0) {
            BufferedReader reader = Files.newBufferedReader(Paths.get(args[0]));
            System.out.println("Reading orders from file: " + args[0]);
            System.out.println();
            readOrdersFromReader(reader, grouped, null);
            return grouped;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        printMenu();

        String firstLine = reader.readLine();
        if (firstLine == null) {
            return grouped;
        }

        String trimmedFirstLine = firstLine.trim();
        if (trimmedFirstLine.equals("1")) {
            Path sampleFile = Paths.get("orders.txt");
            if (!Files.exists(sampleFile)) {
                sampleFile = Paths.get("src", "orders.txt");
            }

            System.out.println("Using sample file: " + sampleFile.toAbsolutePath());
            System.out.println();
            readOrdersFromReader(Files.newBufferedReader(sampleFile), grouped, null);
            return grouped;
        }

        if (trimmedFirstLine.equals("2")) {
            System.out.println();
            System.out.println("Type one order per line.");
            System.out.println("When you are finished, type DONE on a line by itself.");
            System.out.println();
            System.out.print("Start typing orders now: ");
            System.out.flush();
            readOrdersFromReader(reader, grouped, null);
            return grouped;
        }

        if (trimmedFirstLine.equals("3")) {
            throw new EOFException("User chose to exit.");
        }

        readOrdersFromReader(reader, grouped, firstLine);

        return grouped;
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("MENU");
        System.out.println("====");
        System.out.println("1 - Use the sample file");
        System.out.println("2 - Type orders manually");
        System.out.println("3 - Exit");
        System.out.println();
        System.out.println("Order format: TRADER_NAME SIDE PRICE QUANTITY");
        System.out.println("Example: TRADER_A BUY 102 10");
        System.out.println();
        System.out.print("Enter choice: ");
        System.out.flush();
    }

    private static void readOrdersFromReader(BufferedReader reader, Map<String, List<Order>> grouped, String firstLine) throws IOException {
        String line = firstLine;

        while (true) {
            if (line == null) {
                line = reader.readLine();
            }

            if (line == null) {
                break;
            }

            String trimmed = line.trim();
            line = null;

            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.equalsIgnoreCase("DONE") || trimmed.equalsIgnoreCase("END")) {
                break;
            }

            // Each order should look like: TRADER_A BUY 102 10
            String[] parts = trimmed.split("\\s+");
            if (parts.length != 4) {
                System.out.println("Please enter exactly 4 values: TRADER_NAME SIDE PRICE QUANTITY");
                System.out.println("Example: TRADER_A BUY 102 10");
                continue;
            }

            try {
                Order order = new Order(
                        parts[0],
                        Side.valueOf(parts[1].toUpperCase(Locale.ROOT)),
                        Double.parseDouble(parts[2]),
                        Integer.parseInt(parts[3])
                );
                grouped.computeIfAbsent(order.traderName, key -> new ArrayList<>()).add(order);
            } catch (Exception ex) {
                System.out.println("Could not read line: " + trimmed);
                System.out.println("Please use the format: TRADER_NAME SIDE PRICE QUANTITY");
            }
        }
    }

    private static int countOrders(Map<String, List<Order>> groupedOrders) {
        int total = 0;
        for (List<Order> orders : groupedOrders.values()) {
            total += orders.size();
        }
        return total;
    }

    private static ExecutorService newFixedThreadPool(int size, String prefix) {
        return Executors.newFixedThreadPool(size, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + thread.getId());
            return thread;
        });
    }
}