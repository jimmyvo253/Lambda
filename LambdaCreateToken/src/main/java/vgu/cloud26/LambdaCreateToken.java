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

public class LambdaCreateToken implements RequestHandler<Object, String> {

    @Override
    public String handleRequest(Object input, Context context) {
        LambdaLogger logger = context.getLogger();
        String email = "";

        try {
            JSONObject event;
            if (input instanceof Map) {
                event = new JSONObject((Map<?, ?>) input);
            } else {
                // Otherwise, try parsing the raw string
                event = new JSONObject(input.toString());
            }

            // CHECK FOR WRAPPING: 
            if (event.has("body")) {
                // Get the content inside "body"
                Object bodyContent = event.get("body");
                
                if (bodyContent instanceof String) {
                    JSONObject innerData = new JSONObject((String) bodyContent);
                    email = innerData.getString("email");
                } else if (bodyContent instanceof Map) {
                    // If it's a nested Map, convert to JSON
                    JSONObject innerData = new JSONObject((Map<?, ?>) bodyContent);
                    email = innerData.getString("email");
                }
            } else {
                // Direct call testing
                email = event.getString("email");
            }
            
        } catch (Exception e) {
            logger.log("JSON Parse Error: " + e.getMessage() + " | Raw Input: " + input.toString());
            return "Error: Invalid Input Format";
        }

        // 2. Professor's HttpClient snippet to get the key "cloud26key"
        try {
            HttpClient client = HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

            HttpRequest requestParameter = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:2773/systemsmanager/parameters/get/?name=cloud26key&withDecryption=true"))
                        .header("Accept", "application/json")
                        .header("X-Aws-Parameters-Secrets-Token", System.getenv("AWS_SESSION_TOKEN"))
                        .GET()
                        .build();

            HttpResponse<String> responseParameter = client.send(requestParameter, HttpResponse.BodyHandlers.ofString());
            
            JSONObject jsonResponse = new JSONObject(responseParameter.body());
            String secretKey = jsonResponse.getJSONObject("Parameter").getString("Value");

            // 3. HMAC Token Generation
            return generateSecureToken(email, secretKey, logger);

        } catch (IOException | InterruptedException | JSONException e) {
            logger.log("Execution Error: " + e.getMessage());
            return "Error: HMAC generation failed";
        }
    }

    // --- Professor's HMAC Method ---
    public static String generateSecureToken(String data, String key, LambdaLogger logger) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String base64Token = Base64.getEncoder().encodeToString(hmacBytes);
            
            logger.log("Input Email: " + data);
            logger.log("Secure Token: " + base64Token);
            return base64Token;
            
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            logger.log("HMAC Error: " + e.getMessage());
            return null;
        }
    }
}