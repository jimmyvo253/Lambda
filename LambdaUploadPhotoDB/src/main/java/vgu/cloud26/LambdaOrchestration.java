package vgu.cloud26;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Properties;

import javax.imageio.ImageIO;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class LambdaOrchestration implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ----- RDS config -----
    private static final String RDS_INSTANCE_HOSTNAME =
            "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int RDS_INSTANCE_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

    // ----- S3 config -----
    private static final Region REGION = Region.AP_SOUTHEAST_1;
    private static final String ORIGINAL_BUCKET = "bucket-vts253";
    private static final String RESIZED_BUCKET  = "resized-bucket-vts253";

    private final S3Client s3 = S3Client.builder()
            .region(REGION)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LambdaLogger log = context.getLogger();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        String dbStatus = "not-run";
        String originalStatus = "not-run";
        String resizedStatus = "not-run";

        try {
            // 1. Parse request JSON
            String body = request.getBody();
            log.log("Request body: " + body);
            JSONObject json = new JSONObject(body);

            String key = json.getString("key");
            String description = json.getString("description");
            String contentBase64 = json.getString("content");

            byte[] originalBytes = Base64.getDecoder().decode(contentBase64);

            // 2. Upload original file to S3
            log.log("Uploading original to S3: bucket=" + ORIGINAL_BUCKET + ", key=" + key);
            putToS3(ORIGINAL_BUCKET, key, originalBytes);
            originalStatus = "success";

            // 3. Resize image and upload resized version
            log.log("Resizing image...");
            byte[] resizedBytes = resizeImage(originalBytes, 300, 300); // 300x300 example
            String resizedKey = addSuffixToKey(key, "_small"); // e.g. uploads/photo123_small.jpg

            log.log("Uploading resized to S3: bucket=" + RESIZED_BUCKET + ", key=" + resizedKey);
            putToS3(RESIZED_BUCKET, resizedKey, resizedBytes);
            resizedStatus = "success";

            // 4. Insert metadata into RDS
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, setMySqlConnectionProperties())) {
                String sql = "INSERT INTO Photos (Description, S3Key) VALUES (?, ?)";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, description);
                    st.setString(2, key);   // store original key, or resizedKey if required
                    int rows = st.executeUpdate();
                    log.log("Inserted rows: " + rows);
                    dbStatus = "success";
                }
            }

            // 5. Build success response
            JSONObject result = new JSONObject();
            result.put("status", "ok");
            result.put("db", dbStatus);
            result.put("uploadOriginal", originalStatus);
            result.put("uploadResized", resizedStatus);
            result.put("key", key);
            result.put("resizedKey", resizedKey);
            result.put("description", description);

            response.setStatusCode(200);
            response.setBody(result.toString());
            response.setHeaders(java.util.Map.of("Content-Type", "application/json"));
            return response;

        } catch (Exception e) {
            log.log("ERROR in LambdaUploadPhotoDB: " + e.toString());

            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("db", dbStatus);
            error.put("uploadOriginal", originalStatus);
            error.put("uploadResized", resizedStatus);
            error.put("message", e.getMessage());

            response.setStatusCode(500);
            response.setBody(error.toString());
            response.setHeaders(java.util.Map.of("Content-Type", "application/json"));
            return response;
        }
    }

    // ---------- helper: upload to S3 ----------
    private void putToS3(String bucket, String key, byte[] bytes) {
        PutObjectRequest req = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        s3.putObject(req, RequestBody.fromBytes(bytes));
    }

    // ---------- helper: simple image resize ----------
    private byte[] resizeImage(byte[] originalBytes, int width, int height) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(originalBytes)) {
            BufferedImage original = ImageIO.read(bais);
            BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, width, height, null);
            g.dispose();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                ImageIO.write(resized, "png", baos);
                return baos.toByteArray();
            }
        }
    }

    // ---------- helper: modify key name for resized ----------
    private String addSuffixToKey(String key, String suffix) {
        int dot = key.lastIndexOf('.');
        if (dot == -1) {
            return key + suffix; // no extension
        }
        String name = key.substring(0, dot);
        String ext = key.substring(dot); // includes the dot
        return name + suffix + ext;      // e.g. photo123_small.png
    }

    // ---------- DB connection helpers (same as your GET Lambda) ----------
    private static Properties setMySqlConnectionProperties() throws Exception {
        Properties mysqlConnectionProperties = new Properties();
        mysqlConnectionProperties.setProperty("useSSL", "true");
        mysqlConnectionProperties.setProperty("user", DB_USER);
        mysqlConnectionProperties.setProperty("password", generateAuthToken());
        return mysqlConnectionProperties;
    }

    private static String generateAuthToken() throws Exception {
        RdsUtilities rdsUtilities = RdsUtilities.builder().build();
        return rdsUtilities.generateAuthenticationToken(
                GenerateAuthenticationTokenRequest.builder()
                        .hostname(RDS_INSTANCE_HOSTNAME)
                        .port(RDS_INSTANCE_PORT)
                        .username(DB_USER)
                        .region(REGION)
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build());
    }
}
