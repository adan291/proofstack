import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Container HEALTHCHECK without curl: exit 0 if the URL answers HTTP 200, 1 otherwise. */
public final class HealthProbe {

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "http://127.0.0.1:8081/actuator/health";
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpResponse<Void> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            System.exit(response.statusCode() == 200 ? 0 : 1);
        } catch (Exception e) {
            System.exit(1);
        }
    }
}
