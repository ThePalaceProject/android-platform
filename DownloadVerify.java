import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.logging.Logger;

public final class DownloadVerify
{
  private static final Logger LOG =
    Logger.getLogger("DownloadVerify");

  private DownloadVerify()
  {

  }

  public static void main(
    final String[] args)
    throws Exception
  {
    final var sourceURI =
      URI.create(args[0]);
    final var outputFile =
      Paths.get(args[1]).toAbsolutePath();
    final var hashExpected =
      args[2];

    LOG.info("Download:        %s".formatted(sourceURI));
    LOG.info("File:            %s".formatted(outputFile));
    LOG.info("Expected SHA256: %s".formatted(hashExpected));

    if (Files.isRegularFile(outputFile)) {
      LOG.info("Output file already exists.");
      return;
    }

    final var client =
      HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    final var request =
      HttpRequest.newBuilder(sourceURI)
        .GET()
        .build();

    final var response =
      client.send(
        request,
        HttpResponse.BodyHandlers.ofFile(
          outputFile,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        )
      );

    final var statusCode = response.statusCode();
    if (statusCode >= 400) {
      LOG.severe("Status Code: %s".formatted(statusCode));
      throw new IOException(
        "Server returned status code %s".formatted(statusCode)
      );
    }

    final var digest = MessageDigest.getInstance("SHA-256");
    try (var stream = Files.newInputStream(outputFile)) {
      try (var digestStream = new DigestInputStream(stream, digest)) {
        digestStream.transferTo(OutputStream.nullOutputStream());
      }
    }

    final var hashReceived = HexFormat.of().formatHex(digest.digest());
    if (!Objects.equals(hashExpected, hashReceived)) {
      LOG.severe("Expected SHA256: %s".formatted(hashExpected));
      LOG.severe("Received SHA256: %s".formatted(hashReceived));
      throw new IOException("Download file failed hash check.");
    }
  }
}
