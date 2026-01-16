package vgu.cloud26;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public class LambdaCreateToken implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // Secret key - store in environment variable for security
    private static final String SECRET_KEY_ENV_VAR = "HMAC_SECRET_KEY";
    private static final String DEFAULT_SECRET_KEY = "mysecretcloud26";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LambdaLogger logger = context.getLogger();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            // Log request
            logger.log("Received token creation request");

            // Get secret key from environment variable
            String secretKey = System.getenv(SECRET_KEY_ENV_VAR);
            if (secretKey == null || secretKey.isEmpty()) {
                secretKey = DEFAULT_SECRET_KEY;
                logger.log("Using default secret key");
            }

            // Parse request - simple version
            String email = null;
            String customKey = null;

            // Try JSON body first
            if (request.getBody() != null && !request.getBody().isEmpty()) {
                try {
                    JSONObject json = new JSONObject(request.getBody());
                    email = json.optString("email", null);
                    customKey = json.optString("key", null);

                    // Backward compatibility
                    if (email == null && json.has("data")) {
                        email = json.getString("data");
                    }
                } catch (JSONException e) {
                    logger.log("JSON parse error: " + e.getMessage());
                }
            }

            // Try query parameters
            if (email == null && request.getQueryStringParameters() != null) {
                Map<String, String> params = request.getQueryStringParameters();
                email = params.get("email");
                customKey = params.get("key");

                if (email == null && params.containsKey("data")) {
                    email = params.get("data");
                }
            }

            // Validate email
            if (email == null || email.isEmpty()) {
                return createErrorResponse(400, "Email is required", response);
            }

            // Simple email validation
            if (!email.contains("@")) {
                return createErrorResponse(400, "Invalid email format", response);
            }

            // Use custom key if provided
            if (customKey != null && !customKey.isEmpty()) {
                secretKey = customKey;
                logger.log("Using custom key");
            }

            // Generate token
            String token = generateSecureToken(email, secretKey, logger);

            if (token == null) {
                return createErrorResponse(500, "Failed to generate token", response);
            }

            // Simple success response
            JSONObject responseBody = new JSONObject();
            responseBody.put("status", "success");
            responseBody.put("token", token);
            responseBody.put("algorithm", "HMAC-SHA256");
            responseBody.put("email", maskEmail(email));

            response.setStatusCode(200);
            response.setBody(responseBody.toString());
            response.setHeaders(Map.of(
                "Content-Type", "application/json"
            ));

            logger.log("Token created for email: " + maskEmail(email));

            return response;

        } catch (Exception e) {
            logger.log("Error: " + e.toString());
            return createErrorResponse(500, "Internal error", response);
        }
    }

    // Generate HMAC-SHA256 token - simple version
    private String generateSecureToken(String email, String key, LambdaLogger logger) {
        try {
            // Validate
            if (email == null || email.isEmpty() || key == null || key.isEmpty()) {
                logger.log("Invalid input");
                return null;
            }

            // Create HMAC
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );

            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(email.getBytes(StandardCharsets.UTF_8));

            // Return Base64 encoded token
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.log("Crypto error: " + e.getMessage());
            return null;
        }
    }

    // Mask email for logging
    private String maskEmail(String email) {
        if (email == null || email.length() < 5) {
            return "***";
        }

        int atIndex = email.indexOf('@');
        if (atIndex > 2) {
            return email.charAt(0) + "***" + email.substring(atIndex - 1);
        }

        return "***" + email.substring(Math.max(0, email.length() - 3));
    }

    // Simple error response
    private APIGatewayProxyResponseEvent createErrorResponse(
            int statusCode,
            String message,
            APIGatewayProxyResponseEvent response) {

        JSONObject errorBody = new JSONObject();
        errorBody.put("status", "error");
        errorBody.put("message", message);

        response.setStatusCode(statusCode);
        response.setBody(errorBody.toString());
        response.setHeaders(Map.of("Content-Type", "application/json"));

        return response;
    }
}