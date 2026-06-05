# Order Book Matching Engine

Java 11 concurrency training module for a single-stock matching engine.

## Run

Use the bundled sample input:

```bash
javac $(find src -name '*.java')
java -cp src Main src/orders.txt
```

You can also pipe orders through stdin:

```bash
java -cp src Main < src/orders.txt
```

## Sample Output

Representative run; confirmation results vary because failures are randomized.

```text
Total Orders Submitted: 7
Total Matches Found: 3
Confirmed Trades: 2
Failed Confirmations: 1
Unmatched Orders Remaining: 1
```

## Class Map

- `src/Main.java` - parses input, wires the thread pools, waits for confirmations, and prints the final summary.
- `src/trader/TraderTask.java` - trader worker submitted to the fixed thread pool.
- `src/trader/NaiveTrader.java` - warm-up demo for raw `Thread` usage.
- `src/engine/MatchingEngine.java` - single matching engine thread with `volatile`, `synchronized`, `ReentrantLock`, and `CompletableFuture` coordination.
- `src/confirmation/TradeConfirmer.java` - synchronous confirmation step used by the async pipeline and failure simulation.
- `src/pitfalls/Pitfalls.java` - four standalone demos covering race conditions, deadlock, counters, and premature `get()` calls.

## Notes

- The engine is intentionally single-threaded.
- Trader submission is concurrent, but each trader submits its own orders sequentially.
- Confirmation failures are random and are expected in normal runs.

## How It Works

1. `Main` reads orders and groups them by trader name.
2. One trader task is created per trader and placed in a fixed thread pool.
3. Each trader task pushes orders into a shared blocking queue.
4. The matching engine reads from the queue, stores orders, and looks for the first BUY/SELL pair where `BUY price >= SELL price`.
5. When a match is found, confirmation runs in a separate thread pool with `CompletableFuture`.
6. Main waits for traders to finish, stops the engine, waits for confirmations, and prints the summary.

## What to Say When Explaining It

- `BlockingQueue` is used so trader threads can safely hand work to the engine.
- `volatile` is used for the engine stop flag because only the main thread writes it.
- `ReentrantLock.tryLock()` is used so the engine can back off instead of waiting forever.
- `synchronized` protects the trade history list when confirmations add results.
- `CompletableFuture` keeps confirmations asynchronous, so the engine does not freeze while waiting.