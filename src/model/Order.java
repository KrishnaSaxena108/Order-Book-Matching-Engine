package model;

public class Order {
    public enum Side { BUY, SELL }

    public final String traderName;
    public final Side side;
    public final double price;
    public final int quantity;

    public Order(String traderName, Side side, double price, int quantity) {
        this.traderName = traderName;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
    }
}