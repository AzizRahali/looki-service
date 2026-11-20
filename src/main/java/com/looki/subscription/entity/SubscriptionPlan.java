package com.looki.subscription.entity;

public enum SubscriptionPlan {

    MONTHLY(29.99, 30, "Monthly", "$29.99 / month"),
    YEARLY(249.99, 365, "Yearly", "$249.99 / year");

    private final double price;
    private final int durationDays;
    private final String label;
    private final String priceLabel;

    SubscriptionPlan(double price, int durationDays, String label, String priceLabel) {
        this.price = price;
        this.durationDays = durationDays;
        this.label = label;
        this.priceLabel = priceLabel;
    }

    public double getPrice() { return price; }
    public int getDurationDays() { return durationDays; }
    public String getLabel() { return label; }
    public String getPriceLabel() { return priceLabel; }
}
