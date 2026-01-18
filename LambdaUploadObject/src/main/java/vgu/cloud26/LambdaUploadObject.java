package vgu.cloud26;

import java.util.Base64;
import java.util.Map;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;


public class LambdaUploadObject implements
        RequestHandler<Map<String, Object>, String> {

    @Override
    public String handleRequest(Map<String, Object> input, Context context) {

        String bucketName = "bucket-vts253";

        JSONObject bodyJSON = new JSONObject(input);
        String content = bodyJSON.getString("content");
        String objName = bodyJSON.getString("key");


        byte[] objBytes = Base64.getDecoder().decode(content.getBytes());

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objName)
                .build();

        S3Client s3Client = S3Client.builder()
                .region(Region.AP_SOUTHEAST_1)
                .build();
        s3Client.putObject(putObjectRequest,
                RequestBody.fromBytes(objBytes));


        String message = "Object uploaded successfully";

        String encodedString = Base64.getEncoder().encodeToString(message.getBytes());

        APIGatewayProxyResponseEvent response;
        response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(200);
        response.setBody(encodedString);
        response.withIsBase64Encoded(true);
        response.setHeaders(java.util.Collections.singletonMap("Content-Type", "text/plain"));

        return "Object uploaded successfully: " + objName;
    }

}