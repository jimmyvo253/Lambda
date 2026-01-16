package vgu.cloud26;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public class LambdaVerifyToken implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String SECRET_KEY_ENV_VAR = "HMAC_SECRET_KEY";
    private static final String DEFAULT_SECRET_KEY = "mysecretclous26";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LambdaLogger logger = context.getLogger();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            logger.log("Token verification request received");

            // Get secret key from environment
            String secretKey = System.getenv(SECRET_KEY_ENV_VAR);
            if (secretKey == null || secretKey.isEmpty()) {
                secretKey = DEFAULT_SECRET_KEY;
                logger.log("WARNING: Using default secret key");
            }

            // Parse request
            JSONObject requestBody;
            try {
                String body = request.getBody();
                if (body == null || body.isEmpty()) {
                    return handleQueryParameters(request, secretKey, logger, response);
                }
                requestBody = new JSONObject(body);
            } catch (Exception e) {
                logger.log("Invalid JSON in request: " + e.getMessage());
                return createErrorResponse(400, "Invalid JSON format", response);
            }

            // Validate required fields
            if (!requestBody.has("token") || !requestBody.has("data")) {
                return createErrorResponse(400, "Missing required fields: 'token' and 'data'", response);
            }

            String token = requestBody.getString("token");
            String data = requestBody.getString("data");

            // Optional: custom key
            if (requestBody.has("key")) {
                secretKey = requestBody.getString("key");
                logger.log("Using custom key from request");
            }

            // Verify token
            boolean isValid = verifyToken(data, token, secretKey, logger);

            // Check expiry if timestamp is included in data
            boolean isExpired = false;
            String expiryInfo = null;

            if (isValid && data.contains("|")) {
                String[] parts = data.split("\\|");
                if (parts.length > 1) {
                    try {
                        long expiryTimestamp = Long.parseLong(parts[parts.length - 1]);
                        Instant expiryTime = Instant.ofEpochSecond(expiryTimestamp);
                        Instant now = Instant.now();

                        if (now.isAfter(expiryTime)) {
                            isExpired = true;
                            isValid = false;
                            expiryInfo = "Token expired at: " + expiryTime.toString();
                        } else {
                            long minutesRemaining = ChronoUnit.MINUTES.between(now, expiryTime);
                            expiryInfo = "Token expires in: " + minutesRemaining + " minutes";
                        }
                    } catch (NumberFormatException e) {
                        logger.log("No valid timestamp in data");
                    }
                }
            }

            // Create response
            JSONObject responseBody = new JSONObject();
            responseBody.put("status", isValid ? "valid" : "invalid");
            responseBody.put("token_valid", isValid);
            responseBody.put("algorithm", "HMAC-SHA256");
            responseBody.put("verification_timestamp", Instant.now().toString());

            if (expiryInfo != null) {
                responseBody.put("expiry_info", expiryInfo);
            }

            if (isExpired) {
                responseBody.put("expired", true);
            }

            response.setStatusCode(200);
            response.setBody(responseBody.toString());
            response.setHeaders(Map.of(
                "Content-Type", "application/json",
                "X-Token-Valid", String.valueOf(isValid)
            ));

            logger.log("Token verification completed: " + (isValid ? "VALID" : "INVALID"));

            return response;

        } catch (Exception e) {
            logger.log("Unexpected error in VerifyTokenLambda: " + e.toString());
            return createErrorResponse(500, "Internal server error: " + e.getMessage(), response);
        }
    }

    // Handle GET requests with query parameters
    private APIGatewayProxyResponseEvent handleQueryParameters(
            APIGatewayProxyRequestEvent request,
            String secretKey,
            LambdaLogger logger,
            APIGatewayProxyResponseEvent response) {

        Map<String, String> queryParams = request.getQueryStringParameters();

        if (queryParams == null || !queryParams.containsKey("token") || !queryParams.containsKey("data")) {
            return createErrorResponse(400,
                "Missing required parameters: 'token' and 'data'. " +
                "Send JSON with {'token':'your-token','data':'original-data'} or use query parameters",
                response);
        }

        String token = queryParams.get("token");
        String data = queryParams.get("data");

        if (queryParams.containsKey("key")) {
            secretKey = queryParams.get("key");
        }

        boolean isValid = verifyToken(data, token, secretKey, logger);

        JSONObject responseBody = new JSONObject();
        responseBody.put("status", isValid ? "valid" : "invalid");
        responseBody.put("token_valid", isValid);
        responseBody.put("algorithm", "HMAC-SHA256");
        responseBody.put("timestamp", System.currentTimeMillis());

        response.setStatusCode(200);
        response.setBody(responseBody.toString());
        response.setHeaders(Map.of("Content-Type", "application/json"));

        return response;
    }

    // Verify HMAC-SHA256 token (simple version)
    public static boolean verifyToken(String data, String token, String key, LambdaLogger logger) {
        try {
            if (data == null || data.isEmpty() || token == null || token.isEmpty() || key == null || key.isEmpty()) {
                logger.log("Invalid input parameters for verification");
                return false;
            }

            // Generate expected token
            String expectedToken = generateSecureToken(data, key, logger);

            if (expectedToken == null) {
                logger.log("Failed to generate expected token");
                return false;
            }

            // Compare tokens (constant-time comparison to prevent timing attacks)
            boolean isValid = constantTimeEquals(token, expectedToken);

            logger.log("Token verification: " + (isValid ? "MATCH" : "MISMATCH"));

            return isValid;

        } catch (Exception e) {
            logger.log("Token verification error: " + e.getMessage());
            return false;
        }
    }

    // Constant-time string comparison to prevent timing attacks
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    // Token generation (same as in TokenGeneratorLambda)
    public static String generateSecureToken(String data, String key, LambdaLogger logger) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.log("Error generating token: " + e.getMessage());
            return null;
        }
    }

    // Create error response
    private APIGatewayProxyResponseEvent createErrorResponse(
            int statusCode,
            String message,
            APIGatewayProxyResponseEvent response) {

        JSONObject errorBody = new JSONObject();
        errorBody.put("status", "error");
        errorBody.put("message", message);
        errorBody.put("timestamp", System.currentTimeMillis());

        response.setStatusCode(statusCode);
        response.setBody(errorBody.toString());
        response.setHeaders(Map.of("Content-Type", "application/json"));

        return response;
    }
}
