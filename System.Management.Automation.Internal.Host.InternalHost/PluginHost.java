import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class PluginHost {
    private static void inspect(String label, URL url) throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{url}, PluginHost.class.getClassLoader())) {
            Class<?> type = loader.loadClass("sample.Greeting");
            System.out.println(label + " owns class: "
                    + (type.getClassLoader() == loader));
            System.out.println(label + " message: "
                    + type.getMethod("hello").invoke(null));
            try (InputStream in = loader.getResourceAsStream("sample/message.txt")) {
                if (in == null) throw new IllegalStateException("missing resource");
                System.out.println(label + " resource: "
                        + new String(in.readAllBytes(), StandardCharsets.UTF_8).trim());
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("dir jar");
        URL dir = Path.of(args[0]).toAbsolutePath().normalize().toUri().toURL();
        URL jar = Path.of(args[1]).toAbsolutePath().normalize().toUri().toURL();
        System.out.println("directory URL ends in /: " + dir.toExternalForm().endsWith("/"));
        System.out.println("JAR URL ends in .jar: " + jar.toExternalForm().endsWith(".jar"));
        inspect("directory", dir);
        inspect("JAR", jar);
    }
}