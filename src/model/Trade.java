package model;

public class Trade {
    public final Order buyOrder;
    public final Order sellOrder;
    public final double matchedPrice;
    public final int quantity;

    public Trade(Order buyOrder, Order sellOrder, double matchedPrice, int quantity) {
        this.buyOrder = buyOrder;
        this.sellOrder = sellOrder;
        this.matchedPrice = matchedPrice;
        this.quantity = quantity;
    }
}