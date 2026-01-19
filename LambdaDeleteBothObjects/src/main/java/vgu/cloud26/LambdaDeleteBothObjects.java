package vgu.cloud26;

import java.util.Base64;
import java.util.Collections;
import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

public class LambdaDeleteBothObjects
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent event, Context context) {

        try {
            JSONObject body = new JSONObject(event.getBody());
            String key = body.getString("key");

            S3Client s3 = S3Client.builder()
                    .region(Region.AP_SOUTHEAST_1)
                    .build();

            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket("bucket-vts253")
                    .key(key)
                    .build());

            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket("resized-bucket-vts253")
                    .key("resized-" + key)
                    .build());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withIsBase64Encoded(false)
                    .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                    .withBody("{\"status\":\"ok\"}");

        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody(e.getMessage());
        }
    }
}
