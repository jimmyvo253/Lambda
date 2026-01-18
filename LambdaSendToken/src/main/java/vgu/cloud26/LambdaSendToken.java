package vgu.cloud26;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

public class LambdaSendToken implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Parsing input
        JSONObject body = new JSONObject(request.getBody());
        String email = body.getString("email");
        String key = body.getString("key"); // Inputting the key dynamically

        //Input Email + Key -> Output Secure Token
        String token = generateSecureToken(email, key, context);

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(200);
        
        JSONObject responseJson = new JSONObject();
        responseJson.put("token", token);
        response.setBody(responseJson.toString());
        
        return response;
    }

    public static String generateSecureToken(String data, String key, Context context) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            context.getLogger().log("Error: " + e.getMessage());
            return null;
        }
    }
}