package ru.netology;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// ---------------------------
// Клиент чата
// ---------------------------
public class ChatClient {
    private final String host;            // адрес сервера
    private final int port;               // порт сервера
    private final String logFile = "file.log"; // файл логов сервера

    // Конструктор — читаем порт из файла настроек
    public ChatClient(String host, String settingsFile) throws IOException {
        this.host = host;
        this.port = readPortFromSettings(settingsFile);
    }

    // Чтение порта из файла
    private int readPortFromSettings(String settingsFile) throws IOException {
        for (String line : Files.readAllLines(Path.of(settingsFile))) {
            line = line.trim();
            if (line.startsWith("port=")) {
                return Integer.parseInt(line.split("=")[1]);
            }
        }
        throw new IllegalArgumentException("Порт не найден в файле настроек");
    }

    // Основной метод клиента
    public void start() {
        try (
                Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader console = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Подключено к чату на сервере " + host + ":" + port);

            // Отслеживание отправленных сообщений
            Set<String> sentMessages = new HashSet<>();

            // Поток для получения сообщений с сервера
            new Thread(() -> {
                try {
                    String serverMsg;
                    while ((serverMsg = in.readLine()) != null) {
                        System.out.println(serverMsg);

                        // Логируем только чужие сообщения
                        String[] parts = serverMsg.split(": ", 2);
                        if (parts.length == 2) {
                            String msgText = parts[1];
                            if (!sentMessages.remove(msgText)) {
                                logMessage(parts[0], msgText);
                            }
                        } else {
                            logMessage("Сервер", serverMsg);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Соединение закрыто сервером");
                }
            }).start();

            // Отправка сообщений на сервер
            String userMsg;
            while ((userMsg = console.readLine()) != null) {
                if (userMsg.equalsIgnoreCase("/exit")) break;
                out.println(userMsg);
                sentMessages.add(userMsg);
                logMessage("Клиент", userMsg);
            }

        } catch (IOException e) {
            System.out.println("Не удалось подключиться к серверу: " + e.getMessage());
        }
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

    // Метод для тестов (отправляет 1 сообщение и получает ответ)
    public void start(String message) {
        try (
                Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            System.out.println("Тестовый клиент подключился к серверу " + host + ":" + port);
            out.println(message);
            logMessage("Клиент (тест)", message);

            String serverResponse = in.readLine();
            if (serverResponse != null) {
                System.out.println("Ответ сервера: " + serverResponse);
                logMessage("Сервер", serverResponse);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient("localhost", "settings.txt");
        client.start();
    }
}


