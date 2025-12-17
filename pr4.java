import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureDemo {

    private static final Random random = new Random();

    public static void main(String[] args) {
        
        ExecutorService executor = Executors.newFixedThreadPool(4);

        
        CompletableFuture<int[]> generateFuture =
                timedSupplyAsync("Генерація масиву (int[10])", () -> {
                    int[] arr = new int[10];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = random.nextInt(51); // 0..50
                    }
                    sleepMs(250); 
                    return arr;
                }, executor);

        CompletableFuture<int[]> plusTenFuture =
                timedThenApplyAsync("Модифікація: +10 до кожного", generateFuture, (int[] arr) -> {
                    int[] modified = Arrays.copyOf(arr, arr.length);
                    for (int i = 0; i < modified.length; i++) {
                        modified[i] += 10;
                    }
                    sleepMs(250);
                    return modified;
                }, executor);

        CompletableFuture<double[]> divideByTwoFuture =
                timedThenApplyAsync("Модифікація: ділення на 2", plusTenFuture, (int[] arr) -> {
                    double[] result = new double[arr.length];
                    for (int i = 0; i < arr.length; i++) {
                        result[i] = arr[i] / 2.0; 
                    }
                    sleepMs(250);
                    return result;
                }, executor);

        CompletableFuture<Void> printOriginal =
                timedThenAcceptAsync("Вивід початкового масиву", generateFuture, (int[] arr) -> {
                    System.out.println("Початковий масив: " + Arrays.toString(arr));
                    sleepMs(150);
                }, executor);

        CompletableFuture<Void> printPlusTen =
                timedThenAcceptAsync("Вивід масиву після +10", plusTenFuture, (int[] arr) -> {
                    System.out.println("Після +10: " + Arrays.toString(arr));
                    sleepMs(150);
                }, executor);

        CompletableFuture<Void> printFinal =
                timedThenAcceptAsync("Вивід фінального масиву", divideByTwoFuture, (double[] arr) -> {
                    System.out.println("Результат ділення: " + Arrays.toString(arr));
                    sleepMs(150);
                }, executor);

        CompletableFuture<Void> extraRun =
                timedRunAsync("Додаткова задача runAsync (лог/пауза)", () -> {
                    System.out.println("runAsync: паралельна службова задача виконується...");
                    sleepMs(200);
                }, executor);

        CompletableFuture<Void> allDone =
                CompletableFuture.allOf(printOriginal, printPlusTen, printFinal, extraRun)
                        .thenRunAsync(() -> System.out.println("✅ Усі асинхронні задачі завершено."), executor);

        allDone.join();

        executor.shutdown();
    }


    private static CompletableFuture<Void> timedRunAsync(String taskName, Runnable task, ExecutorService executor) {
        return CompletableFuture.runAsync(() -> {
            long start = System.nanoTime();
            task.run();
            long end = System.nanoTime();
            printTime(taskName, start, end);
        }, executor);
    }

    private static <T> CompletableFuture<T> timedSupplyAsync(String taskName, SupplierWithException<T> supplier,
                                                            ExecutorService executor) {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            try {
                T result = supplier.get();
                long end = System.nanoTime();
                printTime(taskName, start, end);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static <T, R> CompletableFuture<R> timedThenApplyAsync(String taskName, CompletableFuture<T> prev,
                                                                   FunctionWithException<T, R> fn,
                                                                   ExecutorService executor) {
        return prev.thenApplyAsync(value -> {
            long start = System.nanoTime();
            try {
                R result = fn.apply(value);
                long end = System.nanoTime();
                printTime(taskName, start, end);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static <T> CompletableFuture<Void> timedThenAcceptAsync(String taskName, CompletableFuture<T> prev,
                                                                    ConsumerWithException<T> consumer,
                                                                    ExecutorService executor) {
        return prev.thenAcceptAsync(value -> {
            long start = System.nanoTime();
            try {
                consumer.accept(value);
                long end = System.nanoTime();
                printTime(taskName, start, end);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    private static void printTime(String taskName, long startNano, long endNano) {
        double ms = (endNano - startNano) / 1_000_000.0;
        System.out.printf("⏱ %s: %.3f ms%n", taskName, ms);
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface FunctionWithException<T, R> {
        R apply(T t) throws Exception;
    }

    @FunctionalInterface
    private interface ConsumerWithException<T> {
        void accept(T t) throws Exception;
    }
}
