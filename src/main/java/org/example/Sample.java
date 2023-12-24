package org.example;

class Sample {
    // выборочная дисперсия по стратегии
    public double[] d = new double[4];
    // "выигрыш" для стратегии
    public double[] profit = new double[4];
    // индекс стратегии с максимальным риском (т.е. максимальной дисперсией)
    public int risk_strategy;
    // Состояния природы для стратегий.
    // Например, environment_state[0] - состояние природы для первой стратегии в этой конкретной выборке
    public int environment_state[] = new int[4];
}