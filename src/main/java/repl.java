import com.github.kjetilv.fjorth.Fjorth;
import com.github.kjetilv.fjorth.Out;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

void main(String[] args) {
    out.println("fjorth");
    Arrays.stream(args)
        .peek(arg -> out.println("Evaluating " + arg))
        .map(Path::of)
        .forEach(file ->
            evaluateLines(file, () -> reader(file))
                .ifPresent(failingLine -> {
                    throw new IllegalStateException("Failed to evaluate " + file + ", failing line: " + failingLine);
                }));
    try (var in = stdin()) {
        in.lines()
            .forEach(this::evaluate);
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}

private Optional<String> evaluateLines(Object source, Supplier<BufferedReader> bufferedReader) {
    try (
        var in = bufferedReader.get()
    ) {
        return in.lines()
            .takeWhile(this::evaluate)
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
