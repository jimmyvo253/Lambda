package vgu.cloud26;

import java.util.Base64;
import java.util.Map;

import org.json.JSONArray;
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

        String current = raw.trim();

        // Unwrap Lambda URL / API Gateway envelopes
        while (current.startsWith("{")) {

            JSONObject obj = new JSONObject(current);

            // Case 1: body is a string
            if (obj.has("body")) {
                Object bodyObj = obj.get("body");

                if (bodyObj instanceof String) {
                    String bodyStr = (String) bodyObj;

                    if (obj.optBoolean("isBase64Encoded", false)) {
                        bodyStr = new String(Base64.getDecoder().decode(bodyStr));
                    }

                    current = bodyStr.trim();
                    continue;
                }

                // Case 2: body is already JSON
                if (bodyObj instanceof JSONObject) {
                    JSONObject bodyJson = (JSONObject) bodyObj;

                    // 🔥 THIS IS THE IMPORTANT PART
                    if (bodyJson.has("items")) {
                        return bodyJson.getJSONArray("items");
                    }

                    // or body itself is the array wrapper
                    current = bodyJson.toString();
                    continue;
                }
            }

            // Case 3: DB lambda returns { items: [...] } directly
            if (obj.has("items")) {
                return obj.getJSONArray("items");
            }

            break;
        }

        // Final attempt: raw array
        if (current.startsWith("[")) {
            return new JSONArray(current);
        }

        throw new JSONException("Unable to extract JSONArray from response: " + raw);
    }


}
