package com.github.kjetilv.fjorth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

public final class Repl {

    public static void main(String[] args) throws IOException {
        PrintWriter out = new PrintWriter(System.out, true);
        Machine machine = new Machine();
        Interpreter interpreter = Bootstrap.interpreter(machine, out);
        out.println("fjorth");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            for (String line = in.readLine(); line != null; line = in.readLine()) {
                try {
                    interpreter.interpret(line);
                    out.println(" ok");
                } catch (ForthException e) {
                    out.println();
                    out.println(e.getMessage());
                    interpreter.reset();
                }
            }
        }
    }
}
