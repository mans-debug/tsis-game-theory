package org.example;

import java.util.Date;

class CurrencyData {

    public Date date;
    public double open;
    public double close;

    public CurrencyData(Date date, double open, double close) {
        this.date = date;
        this.open = open;
        this.close = close;
    }
}