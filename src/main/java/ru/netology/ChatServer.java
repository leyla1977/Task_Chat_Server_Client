package ru.netology;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

// ---------------------------
// Сервер для группового чата
// ---------------------------
public class ChatServer {

    private final int port;                        // порт сервера
    private final ExecutorService pool = Executors.newFixedThreadPool(64); // пул потоков для клиентов
    private final Set<PrintWriter> clientOutputs = new HashSet<>();        // список потоков для рассылки сообщений
    private final String logFile = "file.log";     // файл логов

    private volatile boolean running = false;      // индикатор работы сервера
    private ServerSocket serverSocket;             // сокет сервера

    // Конструктор — чтение порта из файла настроек
    public ChatServer(String settingsFile) throws IOException {
        this.port = readPortFromSettings(settingsFile);
    }

    // Конструктор — вручную указываем порт (для тестов)
    public ChatServer(int port) {
        this.port = port;
    }

    // Проверка, запущен ли сервер
    public boolean isRunning() {
        return running;
    }

    // Чтение порта из файла настроек
    private int readPortFromSettings(String settingsFile) throws IOException {
        for (String line : Files.readAllLines(Path.of(settingsFile))) {
            line = line.trim();
            if (line.startsWith("port=")) {
                return Integer.parseInt(line.split("=")[1]);
            }
        }
        throw new IllegalArgumentException("Порт не найден в файле настроек");
    }

    // Запуск сервера
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Сервер чата запущен на порту " + port);

        // Отдельный поток для отправки сообщений с консоли сервера
        new Thread(() -> {
            try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
                String serverMsg;
                while (running && (serverMsg = console.readLine()) != null) {
                    if (serverMsg.equalsIgnoreCase("/exit")) break; // команда завершения
                    broadcast("Сервер", serverMsg); // рассылаем сообщение всем клиентам
                }
                stop(); // если введено "/exit" → останавливаем сервер
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Основной цикл ожидания подключений
        try {
            while (running) {
                try {
                    Socket socket = serverSocket.accept(); // ждем клиента
                    pool.submit(() -> handleClient(socket)); // обрабатываем клиента в отдельном потоке
                } catch (SocketException e) {
                    break; // если сервер закрыт — выходим из цикла
                }
            }
        } finally {
            stop();
        }
    }

    // Остановка сервера
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        pool.shutdownNow();
        synchronized (clientOutputs) {
            for (PrintWriter out : clientOutputs) {
                out.close();
            }
            clientOutputs.clear();
        }
        System.out.println("Сервер остановлен");
    }

    // Работа с клиентом
    private void handleClient(Socket socket) {
        try (
                socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            synchronized (clientOutputs) {
                clientOutputs.add(out); // добавляем клиента в список получателей
            }

            out.println("Введите свое имя:");
            String name = in.readLine();

            // Сообщаем всем, что подключился новый клиент
            broadcast("Сервер", "Клиент " + name + " присоединился(лась) к чату");

            String message;
            while ((message = in.readLine()) != null) {
                if (message.equalsIgnoreCase("/exit")) break; // клиент хочет выйти
                broadcast(name, message); // рассылаем сообщение
            }

            // Удаляем клиента и сообщаем об отключении
            synchronized (clientOutputs) {
                clientOutputs.remove(out);
            }
            broadcast("Сервер", "Клиент " + name + " покинул(а) чат");

        } catch (IOException e) {
            System.out.println("Клиент отключился: " + e.getMessage());
        }
    }

    // Рассылка сообщений всем клиентам
    private void broadcast(String sender, String message) {
        String formattedMessage = "Сообщение от " + sender + ": " + message;
        synchronized (clientOutputs) {
            for (PrintWriter out : clientOutputs) {
                out.println(formattedMessage);
            }
        }
        System.out.println(formattedMessage);
        logMessage(sender, message); // записываем в лог
    }

    // Запись сообщений в file.log
    private void logMessage(String sender, String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String line = String.format("[%s] %s: %s", timestamp, sender, message);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            pw.println(line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        ChatServer server = new ChatServer("settings.txt");
        server.start();
    }
}



