
package com.redislite;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static final int PORT = 6379;
    private static final ExecutorService CLIENT_POOL =
            Executors.newFixedThreadPool(10);

    private static RedisStore store;

    public static void main(String[] args) throws IOException {
        store = new RedisStore();

        System.out.println("Starting RedisLite with disk persistence...");
        System.out.println("Listening on port " + PORT);
        System.out.println("Data file: data/redis-lite.db");
        System.out.println("Commands: PING, SET, GET, DEL, TTL");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                CLIENT_POOL.submit(() -> handleClient(client));
            }
        } finally {
            CLIENT_POOL.shutdown();
        }
    }

    private static void handleClient(Socket client) {
        System.out.println("Client connected: " + client.getRemoteSocketAddress());

        try (client) {
            var input = client.getInputStream();
            var output = client.getOutputStream();

            while (!client.isClosed()) {
                List<String> parts = RespProtocol.readCommand(input);

                if (parts == null) {
                    break;
                }

                try {
                    executeCommand(parts, output);
                } catch (IllegalArgumentException e) {
                    RespProtocol.writeError(output, "ERR " + e.getMessage());
                } catch (IOException e) {
                    RespProtocol.writeError(output, "ERR persistence failure");
                    System.err.println("Storage error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Client connection ended: " + e.getMessage());
        }
    }

    private static void executeCommand(
            List<String> parts, java.io.OutputStream output)
            throws IOException {

        String command = parts.get(0).toUpperCase(Locale.ROOT);

        switch (command) {
            case "PING":
                if (parts.size() != 1) {
                    RespProtocol.writeError(output, "ERR wrong number of arguments");
                } else {
                    RespProtocol.writeSimpleString(output, "PONG");
                }
                break;

            case "SET":
                handleSet(parts, output);
                break;

            case "GET":
                if (parts.size() != 2) {
                    RespProtocol.writeError(output, "ERR wrong number of arguments");
                    break;
                }

                RespProtocol.writeBulkString(output, store.get(parts.get(1)));
                break;

            case "DEL":
                if (parts.size() != 2) {
                    RespProtocol.writeError(output, "ERR wrong number of arguments");
                    break;
                }

                RespProtocol.writeInteger(output, store.delete(parts.get(1)));
                break;

            case "TTL":
                if (parts.size() != 2) {
                    RespProtocol.writeError(output, "ERR wrong number of arguments");
                    break;
                }

                RespProtocol.writeInteger(output, store.ttl(parts.get(1)));
                break;

            default:
                RespProtocol.writeError(output, "ERR unknown command '" + command + "'");
        }
    }

    private static void handleSet(
            List<String> parts, java.io.OutputStream output)
            throws IOException {

        if (parts.size() != 3 && parts.size() != 5) {
            RespProtocol.writeError(output, "ERR wrong number of arguments");
            return;
        }

        long ttlSeconds = 0;

        if (parts.size() == 5) {
            if (!parts.get(3).equalsIgnoreCase("EX")) {
                RespProtocol.writeError(output, "ERR expected EX followed by seconds");
                return;
            }

            try {
                ttlSeconds = Long.parseLong(parts.get(4));

                if (ttlSeconds <= 0) {
                    RespProtocol.writeError(output, "ERR TTL must be positive");
                    return;
                }
            } catch (NumberFormatException e) {
                RespProtocol.writeError(output, "ERR invalid TTL");
                return;
            }
        }

        store.set(parts.get(1), parts.get(2), ttlSeconds);
        RespProtocol.writeSimpleString(output, "OK");
    }
}
