// package vgu.cloud26;

// import java.awt.Color;
// import java.awt.Graphics2D;
// import java.awt.RenderingHints;
// import java.awt.image.BufferedImage;
// import java.io.ByteArrayInputStream;
// import java.io.ByteArrayOutputStream;
// import java.sql.Connection;
// import java.sql.DriverManager;
// import java.sql.PreparedStatement;
// import java.util.Base64;
// import java.util.Map;
// import java.util.Properties;

// import javax.imageio.ImageIO;

// import org.json.JSONObject;

// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.LambdaLogger;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
// import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

// import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
// import software.amazon.awssdk.core.sync.RequestBody;
// import software.amazon.awssdk.regions.Region;
// import software.amazon.awssdk.services.rds.RdsUtilities;
// import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// public class LambdaOrchestration implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

//     // ----- RDS config -----
//     private static final String RDS_INSTANCE_HOSTNAME =
//             "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
//     private static final int RDS_INSTANCE_PORT = 3306;
//     private static final String DB_USER = "cloud26";
//     private static final String JDBC_URL =
//             "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

//     // ----- S3 config -----
//     private static final Region REGION = Region.AP_SOUTHEAST_1;
//     private static final String srcBucket = "bucket-vts253";
//     // private static final String RESIZED_BUCKET  = "resized-bucket-vts253";

//     // ----- Warm-up config -----
//     // private static final String WARMUP_EVENT_SOURCE = "aws.events";
//     // private static final String WARMUP_DETAIL_TYPE = "Scheduled Event";
//     // private static final String WARMUP_ACTION = "warm-up";

//     private final S3Client s3 = S3Client.builder()
//             .region(REGION)
//             .credentialsProvider(DefaultCredentialsProvider.create())
//             .build();

//     @Override
//     public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
//         LambdaLogger log = context.getLogger();

//         // Check if this is a warm-up invocation from EventBridge
//         // if (isWarmUpInvocation(request)) {
//         //     log.log("Warm-up invocation detected. Returning early to keep Lambda warm.");
//         //     return createWarmUpResponse();
//         // }

//         APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

//         String dbStatus = "not-run";
//         String originalStatus = "not-run";
//         String resizedStatus = "not-run";

//         try {
//             // 1. Parse request JSON
//             String body = request.getBody();
//             log.log("Request body: " + body);
//             JSONObject json = new JSONObject(body);

//             String key = json.getString("key");
//             String srcKey = record.getS3().getObject().getUrlDecodedKey();
//             String description = json.getString("description");
//             String contentBase64 = json.getString("content");

//             byte[] originalBytes = Base64.getDecoder().decode(contentBase64);

//             // 2. Upload original file to S3
//             log.log("Uploading original to S3: bucket=" + srcBucket + ", key=" + key);
//             putToS3(srcBucket, key, originalBytes);
//             originalStatus = "success";

//             // 3. Resize image and upload resized version
//             log.log("Resizing image...");
//             // byte[] resizedBytes = resizeImage(originalBytes); // 300x300 example
//             // String resizedKey = addSuffixToKey(key, "_small"); // e.g. uploads/photo123_small.jpg
//             String dstBucket = "resized-" + srcBucket;
//             String dstKey = "resized-" + srcKey;

            

//             log.log("Uploading resized to S3: bucket=" + RESIZED_BUCKET + ", key=" + resizedKey);
//             putToS3(dstBucket, dstKey, resizedBytes);
//             resizedStatus = "success";
//             // 4. Insert metadata into RDS
//             Class.forName("com.mysql.cj.jdbc.Driver");
//             try (Connection conn = DriverManager.getConnection(JDBC_URL, setMySqlConnectionProperties())) {
//                 String sql = "INSERT INTO Photos (Description, S3Key) VALUES (?, ?)";
//                 try (PreparedStatement st = conn.prepareStatement(sql)) {
//                     st.setString(1, description);
//                     st.setString(2, key);   // store original key, or resizedKey if required
//                     int rows = st.executeUpdate();
//                     log.log("Inserted rows: " + rows);
//                     dbStatus = "success";
//                 }
//             }

//             // 5. Build success response
//             JSONObject result = new JSONObject();
//             result.put("status", "ok");
//             result.put("db", dbStatus);
//             result.put("uploadOriginal", originalStatus);
//             result.put("uploadResized", resizedStatus);
//             result.put("key", key);
//             result.put("resizedKey", resizedKey);
//             result.put("description", description);

//             response.setStatusCode(200);
//             response.setBody(result.toString());
//             response.setHeaders(java.util.Map.of("Content-Type", "application/json"));
//             return response;

//         } catch (Exception e) {
//             log.log("ERROR in LambdaUploadPhotoDB: " + e.toString());

//             JSONObject error = new JSONObject();
//             error.put("status", "error");
//             error.put("db", dbStatus);
//             error.put("uploadOriginal", originalStatus);
//             error.put("uploadResized", resizedStatus);
//             error.put("message", e.getMessage());

//             response.setStatusCode(500);
//             response.setBody(error.toString());
//             response.setHeaders(Map.of(
//             "Content-Type", "application/json",
//             "Access-Control-Allow-Origin", "*",
//             "Access-Control-Allow-Methods", "POST,GET,DELETE,PUT,OPTIONS",
//             "Access-Control-Allow-Headers", "Content-Type"
// ));
//             return response;
//         }
//     }

//     // // ---------- Warm-up detection ----------
//     // private boolean isWarmUpInvocation(APIGatewayProxyRequestEvent request) {
//     //     // Method 1: Check for EventBridge scheduled event pattern
//     //     if (request.getHeaders() != null) {
//     //         String userAgent = request.getHeaders().get("User-Agent");
//     //         if (userAgent != null && userAgent.contains("Amazon CloudWatch Events")) {
//     //             return true;
//     //         }
//     //     }

//     //     // Method 2: Check request body for warm-up marker
//     //     if (request.getBody() != null && !request.getBody().isEmpty()) {
//     //         try {
//     //             JSONObject json = new JSONObject(request.getBody());
//     //             if (json.has("action") && WARMUP_ACTION.equals(json.getString("action"))) {
//     //                 return true;
//     //             }
//     //         } catch (Exception e) {
//     //             // Not a JSON or doesn't have the action field
//     //         }
//     //     }

//     //     return false;
//     // }

//     // // ---------- Create warm-up response ----------
//     // private APIGatewayProxyResponseEvent createWarmUpResponse() {
//     //     JSONObject warmUpResponse = new JSONObject();
//     //     warmUpResponse.put("status", "warm");
//     //     warmUpResponse.put("message", "Lambda function is warm and ready");
//     //     warmUpResponse.put("timestamp", System.currentTimeMillis());

//     //     APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
//     //     response.setStatusCode(200);
//     //     response.setBody(warmUpResponse.toString());
//     //     response.setHeaders(java.util.Map.of("Content-Type", "application/json"));
//     //     return response;
//     // }

//     // ---------- helper: upload to S3 ----------
//     private void putToS3(String bucket, String key, byte[] bytes) {
//         PutObjectRequest req = PutObjectRequest.builder()
//                 .bucket(bucket)
//                 .key(key)
//                 .build();
//         s3.putObject(req, RequestBody.fromBytes(bytes));
//     }

//     // ---------- helper: simple image resize ----------
//     // ---------- helper: safe image resize ----------
//     private byte[] resizeImage(byte[] originalBytes) throws Exception {

//         BufferedImage srcImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
//         if (srcImage == null) {
//             throw new RuntimeException("Not an image");
//         }

//         int srcWidth = srcImage.getWidth();
//         int srcHeight = srcImage.getHeight();

//         int MAX_DIMENSION = 300;

//         float scale = Math.min(
//                 (float) MAX_DIMENSION / srcWidth,
//                 (float) MAX_DIMENSION / srcHeight
//         );

//         int width = Math.round(scale * srcWidth);
//         int height = Math.round(scale * srcHeight);

//         BufferedImage resizedImage =
//                 new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

//         Graphics2D g = resizedImage.createGraphics();
//         g.setPaint(Color.WHITE);
//         g.fillRect(0, 0, width, height);
//         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
//                 RenderingHints.VALUE_INTERPOLATION_BILINEAR);
//         g.drawImage(srcImage, 0, 0, width, height, null);
//         g.dispose();

//         ByteArrayOutputStream baos = new ByteArrayOutputStream();
//         ImageIO.write(resizedImage, "png", baos);
//         return baos.toByteArray();
//     }


//     // ---------- helper: modify key name for resized ----------
//     private String addSuffixToKey(String key, String suffix) {
//         int dot = key.lastIndexOf('.');
//         if (dot == -1) {
//             return key + suffix; // no extension
//         }
//         String name = key.substring(0, dot);
//         String ext = key.substring(dot); // includes the dot
//         return name + suffix + ext;      // e.g. photo123_small.png
//     }

//     // ---------- DB connection helpers (same as your GET Lambda) ----------
//     private static Properties setMySqlConnectionProperties() throws Exception {
//         Properties mysqlConnectionProperties = new Properties();
//         mysqlConnectionProperties.setProperty("useSSL", "true");
//         mysqlConnectionProperties.setProperty("user", DB_USER);
//         mysqlConnectionProperties.setProperty("password", generateAuthToken());
//         return mysqlConnectionProperties;
//     }

//     private static String generateAuthToken() throws Exception {
//         RdsUtilities rdsUtilities = RdsUtilities.builder().build();
//         return rdsUtilities.generateAuthenticationToken(
//                 GenerateAuthenticationTokenRequest.builder()
//                         .hostname(RDS_INSTANCE_HOSTNAME)
//                         .port(RDS_INSTANCE_PORT)
//                         .username(DB_USER)
//                         .region(REGION)
//                         .credentialsProvider(DefaultCredentialsProvider.create())
//                         .build());
//     }
// }


package vgu.cloud26;

import com.amazonaws.services.lambda.runtime.*;
import com.amazonaws.services.lambda.runtime.events.*;

import org.json.JSONObject;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;

import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;

public class LambdaUploadPhotoDB implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ===== CONFIG =====
    private static final String ORIGINAL_BUCKET = "bucket-vts253";
    private static final Region REGION = Region.AP_SOUTHEAST_1;

    private static final String DB_HOST = "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int DB_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/Cloud26";

    private final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {

        String dbStatus = "error";
        String originalStatus = "error";
        String resizedStatus = "error";

        try {
            // ===== Parse request =====
            JSONObject json = new JSONObject(request.getBody());
            String key = json.getString("key");
            String description = json.getString("description");
            String contentBase64 = json.getString("content");

            byte[] originalBytes = Base64.getDecoder().decode(contentBase64);

            // ===== Activity 2: upload original =====
            putToS3(ORIGINAL_BUCKET, key, originalBytes);
            originalStatus = "success";

            // ===== Activity 3: resize & upload =====
            byte[] resizedBytes = resizeImage(originalBytes);

            String resizedBucket = "resized-" + ORIGINAL_BUCKET;
            String resizedKey = "resized-" + key;

            putToS3(resizedBucket, resizedKey, resizedBytes);
            resizedStatus = "success";

            // ===== Activity 1: insert DB =====
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, dbProps())) {
                String sql = "INSERT INTO Photos (Description, S3Key) VALUES (?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, description);
                    ps.setString(2, key);
                    ps.executeUpdate();
                    dbStatus = "success";
                }
            }

            // ===== Response =====
            JSONObject result = new JSONObject();
            result.put("db", dbStatus);
            result.put("uploadOriginal", originalStatus);
            result.put("uploadResized", resizedStatus);
            result.put("originalKey", key);
            result.put("resizedKey", resizedKey);

            return response(200, result.toString());

        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("db", dbStatus);
            err.put("uploadOriginal", originalStatus);
            err.put("uploadResized", resizedStatus);
            err.put("error", e.getMessage());
            return response(500, err.toString());
        }
    }

    // ===== Helpers =====

    private void putToS3(String bucket, String key, byte[] bytes) {
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).build(),
                RequestBody.fromBytes(bytes)
        );
    }

    private byte[] resizeImage(byte[] original) throws Exception {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
        int w = 300, h = 300;

        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(resized, "png", out);
        return out.toByteArray();
    }

    private static Properties dbProps() throws Exception {
        Properties p = new Properties();
        p.setProperty("user", DB_USER);
        p.setProperty("useSSL", "true");
        p.setProperty("password", authToken());
        return p;
    }

    private static String authToken() throws Exception {
        return RdsUtilities.builder().build()
                .generateAuthenticationToken(
                        GenerateAuthenticationTokenRequest.builder()
                                .hostname(DB_HOST)
                                .port(DB_PORT)
                                .username(DB_USER)
                                .region(REGION)
                                .credentialsProvider(DefaultCredentialsProvider.create())
                                .build());
    }

    private APIGatewayProxyResponseEvent response(int code, String body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(code)
                .withBody(body)
                .withHeaders(Map.of(
                        "Content-Type", "application/json"
                ));
    }
}
