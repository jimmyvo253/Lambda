package vgu.cloud26;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

public class LambdaDeleteOrchestration implements RequestHandler<Map<String, Object>, String> {

    private final LambdaClient lambdaClient = LambdaClient.builder()
            .region(Region.AP_SOUTHEAST_1)
            .build();

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        JSONObject payload = new JSONObject(input);
        
        // Wrap the payload so the worker's event.getBody() can read it
        String wrappedPayload = new JSONObject().put("body", payload.toString()).toString();

        // Run DB deletion and S3 deletion in parallel
        CompletableFuture<String> dbTask = invokeAsync("LambdaDeletePhotoDB", payload.toString());
        CompletableFuture<String> s3Task = invokeAsync("LambdaDeleteBothObjects", wrappedPayload);

        CompletableFuture.allOf(dbTask, s3Task).join();

        return "{\"status\":\"success\", \"message\":\"S3 Objects and DB Record deleted\"}";
    }

    private CompletableFuture<String> invokeAsync(String functionName, String payload) {
        return CompletableFuture.supplyAsync(() -> {
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(functionName)
                    .payload(SdkBytes.fromUtf8String(payload))
                    .build();
            return lambdaClient.invoke(request).payload().asUtf8String();
        });
    }
}