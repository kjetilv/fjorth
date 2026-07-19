import com.github.kjetilv.fjorth.Fjorth;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@SuppressWarnings("MethodMayBeStatic")
void main() {
    var fjorth = Fjorth.getDefault();
    var out = fjorth.out();
    out.println("fjorth");
    try (var in = new BufferedReader(new InputStreamReader(System.in))) {
        for (String line = in.readLine(); line != null; line = in.readLine()) {
            switch (fjorth.eval(line)) {
                case Fjorth.Result.OK _ -> out.println(" ok");
                case Fjorth.Result.Failed failed -> {
                    out.print("\n" + failed.message());
                    fjorth.reset();
                }
            }
        }
    } catch (Exception e) {
        throw new IllegalStateException("Run failed", e);
    }
}
