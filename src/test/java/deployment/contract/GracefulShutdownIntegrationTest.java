package deployment.contract;

import com.carwash.CarwashBookingQueueSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An isolated real HTTP server verifies Spring's configured SIGTERM-equivalent drain path. */
class GracefulShutdownIntegrationTest {
    @Test void readinessIsRemovedWhileAnInflightRequestCompletesBeforeShutdown() throws Exception {
        var context = new SpringApplicationBuilder(CarwashBookingQueueSystemApplication.class, ControlledEndpoint.class)
                .web(WebApplicationType.SERVLET).profiles("test").properties("server.port=0").run();
        var endpoint = context.getBean(HoldRequest.class);
        try {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/hold"))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            var response = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());
            assertTrue(endpoint.entered.await(10, TimeUnit.SECONDS));
            var stopped = CompletableFuture.runAsync(context::close);
            assertTrue(endpoint.refusing.await(10, TimeUnit.SECONDS));
            assertFalse(stopped.isDone(), "Active HTTP request must drain before context shutdown completes");
            endpoint.release.countDown();
            assertEquals(200, response.get(15, TimeUnit.SECONDS).statusCode());
            assertEquals("done", response.get(15, TimeUnit.SECONDS).body());
            stopped.get(15, TimeUnit.SECONDS);
        } finally {
            endpoint.release.countDown();
            context.close();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ControlledEndpoint {
        @Bean HoldRequest holdRequest() { return new HoldRequest(); }
        @Bean @Order(0) SecurityFilterChain holdSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/hold").csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
        @Bean ApplicationListener<AvailabilityChangeEvent<ReadinessState>> readinessListener(HoldRequest hold) {
            return event -> {
                if (event.getState() == ReadinessState.REFUSING_TRAFFIC) hold.refusing.countDown();
            };
        }
    }

    @RestController
    static class HoldRequest {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch refusing = new CountDownLatch(1);

        @GetMapping("/hold") String hold() throws InterruptedException {
            entered.countDown();
            if (!release.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("Test request was not released");
            return "done";
        }
    }
}
