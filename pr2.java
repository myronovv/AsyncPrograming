import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;








public class Main {

    public static void main(String[] args) {
        long startTime = System.nanoTime(); 

        
        Random random = new Random();

        int minSize = 40;
        int maxSize = 60;
        int size = random.nextInt(maxSize - minSize + 1) + minSize; 

        
        if (size % 2 != 0) {
            size--; 
        }

        int[] numbers = new int[size];
        for (int i = 0; i < size; i++) {
            numbers[i] = random.nextInt(101);
        }

        System.out.println("Згенерований масив (" + size + " елементiв):");
        System.out.println(Arrays.toString(numbers));
        System.out.println();

        
        int numThreads = Runtime.getRuntime().availableProcessors();
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

        
        CopyOnWriteArraySet<Integer> completedChunks = new CopyOnWriteArraySet<>();

        

        int totalPairs = numbers.length / 2;

        
        int pairsPerChunk = (int) Math.ceil((double) totalPairs / numThreads);
        if (pairsPerChunk == 0) {
            pairsPerChunk = 1;
        }

        List<Future<int[]>> futures = new ArrayList<>();

        int currentPairIndex = 0;
        int chunkId = 0;

        while (currentPairIndex < totalPairs) {
            int startPair = currentPairIndex;
            int endPair = Math.min(currentPairIndex + pairsPerChunk, totalPairs);

            int fromIndex = startPair * 2;      
            int toIndexExclusive = endPair * 2; 

            PairProductTask task = new PairProductTask(
                    numbers,
                    fromIndex,
                    toIndexExclusive,
                    chunkId,
                    completedChunks
            );

            Future<int[]> future = executorService.submit(task);
            futures.add(future);

            currentPairIndex = endPair;
            chunkId++;
        }

       
        System.out.println("Статус задач одразу пiсля submit():");
        for (int i = 0; i < futures.size(); i++) {
            System.out.println("  Task " + i + " isDone=" + futures.get(i).isDone());
        }
        System.out.println();

        
        Future<?> cancelledFuture = executorService.submit(() -> {
            try {
                Thread.sleep(5000); 
            } catch (InterruptedException e) {
                
            }
        });

        boolean cancelled = cancelledFuture.cancel(true); 
        System.out.println("Статус скасованої (штучної) задачi:");
        System.out.println("  cancel() result = " + cancelled);
        System.out.println("  isCancelled() = " + cancelledFuture.isCancelled());
        System.out.println("  isDone() = " + cancelledFuture.isDone());
        System.out.println();

        
        int[] pairProducts = new int[totalPairs];
        int offset = 0;

        for (int i = 0; i < futures.size(); i++) {
            Future<int[]> future = futures.get(i);
            try {
                int[] partialResult = future.get(); 
                System.arraycopy(partialResult, 0, pairProducts, offset, partialResult.length);
                offset += partialResult.length;
                System.out.println("Пiсля future.get(): Task " + i + " isDone=" + future.isDone());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        System.out.println();
        System.out.println("Масив попарних добуткiв:");
        System.out.println(Arrays.toString(pairProducts));

        
        System.out.println();
        System.out.println("Chunk-и, якi завершилися (з CopyOnWriteArraySet): " + completedChunks);

        
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMillis = (endTime - startTime) / 1_000_000;
        System.out.println();
        System.out.println("Час роботи програми: " + durationMillis + " мс");
    }

    /**
     * Callable, який рахує попарнi добутки для частини масиву.
     * Кожен парний iндекс множиться на наступний непарний: a[0]*a[1], a[2]*a[3], ...
     */
    public static class PairProductTask implements Callable<int[]> {

        private final int[] source;
        private final int from; // включно
        private final int to;   // виключно
        private final int chunkId;
        private final CopyOnWriteArraySet<Integer> completedChunks;

        public PairProductTask(int[] source,
                               int from,
                               int to,
                               int chunkId,
                               CopyOnWriteArraySet<Integer> completedChunks) {
            this.source = source;
            this.from = from;
            this.to = to;
            this.chunkId = chunkId;
            this.completedChunks = completedChunks;
        }

        @Override
        public int[] call() {
            // Кiлькiсть елементiв у цьому шматку
            int length = to - from;
            int pairsCount = length / 2;

            int[] result = new int[pairsCount];

            int indexResult = 0;
            for (int i = from; i < to; i += 2) {
                int product = source[i] * source[i + 1];
                result[indexResult++] = product;

                

            }

            
            completedChunks.add(chunkId);

            System.out.println("Chunk " + chunkId +
                    " обробляє елементи [" + from + "; " + (to - 1) + "] " +
                    "у потоцi: " + Thread.currentThread().getName());

            return result;
        }
    }
}
