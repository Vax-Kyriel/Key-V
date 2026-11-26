package keyv;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class Scripts {
    private Scripts() {
    }

    static String load(String name) throws IOException {
        Path dir = directory();
        String prelude = stripBom(Files.readString(dir.resolve("prelude.ps1"), StandardCharsets.UTF_8));
        if ("prelude.ps1".equals(name)) {
            return prelude;
        }
        String body = stripBom(Files.readString(dir.resolve(name), StandardCharsets.UTF_8));
        return prelude + System.lineSeparator() + body;
    }

    private static Path directory() throws IOException {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("scripts"));
        try {
            var location = KeyVApp.class.getProtectionDomain().getCodeSource().getLocation();
            Path code = Path.of(location.toURI());
            Path parent = Files.isDirectory(code) ? code : code.getParent();
            if (parent != null) {
                candidates.add(parent.resolve("scripts"));
                if (parent.getParent() != null) {
                    candidates.add(parent.getParent().resolve("scripts"));
                }
            }
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            // Le dossier scripts du répertoire courant reste le repli.
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("prelude.ps1"))) {
                return candidate.toAbsolutePath();
            }
        }
        throw new IOException("Dossier scripts introuvable à côté de Key-V.");
    }

    private static String stripBom(String text) {
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }
}
