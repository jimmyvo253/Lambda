package vgu.cloud26;

import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class LambdaInvokeTokenFunction implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final LambdaClient client = LambdaClient.builder()
        .region(Region.AP_SOUTHEAST_1)
        .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context ctx) {
        String requestBody = event.getBody();
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return errorResponse("Request body is missing.");
        }

        JSONObject result = new JSONObject();
        
        try {
            JSONObject frontendPayload = new JSONObject(requestBody);
            String action = frontendPayload.optString("action", "");

            // 1. Build the worker data
            JSONObject workerData = new JSONObject();
            workerData.put("email", frontendPayload.getString("email"));
            if (frontendPayload.has("token")) {
                workerData.put("token", frontendPayload.getString("token"));
            }

            // 2. Wrap under "body" as a string
            JSONObject payloadToInvoke = new JSONObject();
            payloadToInvoke.put("body", workerData.toString());

            String workerResp;
            
            // FIX: Using traditional switch statement to avoid "unexpected statement" errors
            switch (action) {
                case "generate" -> {
                    workerResp = invoke("LambdaCreateToken", payloadToInvoke);
                    result.put("auth_status", workerResp);
                }
                case "verify" -> {
                    workerResp = invoke("LambdaVerifyToken", payloadToInvoke);
                    result.put("auth_status", workerResp);
                }
                default -> {
                    return errorResponse("Invalid action: " + action);
                }
            }
            
            result.put("status", "SUCCESS");

        } catch (JSONException e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(400)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(result.toString());
        }

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(result.toString());
    }

    private String invoke(String function, JSONObject json) {
        // STRICT JSON FIX: Convert to String first to avoid the "body={email=...}" error
        String jsonString = json.toString();

        InvokeResponse resp = client.invoke(
                InvokeRequest.builder()
                        .functionName(function)
                        .payload(SdkBytes.fromUtf8String(jsonString))
                        .build()
        );

        String output = resp.payload().asUtf8String();
        
        // Clean up quotes from plain string responses
        if (output.startsWith("\"") && output.endsWith("\"")) {
            output = output.substring(1, output.length() - 1);
        }
        return output;
    }

    private APIGatewayProxyResponseEvent errorResponse(String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(400)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody("{\"error\":\"" + msg + "\"}");
    }

}