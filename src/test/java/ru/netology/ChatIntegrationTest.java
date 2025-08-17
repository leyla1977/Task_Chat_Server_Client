package ru.netology;

import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;

import static org.junit.jupiter.api.Assertions.*;

// ---------------------------
// Интеграционные тесты чата
// ---------------------------
class ChatIntegrationTest {

    private ChatServer server;
    private int port = 5003;

    // Запускаем сервер перед каждым тестом
    @BeforeEach
    void setUp() throws Exception {
        server = new ChatServer(port);
        Thread serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // ждем запуска сервера
        while (!server.isRunning()) {
            Thread.sleep(100);
        }
    }

    // Останавливаем сервер после каждого теста
    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        Thread.sleep(200); // время на закрытие сокетов
    }

    @Test
    void testServerStartsAndStops() {
        assertTrue(server.isRunning(), "Сервер должен быть запущен");
    }

    // Тест сервера и клиента
    @Test
    void testSingleClientMessage() throws Exception {
        try (
                Socket socket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            in.readLine(); // приветствие
            out.println("Alice");
            in.readLine(); // сообщение о присоединении

            out.println("Hello from Alice");
            String response = in.readLine();

            assertNotNull(response);
            assertTrue(response.contains("Alice"));
            assertTrue(response.contains("Hello from Alice"));
        }
    }

    //Имитация подключеия клиента через Telnet
    @Test
    void testTelnetClientInteraction() throws Exception {
        try (
                // Подключаемся к серверу как "telnet"
                Socket telnetSocket = new Socket("localhost", port);
                BufferedReader in = new BufferedReader(new InputStreamReader(telnetSocket.getInputStream()));
                PrintWriter out = new PrintWriter(telnetSocket.getOutputStream(), true)
        ) {
            // 1. Сервер сразу должен прислать приглашение ввести имя
            String greeting = in.readLine();
            assertNotNull(greeting, "Сервер не прислал приглашение для имени");
            assertTrue(greeting.contains("Введите"), "Сообщение сервера должно содержать приглашение");

            // 2. Отправляем имя "telnet", как будто мы печатаем его в терминале telnet
            out.println("telnet");

            // 3. Ждём подтверждение, что сервер увидел имя
            String joinMsg = in.readLine();
            assertNotNull(joinMsg, "Сервер не прислал сообщение о подключении");
            assertTrue(joinMsg.contains("telnet"), "Сообщение должно содержать имя 'telnet'");
            assertTrue(joinMsg.contains("присоединился"), "Сообщение должно сообщать о входе в чат");

            // 4. Отправляем тестовое сообщение, как будто это пишется в telnet
            out.println("Hello from telnet");

            // 5. Читаем ответ и проверяем его
            String response = in.readLine();
            assertNotNull(response, "Сервер не прислал сообщение обратно");
            assertTrue(response.contains("telnet"), "Сообщение должно содержать имя отправителя");
            assertTrue(response.contains("Hello from telnet"), "Сообщение должно содержать текст");
        }
    }

    // Подключение нескольких клиентов
    @Test
    void testMultipleClientsMessageExchange() throws Exception {
        try (
                Socket socket1 = new Socket("localhost", port);
                BufferedReader in1 = new BufferedReader(new InputStreamReader(socket1.getInputStream()));
                PrintWriter out1 = new PrintWriter(socket1.getOutputStream(), true);

                Socket socket2 = new Socket("localhost", port);
                BufferedReader in2 = new BufferedReader(new InputStreamReader(socket2.getInputStream()));
                PrintWriter out2 = new PrintWriter(socket2.getOutputStream(), true)
        ) {
            // клиент 1 подключается
            in1.readLine(); // "Введите имя"
            out1.println("Client1");
            in1.readLine(); // "Сообщение от Сервер: Клиент Client1 присоединился..."

            // клиент 2 подключается
            in2.readLine(); // "Введите имя"
            out2.println("Client2");
            in2.readLine(); // "Сообщение от Сервер: Клиент Client2 присоединился..."

            // клиент 1 отправляет сообщение
            out1.println("Hi from Client1");

            // клиент 2 ждёт сообщение от Client1
            String msgForClient2 = null;
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 3000) { // ждём до 3 секунд
                if (in2.ready()) {
                    String line = in2.readLine();
                    System.out.println("Client2 получил: " + line);
                    if (line.contains("Client1") && line.contains("Hi from Client1")) {
                        msgForClient2 = line;
                        break;
                    }
                }
                Thread.sleep(50);
            }

            // Проверяем, что сообщение получено
            assertNotNull(msgForClient2, "Сообщение от Client1 не было получено Client2");
            assertTrue(msgForClient2.contains("Client1"), "Сообщение должно содержать имя отправителя");
            assertTrue(msgForClient2.contains("Hi from Client1"), "Сообщение должно содержать текст");
        }
    }

}
