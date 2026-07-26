package com.github.kjetilv.fjorth;

import module java.base;

public interface Loader {

    default Interpreter.Result load(String resource) {
        var classLoader = Thread.currentThread().getContextClassLoader();
        var resourceAsStream = classLoader.getResourceAsStream(resource);
        if (resourceAsStream == null) {
            throw new IllegalArgumentException("Np such resource: " + resource);
        }
        try (resourceAsStream) {
            return load(resourceAsStream);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + resource, e);
        }
    }

    default Interpreter.Result load(Path path) {
        try (var inputStream = Files.newInputStream(path)) {
            return load(inputStream);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    default Interpreter.Result load(InputStream inputStream) {
        try (
            var reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            var bufferedReader = new BufferedReader(reader)
        ) {
            return load(bufferedReader);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + inputStream, e);
        }
    }

    Interpreter.Result load(Reader reader);
}
