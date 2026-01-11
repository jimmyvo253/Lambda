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

public class LambdaDeleteBothObjects implements
        RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        String originalBucket = "bucket-vts253";
        String resizedBucket  = "resized-bucket-vts253";
        String requestBody = event.getBody();

        JSONObject bodyJSON = new JSONObject(requestBody);
        String objName = bodyJSON.getString("key");

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_SOUTHEAST_1)
                .build();


        //Delete original object
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(originalBucket)
                .key(objName)
                .build());

        //Delete resized object
        String resizedKey = "resized-" + objName;
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(resizedBucket)
                .key(resizedKey)
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
