import java.util.*;
import java.util.concurrent.*;

public class MatrixIndexSumSearchSimple {

    // ================== РЕЗУЛЬТАТ ==================
    // Просто масив: [row, col, value], або null якщо не знайдено
    private static void printResult(int[] res, String label, long timeMs) {
        System.out.println("\n=== " + label + " ===");
        if (res == null) {
            System.out.println("Елемент, що дорiвнює сумi iндексiв (i + j), не знайдено.");
        } else {
            System.out.printf(
                    "Знайдено елемент: value = %d на позицiї [%d][%d] (i + j = %d)\n",
                    res[2], res[0], res[1], res[0] + res[1]
            );
        }
        System.out.println("Час виконання: " + timeMs + " мс");
    }

    // ================== ВВiД КОРИСТУВАЧА ==================
    private static int readInt(Scanner sc, String msg, int min) {
        while (true) {
            System.out.print(msg);
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v < min) {
                    System.out.println("Значення має бути не менше " + min);
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Введiть цiле число.");
            }
        }
    }

    // ================== ГЕНЕРАЦiЯ ТА ВИВiД МАТРИЦi ==================
    private static int[][] generateMatrix(int rows, int cols, int min, int max) {
        int[][] a = new int[rows][cols];
        Random rnd = new Random();
        int bound = max - min + 1;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                a[i][j] = rnd.nextInt(bound) + min;
            }
        }
        return a;
    }

    private static void printMatrix(int[][] a) {
        System.out.println("\nЗгенерований масив:");
        for (int[] row : a) {
            for (int x : row) {
                System.out.printf("%6d", x);
            }
            System.out.println();
        }
    }

    // ================== WORK STEALING (ForkJoin) ==================
    private static class ForkJoinSearchTask extends RecursiveTask<int[]> {
        private static final int THRESHOLD_ROWS = 30;

        private final int[][] a;
        private final int startRow;
        private final int endRow; // exclusive

        ForkJoinSearchTask(int[][] a, int startRow, int endRow) {
            this.a = a;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        protected int[] compute() {
            int rows = endRow - startRow;
            if (rows <= THRESHOLD_ROWS) {
                // Послiдовний пошук у пiддiапазонi
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < a[i].length; j++) {
                        if (a[i][j] == i + j) {
                            return new int[]{i, j, a[i][j]};
                        }
                    }
                }
                return null;
            } else {
                // Дiлимо задачу
                int mid = (startRow + endRow) / 2;
                ForkJoinSearchTask left = new ForkJoinSearchTask(a, startRow, mid);
                ForkJoinSearchTask right = new ForkJoinSearchTask(a, mid, endRow);

                left.fork();
                int[] rightRes = right.compute();
                int[] leftRes = left.join();

                if (leftRes != null) return leftRes;
                return rightRes;
            }
        }
    }

    private static int[] searchForkJoin(int[][] a) {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        return pool.invoke(new ForkJoinSearchTask(a, 0, a.length));
    }

    // ================== WORK DEALING (ExecutorService) ==================
    private static class RangeTask implements Callable<int[]> {
        private final int[][] a;
        private final int startRow;
        private final int endRow;

        RangeTask(int[][] a, int startRow, int endRow) {
            this.a = a;
            this.startRow = startRow;
            this.endRow = endRow;
        }

        @Override
        public int[] call() {
            for (int i = startRow; i < endRow; i++) {
                for (int j = 0; j < a[i].length; j++) {
                    if (a[i][j] == i + j) {
                        return new int[]{i, j, a[i][j]};
                    }
                }
            }
            return null;
        }
    }

    private static int[] searchExecutor(int[][] a) throws InterruptedException {
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService exec = Executors.newFixedThreadPool(threads);

        try {
            int totalRows = a.length;
            int chunk = Math.max(1, totalRows / threads);

            List<Callable<int[]>> tasks = new ArrayList<>();
            for (int start = 0; start < totalRows; start += chunk) {
                int end = Math.min(start + chunk, totalRows);
                tasks.add(new RangeTask(a, start, end));
            }

            List<Future<int[]>> futures = exec.invokeAll(tasks);
            int[] firstFound = null;
            for (Future<int[]> f : futures) {
                try {
                    int[] res = f.get();
                    if (res != null && firstFound == null) {
                        firstFound = res;
                    }
                } catch (ExecutionException e) {
                    System.out.println("Помилка в потоцi: " + e.getCause());
                }
            }
            return firstFound;
        } finally {
            exec.shutdown();
        }
    }

    // ================== MAIN ==================
    public static void main(String[] args) {
        System.out.println("=== Пошук елемента, що дорiвнює i + j, у 2D масивi ===");

        try (Scanner sc = new Scanner(System.in)) {
            int rows = readInt(sc, "Введiть кiлькiсть рядкiв (>0): ", 1);
            int cols = readInt(sc, "Введiть кiлькiсть стовпцiв (>0): ", 1);

            System.out.print("Мiнiмальне значення елементiв: ");
            int min = Integer.parseInt(sc.nextLine().trim());

            int max;
            while (true) {
                System.out.print("Максимальне значення елементiв: ");
                max = Integer.parseInt(sc.nextLine().trim());
                if (max < min) {
                    System.out.println("Максимум не може бути меншим за мiнiмум.");
                } else break;
            }

            int[][] a = generateMatrix(rows, cols, min, max);
            printMatrix(a);

            // Work Stealing (ForkJoin)
            long t1 = System.nanoTime();
            int[] res1 = searchForkJoin(a);
            long t2 = System.nanoTime();
            printResult(res1, "Work Stealing (ForkJoinPool)", (t2 - t1) / 1_000_000);

            // Work Dealing (ExecutorService)
            long t3 = System.nanoTime();
            int[] res2 = searchExecutor(a);
            long t4 = System.nanoTime();
            printResult(res2, "Work Dealing (ExecutorService)", (t4 - t3) / 1_000_000);

            // Коротке порiвняння
            long time1 = (t2 - t1) / 1_000_000;
            long time2 = (t4 - t3) / 1_000_000;
            System.out.println("\n=== Порiвняння часу ===");
            System.out.println("ForkJoin (stealing):  " + time1 + " мс");
            System.out.println("Executor (dealing):   " + time2 + " мс");
        } catch (Exception e) {
            System.out.println("Сталася помилка: " + e.getMessage());
        }
    }
}