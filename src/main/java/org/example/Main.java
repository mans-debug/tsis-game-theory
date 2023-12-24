package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

class Sample {
    // выборочная дисперсия по стратегии
    double[] d = new double[4];
    // "выигрыш" для стратегии
    double[] profit = new double[4];
    // Индекс стратегии с максимальным риском (т.е. максимальной дисперсией)
    int risk_strategy;
    // Состояния природы для стратегий.
    // Например, environment_state[0] - состояние природы для первой стратегии в этой конкретной выборке
    int[] environment_state = new int[4];
}

class CurrencyData {
    Date date;
    double open;
    double close;
    CurrencyData(Date date, double open, double close) {
        this.date = date;
        this.open = open;
        this.close = close;
    }
}

class MatrixElement {
    double value;
    double prob;
    MatrixElement(double value, double prob) {
        this.value = value;
        this.prob = prob;
    }
}

class Main {
    CurrencyData[] cnyrub;
    CurrencyData[] eurrub;
    CurrencyData[] gbprub;
    CurrencyData[] usdrub;

    // исходная сумма для вложения
    double budget = 1000000;

    // кол-во дней в подвыборке
    final int portionSize = 5;

    final double A = 3000;

    // массив из 5 дневных (portion_size) выборок
    Sample[] sampling;

    // пропорции валют в стратегиях
    double[][] strategy = new double[][] {
            {0.1, 0.15, 0.15, 0.6}, {0.3, 0.3, 0.3, 0.1}, {0.6, 0.1, 0.1, 0.2}, {0.35, 0.35, 0.15, 0.15}
    };

    // размер всей исследуемой выборки
    int sz;

    // итоговая матрица
    MatrixElement[][] matrix = new MatrixElement[4][4];

    public static void main(String[] args) {
        Main prog = new Main();
        // Загружаем данные
        prog.initData();
        // Строим выборку
        prog.sampling();
        // Выводим матрицу
        prog.makeMatrix();
        // Выводим критерии
        prog.printCriteria();
    }

    static int min(int a1, int a2, int a3, int a4) {
        return Math.min(a1, Math.min(a2, Math.min(a3, a4)));
    }

    void initData() {
        /*
            File format
            <TICKER>;<PER>;<DATE>;<TIME>;<OPEN>;<HIGH>;<LOW>;<CLOSE>;<VOL>
         */
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");

        try {
            List<String> pairCNYRUB = Files.readAllLines(Paths.get("src/main/resources/new_cur/CNYRUB_TOM_01042021_31082023.csv"));
            List<String> pairEURRUB = Files.readAllLines(Paths.get("src/main/resources/new_cur/EURRUB_01042021_31082023.csv"));
            List<String> pairGBPRUB = Files.readAllLines(Paths.get("src/main/resources/new_cur/GBPRUB_TOM_01042021_31082023.csv"));
            List<String> pairUSDRUB = Files.readAllLines(Paths.get("src/main/resources/new_cur/USDRUB_01042021_31082023.csv"));

            // Первая строка - заголовок
            sz = min(pairCNYRUB.size(), pairEURRUB.size(), pairGBPRUB.size(), pairUSDRUB.size()) - 1;

            cnyrub = new CurrencyData[sz];
            eurrub = new CurrencyData[sz];
            gbprub = new CurrencyData[sz];
            usdrub = new CurrencyData[sz];

            for (int i = 1; i < sz; i++) {
                String[] p_cnyrub = pairCNYRUB.get(i).replace(",", ".").split(";");
                String[] p_eurrub = pairEURRUB.get(i).replace(",", ".").split(";");
                String[] p_gbprub = pairGBPRUB.get(i).replace(",", ".").split(";");
                String[] p_usdrub = pairUSDRUB.get(i).replace(",", ".").split(";");

                cnyrub[i-1] = new CurrencyData(sdf.parse(p_cnyrub[2]), Double.parseDouble(p_cnyrub[4]), Double.parseDouble(p_cnyrub[7]));
                eurrub[i-1] = new CurrencyData(sdf.parse(p_eurrub[2]), Double.parseDouble(p_eurrub[4]), Double.parseDouble(p_eurrub[7]));
                gbprub[i-1] = new CurrencyData(sdf.parse(p_gbprub[2]), Double.parseDouble(p_gbprub[4]), Double.parseDouble(p_gbprub[7]));
                usdrub[i-1] = new CurrencyData(sdf.parse(p_usdrub[2]), Double.parseDouble(p_usdrub[4]), Double.parseDouble(p_usdrub[7]));
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    void sampling() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
        // будем рассматривать выборки по portion_size дней из массива котировок
        // затем по ним фиксировать частоту появления событий при разных стратегиях
        sampling = new Sample[sz/ portionSize];

        // цикл по подвыборкам, их количество = размер массива котировок / portion_size
        for (int i = 0; i < sz/ portionSize; i++) {
            String dateString = sdf.format(cnyrub[i * portionSize].date);
            System.out.println("Выборка #" + i + " дата с: " + dateString);

            int l = i * portionSize; // индекс первого элемента подвыборки в общем массиве котировок

            // формируем и обсчитываем выборку из 5 (portion_size) дней
            // в цикле индекс очередной подвыборки формируется как [i/portion_size]
            sampling[i] = new Sample();

            // Расчитываем "прибыль"(profit) - разницу между пересчитанной в рублях валютной корзины по
            // курсам закрытия и исходной суммой для каждой стратегии
            // в i-ый день формируем корзину, в (i + portion_size)-ый день конвертируем в рубли
            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                sampling[i].profit[k] = budget * (
                        strategy[k][0] * ((1 / cnyrub[l].open) * cnyrub[l + portionSize - 1].close - 1) +
                                strategy[k][1] * ((1 / eurrub[l].open) * eurrub[l + portionSize - 1].close - 1) +
                                strategy[k][2] * ((1 / gbprub[l].open) * gbprub[l + portionSize - 1].close - 1) +
                                strategy[k][3] * ((1 / usdrub[l].open) * usdrub[l + portionSize - 1].close - 1)
                );
            }

            // Для подсчета дисперсии по пропорциональной совокупности валют в корзине по каждой стратегии
            // сформируем "курс" корзины на каждый день подвыборки из portion_size дней (для определенности, по цене закрытия)
            // как сумму произведений курсов валют на соответствующую пропорцию из стратегии
            // По такому "курсу" корзины для разных стратегий вычислим среднее и дисперсию
            double max_dispersion = 0; // переменная для сравнения дисперсий разных стратегий
            for (int k = 0; k < 4; k++) { // цикл по стратегиям
                // среднее
                double x = 0;
                for (int j = l; j < l + portionSize; j++) {
                    x += strategy[k][0] * cnyrub[j].close + strategy[k][1] * eurrub[j].close +
                            strategy[k][2] * gbprub[j].close + strategy[k][3] * usdrub[j].close;
                }
                x /= portionSize;

                // дисперсия
                double d = 0;
                for (int j = l; j < l + portionSize; j++) {
                    d += (strategy[k][0] * cnyrub[j].close + strategy[k][1] * eurrub[j].close +
                            strategy[k][2] * gbprub[j].close + strategy[k][3] * usdrub[j].close - x) *
                            (strategy[k][0] * cnyrub[j].close + strategy[k][1] * eurrub[j].close +
                                    strategy[k][2] * gbprub[j].close + strategy[k][3] * usdrub[j].close - x);
                }
                // зафиксируем дисперсию в структуре, хранящей разные параметры подвыборки
                sampling[i].d[k] = d / portionSize;

                // Проверим, не является ли стратегия наиболее рискованной
                if (sampling[i].d[k] > max_dispersion) {
                    max_dispersion = sampling[i].d[k];
                    sampling[i].risk_strategy = k;
                }
            }

            // Определим состояния "Природы" для стратегий
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

    /*
        Соберем значения в стратегиях с одинаковыми состояниями природы
        например:
        s0 вместе с состоянием p0 встречается 0 раза
        s0 вместе с состоянием p1 встречается 0 раза
        s0 вместе с состоянием p2 встречается 5 раза
        s0 вместе с состоянием p3 встречается 14 раза
        Тогда средний выигрыш s0 при условии состояния p0 составит 0 руб. с частотой 0
            средний выигрыш s0 при условии состояния p1 составит 0 руб. с частотой 0
            средний выигрыш s0 при условии состояния p2 составит (57419,073814245 / 5)=11483,814762849 руб. с частотой 5/19
            средний выигрыш s0 при условии состояния p3 составит (−60454,60621777 / 14)=−4318,186158412 руб. с частотой 14/19
     */
    void makeMatrix() {
        System.out.println("----------------------------------------------------------------------------");
        for (int k = 0; k < 4; k++) { // перебираем стратегии
            double cp0 = 0, cp1 = 0, cp2 = 0, cp3 = 0; // счетчики выпавших состояний природы при стратегии k
            double ss0 = 0, ss1 = 0, ss2 = 0, ss3 = 0; // сумма выигрыша стратегии k при определенном состоянии природы

            for (int i = 0; i < sz / portionSize; i++) { // перебираем выборки и смотрим соответствие стратегии и состояния
                if (sampling[i].environment_state[k] == 0) {
                    cp0++;
                    ss0 += sampling[i].profit[k];
                } else if (sampling[i].environment_state[k] == 1) {
                    cp1++;
                    ss1 += sampling[i].profit[k];
                } else if (sampling[i].environment_state[k] == 2) {
                    cp2++;
                    ss2 += sampling[i].profit[k];
                } else if (sampling[i].environment_state[k] == 3) {
                    cp3++;
                    ss3 += sampling[i].profit[k];
                }
            }

            if (cp0 > 0) {
                ss0 = ss0 / cp0;
                cp0 = cp0 / ((double) sz / portionSize);
            }
            if (cp1 > 0) {
                ss1 = ss1 / cp1;
                cp1 = cp1 / ((double) sz / portionSize);
            }
            if (cp2 > 0) {
                ss2 = ss2 / cp2;
                cp2 = cp2 / ((double) sz / portionSize);
            }
            if (cp3 > 0) {
                ss3 = ss3 / cp3;
                cp3 = cp3 / ((double) sz / portionSize);
            }

            // Выводим строку матрицы
            System.out.println("s" + (k + 1) +" | " + ss0 + " " + cp0 + " | " + ss1 + " " + cp1 + " | " + ss2 + " " + cp2 + " | " + ss3 + " " + cp3 + " |");

            matrix[k][0] = new MatrixElement(ss0, cp0);
            matrix[k][1] = new MatrixElement(ss1, cp1);
            matrix[k][2] = new MatrixElement(ss2, cp2);
            matrix[k][3] = new MatrixElement(ss3, cp3);
        }

        System.out.println("----------------------------------------------------------------------------");
        System.out.print("Average X by П |");
        for (int j = 0; j < 4; j++) {
            double sum = 0;
            for (int i = 0; i < 4; i++) {
                sum += matrix[i][j].value;
            }
            double average = sum / 4;
            System.out.print(" " + average + " |");
        }
        System.out.println();
    }

    void printCriteria() {
        System.out.println("----------------------------------------------------------------------------");
        printBayesCriteria();
        printWaldPessimismCriteria();
        printOptimismCriteria();
        printHurwichCriteria();
        printSavageCriteria();
    }

    void printBayesCriteria() {
        double[] bs = new double[4];
        for (int i = 0; i < 4; i++) {
            double b = 0;
            for (MatrixElement element : matrix[i]) {
                b += element.prob * element.value;
            }
            bs[i] = b;
        }
        double max = Arrays.stream(bs).max().getAsDouble();
        ArrayList<Integer> bestStrategies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (bs[i] == max) {
                bestStrategies.add(i + 1);
            }
        }
        System.out.println("По критерию Байеса наилучшие стратегии: " + bestStrategies);
    }

    void printWaldPessimismCriteria() {
        List<Double> ws = Arrays.stream(matrix)
                .map(elements -> Arrays.stream(elements).map(e -> e.value).min(Double::compareTo).get())
                .toList();
        Double max = ws.stream().max(Double::compareTo).get();
        ArrayList<Integer> bestStrategies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (max.equals(ws.get(i))) {
                bestStrategies.add(i + 1);
            }
        }
        System.out.println("По критерию Вальда (пессимизма) наилучшие стратегии: " + bestStrategies);
    }

    void printOptimismCriteria() {
        List<Double> os = Arrays.stream(matrix)
                .map(elements -> Arrays.stream(elements).map(e -> e.value).max(Double::compareTo).get())
                .toList();
        Double max = os.stream().max(Double::compareTo).get();
        ArrayList<Integer> bestStrategies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (max.equals(os.get(i))) {
                bestStrategies.add(i + 1);
            }
        }
        System.out.println("По критерию оптимизма наилучшие стратегии: " + bestStrategies);
    }

    void printHurwichCriteria() {
        List<Double> bs = Arrays.stream(matrix)
                .map(elements -> Arrays.stream(elements).map(e -> e.value).min(Double::compareTo).get())
                .toList();
        List<Double> os = Arrays.stream(matrix)
                .map(elements -> Arrays.stream(elements).map(e -> e.value).max(Double::compareTo).get())
                .toList();
        double pessimismCoef = 0.5;
        double optimismCoef = 1 - pessimismCoef;

        double[] hs = new double[4];
        for (int i = 0; i < 4; i++) {
            hs[i] = pessimismCoef * bs.get(i) + optimismCoef * os.get(i);
        }
        double max = Arrays.stream(hs).max().getAsDouble();
        ArrayList<Integer> bestStrategies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (hs[i] == max) {
                bestStrategies.add(i + 1);
            }
        }
        System.out.println("По критерию Гурвица наилучшие стратегии: " + bestStrategies);
    }

    void printSavageCriteria() {
        double[][] risksTable = new double[4][4];
        for (int j = 0; j < 4; j++) {
            double columnMax = 0;
            for (int i = 0; i < 4; i++) {
                columnMax = Double.max(columnMax, matrix[i][j].value);
            }
            for (int i = 0; i < 4; i++) {
                risksTable[i][j] = columnMax - matrix[i][j].value;
            }
        }
        List<Double> rowsMaxs = Arrays.stream(risksTable).map(row -> Arrays.stream(row).max().getAsDouble()).toList();
        Double min = rowsMaxs.stream().min(Double::compareTo).get();
        ArrayList<Integer> bestStrategies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (min.equals(rowsMaxs.get(i))) {
                bestStrategies.add(i + 1);
            }
        }
        System.out.println("По критерию Сэвиджа наилучшие стратегии: " + bestStrategies);
    }
}
