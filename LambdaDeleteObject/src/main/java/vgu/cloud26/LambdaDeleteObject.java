package vgu.cloud26;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.json.JSONObject;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.util.Base64;
import java.util.Collections;

public class LambdaDeleteObject implements
        RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String bucketName = "bucket-vts253";
        String requestBody = event.getBody();

        JSONObject bodyJSON = new JSONObject(requestBody);
        String objName = bodyJSON.getString("key");

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_SOUTHEAST_1)
                .build();

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objName)
                .build());

        String message = "Object deleted successfully";
        String encoded = Base64.getEncoder().encodeToString(message.getBytes());

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(200);
        response.setBody(encoded);
        response.withIsBase64Encoded(true);
        response.setHeaders(Collections.singletonMap("Content-Type", "text/plain"));
        return response;
    }
}
