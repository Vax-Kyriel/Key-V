package keyv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class SecureBootMemory {
    private final Path file;
    private final Properties properties = new Properties();

    SecureBootMemory() throws IOException {
        String appData = System.getenv("APPDATA");
        Path dir = Path.of(appData == null ? System.getProperty("user.home") : appData, "Key-V");
        Files.createDirectories(dir);
        file = dir.resolve("state.properties");
        if (Files.exists(file)) {
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
        }
    }

    void remember(String vm, String state, String template) throws IOException {
        properties.setProperty(key(vm, "state"), state == null ? "" : state);
        properties.setProperty(key(vm, "template"), template == null ? "" : template);
        store();
    }

    record Previous(String state, String template) {
    }

    Previous previous(String vm) {
        String state = properties.getProperty(key(vm, "state"));
        if (state == null) {
            return null;
        }
        return new Previous(state, properties.getProperty(key(vm, "template"), ""));
    }

    void clear(String vm) throws IOException {
        properties.remove(key(vm, "state"));
        properties.remove(key(vm, "template"));
        store();
    }

    private static String key(String vm, String field) {
        return field + "." + URLEncoder.encode(vm, StandardCharsets.UTF_8);
    }

    private void store() throws IOException {
        try (OutputStream output = Files.newOutputStream(file)) {
            properties.store(output, "Key-V");
        }
    }
}
