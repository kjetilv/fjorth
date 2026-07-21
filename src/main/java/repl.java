import com.github.kjetilv.fjorth.Console;
import com.github.kjetilv.fjorth.Fjorth;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

///  Sally Fjorð!
void main(String[] args) {
    Arrays.stream(args)
        .peek(arg -> CONSOLE.println("*** evaluating file " + arg))
        .map(Path::of)
        .forEach(file ->
            evaluateLines(file, () -> reader(file))
                .ifPresent(failingLine -> {
                    throw new IllegalStateException("Failed to evaluate " + file + ", failing line: " + failingLine);
                }));
    CONSOLE.println("fjorth");
    try (var in = stdin()) {
        in.lines()
            .forEach(this::evaluate);
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}

/// @param source         Source of lines
/// @param readerSupplier Supplier of reader
/// @return Error message for first line that failed, empty if all succeeded
private Optional<String> evaluateLines(
    Object source,
    Supplier<BufferedReader> readerSupplier
) {
    try (var in = readerSupplier.get()) {
        int[] ln = {1};
        return in.lines()
            .flatMap(line -> {
                try {
                    return error(source, line, ln);
                } finally {
                    ln[0]++;
                }
            })
            .findFirst();
    } catch (Exception e) {
        throw new IllegalStateException("Failed to read from " + source, e);
    }
}

private Stream<String> error(Object source, String line, int[] ln) {
    return evaluate(line)
        ? Stream.empty()
        : Stream.of(source + ":" + ln[0] + " >> " + line);
}

@SuppressWarnings("MethodMayBeStatic")
private boolean evaluate(String line) {
    return switch (FJORTH.interpret(line)) {
        case Fjorth.Result.OK _ -> {
            CONSOLE.println(" ok");
            yield true;
        }
        case Fjorth.Result.Failed(var message) -> {
            CONSOLE.println();
            CONSOLE.println(message);
            yield false;
        }
    };
}

private static final Fjorth FJORTH = Fjorth.getDefault();

private static final Console CONSOLE = FJORTH.console();

private static BufferedReader stdin() {
    return new BufferedReader(new InputStreamReader(System.in));
}

private static BufferedReader reader(Path file) {
    try {
        return Files.newBufferedReader(file);
    } catch (Exception e) {
        throw new IllegalStateException("Could not read from " + file, e);
    }
}
