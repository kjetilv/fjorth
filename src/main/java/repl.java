import com.github.kjetilv.fjorth.*;
import com.github.kjetilv.fjorth.Interpreter.Result.Failed;

///  Salty Fjorð!
@SuppressWarnings("MethodMayBeStatic")
void main(String[] args) {
    if (args.length != 0) {
        var result = evaluateArgumentFiles(args);
        if (result instanceof Failed(var message)) {
            CONSOLE.println("Terminating: " + message);
            return;
        }
    }
    evaluateStdin();
}

private static final Console CONSOLE = Consoles.stdout();

private static final Interpreter INTERPRETER = Machine.create().interpreter(CONSOLE);

private static final Loader LOADER = INTERPRETER.loader();

private static Interpreter.Result evaluateArgumentFiles(String[] args) {
    for (String arg : args) {
        var path = Path.of(arg);
        if (Files.isRegularFile(path)) {
            var result = LOADER.load(path);
            if (result instanceof Failed(var message)) {
                CONSOLE.println("Failed compilation of " + arg + "\n  " + message);
                return result;
            }
        } else {
            throw new IllegalArgumentException("Not a valid file: " + arg);
        }
    }
    return Interpreter.Result.OK;
}

private static void evaluateStdin() {
    CONSOLE.println("fjorth");
    try {
        try (
            var stdinReader = new BufferedReader(new InputStreamReader(System.in));
            var lines = stdinReader.lines()
        ) {
            lines.forEach(INTERPRETER::interpretInteractively);
        }
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}
