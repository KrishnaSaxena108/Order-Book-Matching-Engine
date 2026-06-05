# Order-Book Matching Engine

Professional, beginner-friendly Java concurrency training module demonstrating a simplified order-book matching engine.

Key highlights:
- Menu-driven CLI with a `file` or `manual` input modes.
- Manual input ends when you type the sentinel `DONE` on a new line.
- Concurrent trader workers submit orders to a single matching engine thread.
- Matches are confirmed asynchronously; confirmations may randomly fail (simulated).

## Requirements

- Java 11 or newer
- A POSIX-like shell (commands shown work on Linux/macOS terminals)

## Quick build & run

From the repository root:

```bash
mkdir -p out
find src -name '*.java' > sources.txt
javac -d out @sources.txt

# Run with the bundled sample orders file
java -cp out Main src/orders.txt

# Or start manual entry mode (type orders then `DONE`)
java -cp out Main
```

## Order format

Each order is a single line with 5 fields separated by spaces:

TRADER_NAME BUY|SELL SYMBOL QUANTITY PRICE

Example:

T1 BUY AAPL 100 150.00

Type `DONE` (uppercase) on an empty line to finish manual input and let the program proceed to matching and confirmations.

## Example output (trimmed)

```
ENGINE STARTED
[TraderPool-1] T1 submitted: BUY AAPL 100 @150.0
Matched: BUY(T1) vs SELL(T2) -> Trade(id=1) - confirmation pending
Confirmed trades: 2 | Failed confirms: 1
Summary: Submitted=7, Matches=3, Confirmed=2, Failed=1, Unmatched=1
```

Actual confirmation/results vary slightly because the confirmation step intentionally simulates latency and occasional failures.

## File and responsibility map

- [src/Main.java](src/Main.java): program entry, input modes, lifecycle orchestration
- [src/engine/MatchingEngine.java](src/engine/MatchingEngine.java): single matching loop, order book, and match logic
- [src/trader/TraderTask.java](src/trader/TraderTask.java): trader worker that submits orders into the shared queue
- [src/trader/NaiveTrader.java](src/trader/NaiveTrader.java): small raw-`Thread` warm-up demo
- [src/confirmation/TradeConfirmer.java](src/confirmation/TradeConfirmer.java): simulated confirmation (latency + random failure)
- [src/pitfalls/Pitfalls.java](src/pitfalls/Pitfalls.java): educational demos (race conditions, deadlocks, volatile vs Atomic, CompletableFuture patterns)

## Concurrency primitives demonstrated

- `Thread` / `Runnable` — trader tasks and simple demos
- `ExecutorService` — fixed thread pools for traders and confirmation workers
- `BlockingQueue` (`LinkedBlockingQueue`) — producer/consumer handoff between traders and engine
- `volatile` — engine stop flag to publish state between threads
- `synchronized` / guarded collections — protect shared lists (trade history, event log)
- `ReentrantLock.tryLock(timeout)` — non-blocking critical section with backoff
- `CompletableFuture` — asynchronous confirmation pipeline and aggregation using `allOf(...).join()`

## Running the Pitfalls demos

```bash
java -cp out pitfalls.Pitfalls
```

Each demo prints a broken and a fixed variant so you can compare the observable behavior.

## Contributing

Small improvements, clearer examples, or additional pitfalls are welcome. Open an issue or send a PR.

## License

This repository is provided for educational purposes. Feel free to reuse examples for learning.
