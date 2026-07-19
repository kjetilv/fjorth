import com.github.kjetilv.fjorth.Fjorth;
import com.github.kjetilv.fjorth.Out;

import java.io.BufferedReader;
import java.io.InputStreamReader;

void main() {
    out.println("fjorth");
    try (var in = new BufferedReader(new InputStreamReader(System.in))) {
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            try (var result = fjorth.eval(line)) {
                switch (result) {
                    case Fjorth.Result.OK _ -> out.println(" ok");
                    case Fjorth.Result.Failed failed -> {
                        out.println();
                        out.println(failed.message());
                    }
                }
            }
        }
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}

private static final Fjorth fjorth = Fjorth.getDefault();

private static final Out out = fjorth.out();
