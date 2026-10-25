
package com.redislite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RespProtocol {

    private RespProtocol() {
    }

    public static List<String> readCommand(InputStream in)
            throws IOException {

        int marker = in.read();

        if (marker == -1) {
            return null;
        }

        if (marker != '*') {
            throw new IOException("Expected RESP array");
        }

        int count = parseNumber(readLine(in), "array length");

        if (count < 1 || count > 1024) {
            throw new IOException("Invalid command argument count");
        }

        List<String> parts = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            if (in.read() != '$') {
                throw new IOException("Expected RESP bulk string");
            }

            int length = parseNumber(readLine(in), "bulk string length");

            if (length < 0 || length > 1024 * 1024) {
                throw new IOException("Invalid bulk string length");
            }

            byte[] data = in.readNBytes(length);

            if (data.length != length) {
                throw new IOException("Unexpected end of command");
            }

            if (in.read() != '\r' || in.read() != '\n') {
                throw new IOException("Invalid bulk string terminator");
            }

            parts.add(new String(data, StandardCharsets.UTF_8));
        }

        return parts;
    }

    private static int parseNumber(String value, String description)
            throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid " + description);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();

        while (true) {
            int b = in.read();

            if (b == -1) {
                throw new IOException("Unexpected end of input");
            }

            if (b == '\r') {
                if (in.read() != '\n') {
                    throw new IOException("Expected LF after CR");
                }

                return line.toString();
            }

            if (line.length() >= 32) {
                throw new IOException("RESP line too long");
            }

            line.append((char) b);
        }
    }

    public static void writeSimpleString(
            OutputStream output, String value) throws IOException {
        write(output, "+" + value + "\r\n");
    }

    public static void writeError(
            OutputStream output, String message) throws IOException {
        write(output, "-" + message + "\r\n");
    }

    public static void writeBulkString(
            OutputStream output, String value) throws IOException {

        if (value == null) {
            write(output, "$-1\r\n");
            return;
        }

        byte[] data = value.getBytes(StandardCharsets.UTF_8);

        write(output, "$" + data.length + "\r\n");
        output.write(data);
        write(output, "\r\n");
    }

    public static void writeInteger(
            OutputStream output, long value) throws IOException {
        write(output, ":" + value + "\r\n");
    }

    private static void write(OutputStream output, String value)
            throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
