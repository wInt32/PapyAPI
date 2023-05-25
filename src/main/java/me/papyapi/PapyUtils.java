package me.papyapi;

import java.io.IOException;
import java.io.InputStream;

public class PapyUtils {
    public static String readInputStreamTillChar(InputStream stream, char end) throws IOException {
        StringBuilder lineBuilder = new StringBuilder();
        int c = 0;

        while (true) {
            c = stream.read();

            if (c != end) {

                lineBuilder.append((char) c);

            } else
                break;

            if (c == -1)
                return "";
        }

        return lineBuilder.toString();

    }

    public static String readStringTillChar(String string, char end) {
        StringBuilder lineBuilder = new StringBuilder();
        int c = 0;

        for (int i = 0; i < string.length(); i++) {
            c = string.charAt(i);

            if (c == end)
                break;

            lineBuilder.append((char) c);

        }

        return lineBuilder.toString();

    }

    public static int getIndexOfChar(String string, char end) {
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == end)
                return i;

        }

        return -1;
    }
}
