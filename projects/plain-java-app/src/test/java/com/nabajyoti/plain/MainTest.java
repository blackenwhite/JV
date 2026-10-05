package com.nabajyoti.plain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void mainPrintsExpectedGreeting() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            Main.main(new String[0]);

            assertEquals(
                    "Hello from the plain Java project." + System.lineSeparator(),
                    output.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }
}
