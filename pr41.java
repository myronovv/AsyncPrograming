import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompletableFutureTask2 {

    private static final Random random = new Random();

    public static void main(String[] args) {
        ExecutorService executor = Executors.newFixedThreadPool(3);

        long totalStart = System.nanoTime(); 

        CompletableFuture<double[]> sequenceFuture = CompletableFuture.supplyAsync(() -> {
            double[] a = new double[20];
            for (int i = 0; i < a.length; i++) {
                a[i] = -10.0 + 20.0 * random.nextDouble();
            }
            return a;
        }, executor);

        CompletableFuture<Double> productFuture = sequenceFuture.thenApplyAsync(a -> {
            double product = 1.0;
            for (int i = 1; i < a.length; i++) {
                product *= (a[i] - a[i - 1]);
            }
            return product;
        }, executor);

        CompletableFuture<Void> printSequenceFuture = sequenceFuture.thenAcceptAsync(a -> {
            System.out.println("Початкова послідовність (20 дійсних чисел):");
            System.out.println(Arrays.toString(a));
        }, executor);

        CompletableFuture<Void> printResultFuture = productFuture.thenAcceptAsync(result -> {
            System.out.println("Результат добутку різниць (a2-a1)*(a3-a2)*...*(an-a(n-1)):");
            System.out.println(result);
        }, executor);

        CompletableFuture<Void> allDone = CompletableFuture
                .allOf(printSequenceFuture, printResultFuture)
                .thenRunAsync(() -> {
                    long totalEnd = System.nanoTime();
                    double totalMs = (totalEnd - totalStart) / 1_000_000.0;
                    System.out.printf("Час роботи всіх асинхронних операцій: %.3f ms%n", totalMs);
                }, executor);

        allDone.join();
        executor.shutdown();
    }
}
