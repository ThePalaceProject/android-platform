import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipCheck {

    private static final Logger LOG = Logger.getLogger("ZipCheck");

    record Failure(String name, String hashExpected, String hashReceived) {

    }

    public static void main(String[] args) throws Exception {
        var fileZip = Paths.get(args[0]);
        var fileSpec = Paths.get(args[1]);

        var properties = new Properties();
        try (var input = Files.newInputStream(fileSpec)) {
            properties.load(input);
        }

        var succeeded = new HashMap<String, String>();
        var failed = new HashMap<String, Failure>();

        try (var zipFile = new ZipFile(fileZip.toFile())) {
            var zipEntries = zipFile.entries();
            while (zipEntries.hasMoreElements()) {
                var entry = (ZipEntry) zipEntries.nextElement();
                var hash = properties.getProperty(entry.getName());
                if (hash != null) {
                    properties.remove(entry.getName());
                    checkHash(zipFile, entry, hash, failed, succeeded);
                }
            }
        }

        for (var entry : succeeded.entrySet()) {
            LOG.info("SUCCEEDED: File %s has expected hash %s"
                    .formatted(entry.getKey(), entry.getValue())
            );
        }

        var failedFlag = false;

        if (!properties.isEmpty()) {
            for (var name : properties.keySet()) {
                failedFlag = true;
                LOG.severe("FAILED: Missing a required file.");
                LOG.severe("  Archive: %s".formatted(fileZip));
                LOG.severe("  File:    %s".formatted(name));
            }
        }

        for (var failure : failed.values()) {
            failedFlag = true;
            LOG.severe("FAILED: File had the wrong hash value.");
            LOG.severe("  Archive:       %s".formatted(fileZip));
            LOG.severe("  File:          %s".formatted(failure.name));
            LOG.severe("  Hash Expected: %s".formatted(failure.hashExpected));
            LOG.severe("  Hash Received: %s".formatted(failure.hashReceived));
        }

        if (failedFlag) {
            System.exit(1);
        } else {
            System.exit(0);
        }
    }

    private static void checkHash(
            ZipFile zipFile,
            ZipEntry zipEntry,
            String hashExpected,
            HashMap<String, Failure> failed,
            HashMap<String, String> succeeded)
            throws Exception {

        var name = zipEntry.getName();
        var digest = MessageDigest.getInstance("SHA-256");

        try (var input = zipFile.getInputStream(zipEntry)) {
            try (var digestStream = new DigestInputStream(input, digest)) {
                digestStream.transferTo(OutputStream.nullOutputStream());
            }
        }

        var hashReceived = HexFormat.of().formatHex(digest.digest());
        if (!Objects.equals(hashExpected, hashReceived)) {
            failed.put(name, new Failure(name, hashExpected, hashReceived));
        } else {
            succeeded.put(name, hashReceived);
        }
    }
}
