package vgu.cloud26;

import java.util.Map;
import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

public class LambdaDeleteOrchestration implements RequestHandler<Map<String, Object>, String> {

    private final LambdaClient lambdaClient = LambdaClient.builder()
            .region(Region.AP_SOUTHEAST_1)
            .build();

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {
        try {
            // Extract the raw JSON string sent from the browser
            String bodyFromHtml = (String) input.get("body");

            // Wrap it so workers can use request.getBody()
            String wrappedPayload = new JSONObject()
                    .put("body", bodyFromHtml)
                    .toString();

            // 1. Invoke DB deletion FIRST
            String dbResult = invoke("LambdaDeletePhotoDB", wrappedPayload);
            JSONObject dbResponse = new JSONObject(dbResult);

            // 2. If DB deletion failed (e.g., 403 Not yours) -> STOP
            if (dbResponse.getInt("statusCode") != 200) {
                return dbResult;
            }

            // 3. Only if DB succeeds -> delete S3 objects
            invoke("LambdaDeleteBothObjects", wrappedPayload);

            return new JSONObject()
                .put("statusCode", 200)
                .put("body", new JSONObject().put("message", "Deleted everywhere").toString())
                .toString();

        } catch (Exception e) {
            return new JSONObject()
                .put("statusCode", 500)
                .put("body", new JSONObject().put("message", e.getMessage()).toString())
                .toString();
        }
    }

    private String invoke(String functionName, String payload) {
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .build();

        InvokeResponse response = lambdaClient.invoke(request);
        return response.payload().asUtf8String();
    }
}