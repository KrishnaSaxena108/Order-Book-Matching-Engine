package model;

public class TradeResult {
    public final Trade trade;
    public final boolean success;

    public TradeResult(Trade trade, boolean success) {
        this.trade = trade;
        this.success = success;
    }
}