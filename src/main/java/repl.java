import com.github.kjetilv.fjorth.Fjorth;
import com.github.kjetilv.fjorth.Out;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

void main(String[] args) {
    Arrays.stream(args)
        .peek(arg -> out.println("*** evaluating file " + arg))
        .map(Path::of)
        .forEach(file ->
            evaluateLines(file, () -> reader(file))
                .ifPresent(failingLine -> {
                    throw new IllegalStateException("Failed to evaluate " + file + ", failing line: " + failingLine);
                }));
    out.println("fjorth");
    try (var in = stdin()) {
        in.lines()
            .forEach(this::evaluate);
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}

/// @param source         Source of lines
/// @param readerSupplier Supplier of reader
/// @return Error messgae for first line that failed, empty if all succeeded
private Optional<String> evaluateLines(Object source, Supplier<BufferedReader> readerSupplier) {
    try (
        var in = readerSupplier.get()
    ) {
        int[] ln = {1};
        return in.lines()
            .flatMap(line -> {
                try {
                    return evaluate(line)
                        ? Stream.empty()
                        : Stream.of(source + ":" + ln[0] + " >> " + line);
                } finally {
                    ln[0]++;
                }
            })
            .findFirst();
    } catch (Exception e) {
        throw new IllegalStateException("Failed to read from " + source, e);
    }
}

@SuppressWarnings("MethodMayBeStatic")
private boolean evaluate(String line) {
    try (var result = fjorth.eval(line)) {
        switch (result) {
            case Fjorth.Result.OK _ -> out.println(" ok");
            case Fjorth.Result.Failed failed -> {
                out.println();
                out.println(failed.message());
                return false;
            }
        }
    }
    return true;
}

private static final Fjorth fjorth = Fjorth.getDefault();

private static final Out out = fjorth.out();

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
