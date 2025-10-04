import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class TicketBookingDemo {

    static class BookingClosedException extends Exception {
        BookingClosedException(String message) { super(message); }
    }

    static class BookingResult {
        final String clientName;
        final int requested;
        final List<Integer> allocated;
        final String message;
        final boolean success;

        BookingResult(String clientName, int requested, List<Integer> allocated, boolean success, String message) {
            this.clientName = clientName;
            this.requested = requested;
            this.allocated = allocated == null ? List.of() : allocated;
            this.success = success;
            this.message = message;
        }

        String humanReadable() {
            if (success) {
                String tickets = allocated.stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "));
                return "✅ Клiєнт: " + clientName +
                        " | Запитано: " + requested +
                        " | Видано квитки №: [" + tickets + "]" +
                        " | Статус: ПiДТВЕРДЖЕНО.";
            } else {
                return "❌ Клiєнт: " + clientName +
                        " | Запитано: " + requested +
                        " | Статус: ВiДХИЛЕНО. Причина: " + message;
            }
        }
    }
    static class TicketInventory {
        private final Semaphore permits; // кiлькiсть доступних квиткiв
        private final ConcurrentLinkedQueue<Integer> freeTickets; // номери квиткiв
        private final ZoneId zone;

        private static final LocalTime CLOSED_FROM = LocalTime.of(0, 0);
        private static final LocalTime CLOSED_TO   = LocalTime.of(6, 0);

        TicketInventory(int totalTickets, ZoneId zone) {
            this.permits = new Semaphore(totalTickets, true);
            this.freeTickets = new ConcurrentLinkedQueue<>();
            for (int i = 1; i <= totalTickets; i++) freeTickets.add(i);
            this.zone = zone;
        }

        private void ensureBookingOpen() throws BookingClosedException {
            LocalTime now = LocalTime.now(zone);
            boolean closed = !now.isBefore(CLOSED_TO) ? false : true;
            if (closed) {
                String nowStr = now.format(DateTimeFormatter.ofPattern("HH:mm"));
                throw new BookingClosedException(
                        "Бронювання тимчасово недоступне з 00:00 до 06:00. Поточний час: " + nowStr
                );
            }
        }

        BookingResult tryBook(String clientName, int count) {
            try {
                if (count <= 0) {
                    return new BookingResult(clientName, count, null, false,
                            "Невiрна кiлькiсть квиткiв (" + count + "). Має бути 1 або бiльше.");
                }
                ensureBookingOpen();

                boolean acquired = permits.tryAcquire(count, 200, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    int available = permits.availablePermits();
                    return new BookingResult(clientName, count, null, false,
                            "Недостатньо вiльних квиткiв. Доступно зараз: " + available + ".");
                }

                List<Integer> allocated = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Integer ticket = freeTickets.poll();
                    if (ticket == null) {
                        for (Integer t : allocated) freeTickets.add(t);
                        permits.release(i); 
                        throw new IllegalStateException("Збiй видiлення квиткiв — неочiкувано закiнчились номери.");
                    }
                    allocated.add(ticket);
                }

                return new BookingResult(
                        clientName, count, allocated, true,
                        "Успiшно видiлено " + count + " квиткiв."
                );

            } catch (BookingClosedException e) {
                return new BookingResult(clientName, count, null, false, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new BookingResult(clientName, count, null, false,
                        "Операцiю перервано. Спробуйте знову.");
            } catch (Exception e) {
                return new BookingResult(clientName, count, null, false,
                        "Системна помилка: " + e.getMessage());
            }
        }

        int remaining() {
            return permits.availablePermits();
        }
    }


    static class BookingClient implements Runnable {
        private final String name;
        private final int requestCount;
        private final TicketInventory inventory;
        private final List<BookingResult> sink;

        BookingClient(String name, int requestCount, TicketInventory inventory, List<BookingResult> sink) {
            this.name = name;
            this.requestCount = requestCount;
            this.inventory = inventory;
            this.sink = sink;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(20, 120));
                BookingResult result = inventory.tryBook(name, requestCount);
                synchronized (sink) { sink.add(result); }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                synchronized (sink) {
                    sink.add(new BookingResult(name, requestCount, null, false, "Клiєнта перервано пiд час очiкування."));
                }
            }
        }
    }


    static class ThreadStateMonitor implements Runnable {
        private final List<Thread> threads;
        private volatile boolean running = true;

        ThreadStateMonitor(List<Thread> threads) { this.threads = threads; }

        public void stop() { running = false; }

        @Override
        public void run() {
            try {
                while (running) {
                    String snapshot = threads.stream()
                            .map(t -> t.getName() + ":" + t.getState())
                            .collect(Collectors.joining(" | "));
                    System.out.println("[Монiторинг] Стани потокiв: " + snapshot);
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        final ZoneId zone = ZoneId.systemDefault();
        final int TOTAL_TICKETS = 10;

        System.out.println("=== СИСТЕМА БРОНЮВАННЯ КВИТКiВ ===");
        System.out.println("Поточний час: " + ZonedDateTime.now(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")));
        System.out.println("Доступно квиткiв на стартi: " + TOTAL_TICKETS);
        System.out.println("Правило: бронювання ЗАБОРОНЕНО з 00:00 до 06:00.\n");

        TicketInventory inventory = new TicketInventory(TOTAL_TICKETS, zone);

        List<BookingResult> results = Collections.synchronizedList(new ArrayList<>());

        List<Thread> clients = new ArrayList<>();
        clients.add(new Thread(new BookingClient("Олена", 3, inventory, results), "Client-Олена"));
        clients.add(new Thread(new BookingClient("Артем", 4, inventory, results), "Client-Артем"));
        clients.add(new Thread(new BookingClient("Марiя", 2, inventory, results), "Client-Марiя"));
        clients.add(new Thread(new BookingClient("Сергiй", 5, inventory, results), "Client-Сергiй"));
        clients.add(new Thread(new BookingClient("Наталя", 1, inventory, results), "Client-Наталя"));

        System.out.println("Початковi стани потокiв:");
        for (Thread t : clients) {
            System.out.println(" - " + t.getName() + " -> " + t.getState()); // NEW
        }
        System.out.println();

        ThreadStateMonitor monitor = new ThreadStateMonitor(clients);
        Thread monitorThread = new Thread(monitor, "ThreadStateMonitor");
        monitorThread.setDaemon(true);
        monitorThread.start();

        clients.forEach(Thread::start);

        System.out.println("Стани вiдразу пiсля старту:");
        for (Thread t : clients) {
            System.out.println(" - " + t.getName() + " -> " + t.getState()); // RUNNABLE/…
        }
        System.out.println();

        for (Thread t : clients) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("⚠ Головний потiк перервано пiд час очiкування: " + e.getMessage());
            }
        }
        monitor.stop();

        System.out.println("Фiнальнi стани потокiв:");
        for (Thread t : clients) {
            System.out.println(" - " + t.getName() + " -> " + t.getState()); // TERMINATED
        }
        System.out.println();

        System.out.println("=== ПiДСУМКИ ДЛЯ КОРИСТУВАЧА ===");
        results.forEach(r -> System.out.println(r.humanReadable()));
        System.out.println("\nЗалишок вiльних квиткiв: " + inventory.remaining());
        System.out.println("Дякуємо! Якщо квиткiв не вистачило — спробуйте змiнити кiлькiсть або час.");
    }
}

