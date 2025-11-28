import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.awt.Desktop;

public class ParallelImageFinder {

    // ---- Перевiрка, чи файл є зображенням (по розширенню) ----
    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
               name.endsWith(".png") || name.endsWith(".gif")  ||
               name.endsWith(".bmp") || name.endsWith(".webp") ||
               name.endsWith(".tiff")|| name.endsWith(".svg");
    }

    // ---- Завдання для Fork/Join: рекурсивний обхiд директорiї ----
    private static class ImageSearchTask extends RecursiveTask<List<File>> {
        private final File directory;

        public ImageSearchTask(File directory) {
            this.directory = directory;
        }

        @Override
        protected List<File> compute() {
            List<File> result = new ArrayList<>();
            List<ImageSearchTask> subTasks = new ArrayList<>();

            File[] children = directory.listFiles();
            if (children == null) {
                return result;
            }

            for (File child : children) {
                if (child.isFile()) {
                    if (isImageFile(child)) {
                        result.add(child);
                    }
                } else if (child.isDirectory()) {
                    // Для пiддиректорiй створюємо окремi пiдзадачi
                    ImageSearchTask subTask = new ImageSearchTask(child);
                    subTask.fork(); // запускаємо асинхронно (work stealing)
                    subTasks.add(subTask);
                }
            }

            // Збираємо результати з пiдзадач
            for (ImageSearchTask subTask : subTasks) {
                result.addAll(subTask.join());
            }

            return result;
        }
    }

    // ---- Ввiд директорiї вiд користувача ----
    private static File readDirectoryFromUser(Scanner scanner) {
        while (true) {
            System.out.print("Введiть шлях до директорiї: ");
            String path = scanner.nextLine().trim();

            File dir = new File(path);
            if (!dir.exists()) {
                System.out.println("Такого шляху не iснує. Спробуйте ще раз.");
                continue;
            }
            if (!dir.isDirectory()) {
                System.out.println("Це не директорiя. Вкажiть саме директорiю.");
                continue;
            }
            return dir;
        }
    }

    // ---- Спроба вiдкрити файл у системному переглядачi ----
    private static void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
                System.out.println("Вiдкриваємо файл: " + file.getAbsolutePath());
            } else {
                System.out.println("Desktop API не пiдтримується. Вiдкрийте файл вручну:");
                System.out.println(file.getAbsolutePath());
            }
        } catch (IOException e) {
            System.out.println("Не вдалося вiдкрити файл: " + e.getMessage());
            System.out.println("Шлях до файлу: " + file.getAbsolutePath());
        }
    }

    // ---- MAIN ----
    public static void main(String[] args) {
        System.out.println("=== Пошук зображень у директорiї (паралельно, Fork/Join, Work Stealing) ===");

        try (Scanner scanner = new Scanner(System.in)) {
            // 1. Користувач обирає директорiю
            File rootDir = readDirectoryFromUser(scanner);

            // 2. Створюємо ForkJoinPool (це спецiальний Thread Pool з work stealing)
            ForkJoinPool pool = ForkJoinPool.commonPool();
            ImageSearchTask rootTask = new ImageSearchTask(rootDir);

            long start = System.nanoTime();
            List<File> images = pool.invoke(rootTask);
            long end = System.nanoTime();
            long durationMs = (end - start) / 1_000_000;

            // 3. Виводимо кiлькiсть знайдених файлiв
            System.out.println("\nЗнайдено зображень: " + images.size());
            System.out.println("Час пошуку: " + durationMs + " мс");

            // 4. Вiдкриваємо останнiй знайдений файл (якщо є)
            if (!images.isEmpty()) {
                File lastImage = images.get(images.size() - 1);
                System.out.println("Останнiй знайдений файл:");
                System.out.println(lastImage.getAbsolutePath());
                openFile(lastImage);
            } else {
                System.out.println("Зображень у цiй директорiї (та пiддиректорiях) не знайдено.");
            }
        }
    }
}