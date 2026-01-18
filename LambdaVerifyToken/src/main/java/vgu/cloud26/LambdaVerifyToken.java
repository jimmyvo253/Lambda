package vgu.cloud26;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaVerifyToken implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        LambdaLogger logger = context.getLogger();
        String email = "";
        String ProvidedToken = "";

        try {
            JSONObject event;
            if (input instanceof Map) {
                event = new JSONObject((Map<?, ?>) input);
            } else {
                event = new JSONObject(input.toString());
            }

            if (event.has("body")) {
                Object bodyObj = event.get("body");
                JSONObject innerData;
                if (bodyObj instanceof String) {
                    innerData = new JSONObject((String) bodyObj);
                } else {
                    innerData = new JSONObject((Map<?, ?>) bodyObj);
                }
                email = innerData.optString("email");
                ProvidedToken = innerData.optString("token");
            } else {
                email = event.optString("email");
                ProvidedToken = event.optString("token");
            }

            if (email.isEmpty() || ProvidedToken.isEmpty()) {
                logger.log("Missing email or token in input");
                return "false";
            }

        } catch (Exception e) {
            logger.log("Input error: " + e.getMessage() + " | Input: " + input.toString());
            return "false";
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

            String sessionToken = System.getenv("AWS_SESSION_TOKEN");
            HttpRequest requestParameter = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:2773/systemsmanager/parameters/get/?name=cloud26key&withDecryption=true"))
                        .header("Accept", "application/json")
                        .header("X-Aws-Parameters-Secrets-Token", sessionToken)
                        .GET()
                        .build();

            HttpResponse<String> responseParameter = client.send(requestParameter, HttpResponse.BodyHandlers.ofString());
            
            String jsonResponse = responseParameter.body();
            JSONObject jsonBody = new JSONObject(jsonResponse);
            JSONObject parameter = (JSONObject) jsonBody.get("Parameter");
            String key = (String) parameter.get("Value");
            
            logger.log("My secret key retrieved successfully");

            // 2. Re-calculate the token
            String expectedToken = generateSecureToken(email, key, logger);

            // 3. Comparison
            boolean isValid = expectedToken != null && expectedToken.equals(ProvidedToken);

            logger.log("Verification result: " + isValid);
            
            // Return simple "true" or "false" string to match your Orchestrator switch-case
            return String.valueOf(isValid);

        } catch (IOException | InterruptedException | JSONException e) {
            logger.log("Verify error: " + e.getMessage());
            return "false";
        }
    }

    public static String generateSecureToken(String data, String key, LambdaLogger logger) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            logger.log("HMAC Error: " + e.getMessage());
            return null;
        }
    }
}