package vgu.cloud26;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.json.JSONObject;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LambdaUploadOrchestration implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final LambdaClient lambda = LambdaClient.create();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Parse incoming request
        JSONObject body = new JSONObject(request.getBody());
        
        // 1. Prepare keys to match Resizer logic
        String srcKey = body.getString("key");
        String dstKey = "resized-" + srcKey;
        String userEmail = body.getString("email");

        // 2. Prepare Database Payload (ID, Description, Email, S3Key, ResizedKey)
        JSONObject dbPayload = new JSONObject()
                .put("description", body.getString("description"))
                .put("email", userEmail)
                .put("s3Key", srcKey)
                .put("resizedKey", dstKey);

        // 3. Parallel Execution
        CompletableFuture<String> dbCall = invokeAsync("LambdaInsertPhotosDB", dbPayload);
        CompletableFuture<String> originalCall = invokeAsync("LambdaUploadObject", body);
        CompletableFuture<String> resizedCall = invokeAsync("LambdaResizer", body);

        // Wait for all to finish
        CompletableFuture.allOf(dbCall, originalCall, resizedCall).join();

        // 4. Build Response
        JSONObject result = new JSONObject()
                .put("db", dbCall.join())
                .put("uploadOriginal", originalCall.join())
                .put("uploadResized", resizedCall.join());

        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody(result.toString())
                .withHeaders(Map.of(
                        "Content-Type", "application/json"
                ));
    }

    private CompletableFuture<String> invokeAsync(String functionName, JSONObject payload) {
        return CompletableFuture.supplyAsync(() -> {
            InvokeResponse resp = lambda.invoke(InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(payload.toString()))
                    .build());
            return resp.payload().asUtf8String();
        });
    }
}