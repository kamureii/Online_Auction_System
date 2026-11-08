package com.auction.server.core;

import java.io.IOException;
import java.io.Reader;

/** Đọc từng dòng nhưng từ chối dòng quá dài, để một client không thể làm server cạn bộ nhớ. */
final class BoundedLineReader {
    private BoundedLineReader() {}

    static final class LineTooLongException extends IOException {
        LineTooLongException(int maxChars) {
            super("Dòng dữ liệu vượt quá " + maxChars + " ký tự.");
        }
    }

    /** @return dòng đọc được (không gồm ký tự xuống dòng), hoặc null khi hết dữ liệu. */
    static String readLine(Reader reader, int maxChars) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                return stripTrailingCr(line);
            }
            if (line.length() >= maxChars) {
                throw new LineTooLongException(maxChars);
            }
            line.append((char) c);
        }
        return line.length() == 0 ? null : stripTrailingCr(line);
    }

    private static String stripTrailingCr(StringBuilder line) {
        int end = line.length();
        if (end > 0 && line.charAt(end - 1) == '\r') {
            end--;
        }
        return line.substring(0, end);
    }
}
