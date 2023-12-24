package org.example.enik;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Main {

    CurrencyData[] cnurub;
    CurrencyData[] eurrub;
    CurrencyData[] usdrub;
    CurrencyData[] gbprub;

    // исходная сумма для вложения
    double budget = 1000000;

    // кол-во дней в подвыборке
    final int portion_size = 5;

    double A = 3000;

    // массив их 5 дневных (portion_size) выборок
    Sample[] sampling;

    // пропорции валют в стратегиях
    double[][] strategy = new double[][]{{0.1,0.15,0.15,0.6}, {0.3,0.3,0.3,0.1},{0.6,0.1,0.1,0.2},{0.35,0.35,0.15,0.15}};

    // размер всей исследуемой выборки
    int sz;

    public static void main(String[] args) {

        Main prog = new Main();
        // Загружаем данные
        prog.initData();
        // Строим выборку
        prog.sampling();
        prog.makeMatrix();

//        System.out.println(
//          1000000*( 0.1*((1/82.34)*82.6032 - 1) + 0.15*((1/0.621525260485071)*0.623447724617695 - 1) +
//                0.15*((1/70.9844)*70.9789 - 1) +0.6*((1/0.006662074143595)*0.006663434096883 - 1)));
    }

    public void sampling() {
        // будем рассматривать выборки по portion_size дней из массива котировок
        // затем по ним фиксировать частоту появления событий при разных стратегиях
        sampling = new Sample[sz/portion_size];

        for (int i = 0; i < sz/portion_size; i++) { // цикл по подвыборкам, их количество = размер массива котировок / portion_size

            System.out.println("Выборка #" + i + " дата с: " + new SimpleDateFormat("dd.MM.yyyy").format(cnurub[i * portion_size].date));

            int l = i * portion_size; // индекс первого элемента подвыборки в общем массиве котировок

            // формируем и обсчитываем выборку из 5 (portion_size) дней
            // в цикле индекс очередной подвыборки формируется как [i/portion_size]
            sampling[i] = new Sample();

            // Расчитываем "прибыль"(profit) - разницу между пересчитанной в рублях валютной корзины по
            // курсам закрытия и исходной суммой для каждой стратегии
            // в i-ый день формируем корзину, в (i + portion_size)-ый день конвертируем в рубли
            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                sampling[i].profit[k] = budget * (
                        strategy[k][0]*((1/ cnurub[l].open)* cnurub[l + portion_size - 1].close - 1) +
                        strategy[k][1]*((1/ eurrub[l].open)* eurrub[l + portion_size - 1].close - 1) +
                        strategy[k][2]*((1/usdrub[l].open)*usdrub[l + portion_size - 1].close - 1) +
                        strategy[k][3]*((1/ gbprub[l].open)* gbprub[l + portion_size - 1].close - 1)
                );
            }

            double max_dispersion = 0; // переменная для сравнения дисперсий разных стратегий
            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                double x_ = 0;
                for (int j = l; j < l + portion_size; j++) {
                    x_ = x_ + strategy[k][0] * cnurub[j].close + strategy[k][1] * eurrub[j].close +
                            strategy[k][2] * usdrub[j].close + strategy[k][3] * gbprub[j].close;
                }
                x_ = x_/portion_size;

                double d = 0;
                for (int j = l; j < l + portion_size; j++) {
                    d = d + (strategy[k][0] * cnurub[j].close + strategy[k][1] * eurrub[j].close +
                            strategy[k][2] * usdrub[j].close + strategy[k][3] * gbprub[j].close - x_) *
                            (strategy[k][0] * cnurub[j].close + strategy[k][1] * eurrub[j].close +
                                    strategy[k][2] * usdrub[j].close + strategy[k][3] * gbprub[j].close - x_);
                }
                // зафиксируем дисперсию в структуре, хранящей разные параметры подвыборки
                sampling[i].d[k] = d/portion_size;

                // Проверим, не является ли стратегия наиболее рискованной
                if (sampling[i].d[k] > max_dispersion) {
                    max_dispersion = sampling[i].d[k];
                    sampling[i].risk_strategy = k;
                }
            }

            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                if (sampling[i].profit[k] > A && k == sampling[i].risk_strategy) {
                    sampling[i].environment_state[k] = 0;
                } else if (sampling[i].profit[k] < A && k == sampling[i].risk_strategy) {
                    sampling[i].environment_state[k] = 1;
                } else if (sampling[i].profit[k] > A && k != sampling[i].risk_strategy) {
                    sampling[i].environment_state[k] = 2;
                } else if (sampling[i].profit[k] < A && k != sampling[i].risk_strategy) {
                    sampling[i].environment_state[k] = 3;
                }
            }

            // распечатаем параметры подвыборки
            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                System.out.println("s" + (k+1) + ": profit=" + sampling[i].profit[k] + " , dispersion=" + sampling[i].d[k] + ", environment_state=" + sampling[i].environment_state[k]);
            }
        }
    }

    public void makeMatrix() {

        System.out.println("----------------------------------------------------------------------------");

        for (int k = 0; k < 4; k++) {// перебираем стратегии
            double cp0=0; double cp1=0; double cp2=0; double cp3=0; // счетчики выпавших состояний природы при стратегии k
            double ss0=0; double ss1=0; double ss2=0; double ss3=0; // сумма выигрыша стратегии k при определенном состоянии природы

            for (int i = 0; i < sz / portion_size; i++) { // перебираем выборки и смотрим соответствие стратегии и состояния
                if (sampling[i].environment_state[k] == 0) { cp0++; ss0 += sampling[i].profit[k];}
                else if (sampling[i].environment_state[k] == 1) { cp1++; ss1 += sampling[i].profit[k];}
                else if (sampling[i].environment_state[k] == 2) { cp2++; ss2 += sampling[i].profit[k];}
                else if (sampling[i].environment_state[k] == 3) { cp3++; ss3 += sampling[i].profit[k];}
            }

            if (cp0 > 0) {ss0 = ss0/cp0; cp0 = cp0/ (sz / portion_size);}
            if (cp1 > 0) {ss1 = ss1/cp1; cp1 = cp1/ (sz / portion_size);}
            if (cp2 > 0) {ss2 = ss2/cp2; cp2 = cp2/ (sz / portion_size);}
            if (cp3 > 0) {ss3 = ss3/cp3; cp3 = cp3/ (sz / portion_size);}

            // Выводим строку матрицы
            System.out.println("s"+ k +"|" + ss0 + "; " + cp0 + "|" + ss1 + "; " + cp1 + "|" + ss2 + "; " + cp2 + "|" + ss3 + "; " + cp3 + "|");
        }
    }

    public void initData() {
        /*
            File format
            <TICKER>;<PER>;<DATE>;<TIME>;<OPEN>;<HIGH>;<LOW>;<CLOSE>;<VOL>
         */
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

        try {
            List<String> pairCNU_RUB = Files.readAllLines(Paths.get("/Users/mansurminnikaev/IdeaProjects/game-theory/src/main/resources/new_cur/CNYRUB_TOM_01042021_31082023.csv"));
            List<String> pairEUR_RUB = Files.readAllLines(Paths.get("/Users/mansurminnikaev/IdeaProjects/game-theory/src/main/resources/new_cur/EURRUB_01042021_31082023.csv"));
            List<String> pairUSD_RUB = Files.readAllLines(Paths.get("/Users/mansurminnikaev/IdeaProjects/game-theory/src/main/resources/new_cur/USDRUB_01042021_31082023.csv"));
            List<String> pairGBP_RUB = Files.readAllLines(Paths.get("/Users/mansurminnikaev/IdeaProjects/game-theory/src/main/resources/new_cur/GBPRUB_TOM_01042021_31082023.csv"));

            // Первая строка - заголовок
            sz = min(pairCNU_RUB.size(), pairEUR_RUB.size(), pairUSD_RUB.size(), pairGBP_RUB.size()) - 1;

            cnurub = new CurrencyData[sz];
            eurrub = new CurrencyData[sz];
            usdrub = new CurrencyData[sz];
            gbprub = new CurrencyData[sz];

            for (int i = 1; i < sz; i++) {
                String[] p_cnurub = pairCNU_RUB.get(i).replace(",", ".").split(";");
                String[] p_eurrub = pairEUR_RUB.get(i).replace(",", ".").split(";");
                String[] p_usdrub = pairUSD_RUB.get(i).replace(",", ".").split(";");
                String[] p_gbprub = pairGBP_RUB.get(i).replace(",", ".").split(";");

                cnurub[i-1] = new CurrencyData(sdf.parse(p_cnurub[2]), Double.parseDouble(p_cnurub[4]), Double.parseDouble(p_cnurub[7]));
                eurrub[i-1] = new CurrencyData(sdf.parse(p_eurrub[2]), Double.parseDouble(p_eurrub[4]), Double.parseDouble(p_eurrub[7]));
                usdrub[i-1] = new CurrencyData(sdf.parse(p_usdrub[2]), Double.parseDouble(p_usdrub[4]), Double.parseDouble(p_usdrub[7]));
                gbprub[i-1] = new CurrencyData(sdf.parse(p_gbprub[2]), Double.parseDouble(p_gbprub[4]), Double.parseDouble(p_gbprub[7]));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    static int min(int a1, int a2, int a3, int a4) {
        int x, y;
        return a1 < (y = a2 < (x = a3 < a4 ? a3 : a4) ? a2 : x) ? a1 : y;
    }
}

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
