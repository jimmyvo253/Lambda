package vgu.cloud26;

import java.util.Base64;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public class LambdaGetListOfObjects implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String THUMB_BUCKET = "resized-bucket-vts253";
    private static final LambdaClient lambda = LambdaClient.builder().region(Region.AP_SOUTHEAST_1).build();
    private static final S3Client s3 = S3Client.builder().region(Region.AP_SOUTHEAST_1).build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Invoke LambdaGetPhotosDB
            String raw = lambda.invoke(InvokeRequest.builder()
                    .functionName("LambdaGetPhotosDB")
                    .payload(SdkBytes.fromUtf8String("{}"))
                    .build())
                    .payload().asUtf8String();

            // Unwrap response if it's { statusCode, body, isBase64Encoded, ... }
            JSONArray dbItems = extractJSONArrayFromLambdaResponse(raw);

            // Attach thumbnails
            JSONArray result = new JSONArray();
            for (int i = 0; i < dbItems.length(); i++) {
                JSONObject item = dbItems.getJSONObject(i);

                // ✅ FIX 1: correct field name
                String key = item.getString("S3Key");

                // Pull resized thumb from S3: resized-<key>
                try {
                    byte[] bytes = s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(THUMB_BUCKET)
                            .key("resized-" + key)
                            .build()).asByteArray();

                    // ✅ Match your HTML: obj.thumbnailData
                    item.put("thumbnailData", Base64.getEncoder().encodeToString(bytes));
                    item.put("extension", "png"); // or "jpeg" if you store JPG thumbs
                } catch (Exception e) {
                    item.put("thumbnailData", "");
                    item.put("extension", "png");
                }

                // Optional: also provide "key" for old UI
                item.put("key", key);

                result.put(item);
            }

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json"
                    ))
                    .withBody(result.toString());

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.toString());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json"
                    ))
                    .withBody(new JSONObject().put("error", e.getMessage()).toString());
        }
    }

    private JSONArray extractJSONArrayFromLambdaResponse(String raw) {
        String trimmed = raw.trim();

        // If it's already an array
        if (trimmed.startsWith("[")) {
            return new JSONArray(trimmed);
        }

        // Otherwise it's a wrapper object
        JSONObject obj = new JSONObject(trimmed);
        String body = obj.optString("body", "[]");

        boolean isB64 = obj.optBoolean("isBase64Encoded", false);
        if (isB64) {
            body = new String(Base64.getDecoder().decode(body));
        }

        return new JSONArray(body);
    }
}
