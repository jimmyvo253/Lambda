package vgu.cloud26;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.Properties;

import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rds.RdsUtilities;
import software.amazon.awssdk.services.rds.model.GenerateAuthenticationTokenRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

public class LambdaDeleteOrchestration implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ===== RDS CONFIG =====
    private static final String RDS_INSTANCE_HOSTNAME =
            "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int RDS_INSTANCE_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

    // ===== S3 CONFIG =====
    private static final String ORIGINAL_BUCKET = "bucket-vts253";
    private static final String RESIZED_BUCKET  = "resized-bucket-vts253";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            APIGatewayProxyRequestEvent request, Context context) {

        LambdaLogger logger = context.getLogger();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            // 1️⃣ Parse input
            String body = request.getBody();
            logger.log("DELETE body: " + body);

            JSONObject json = new JSONObject(body);
            String key = json.getString("key");

            // 2️⃣ Delete from database
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(
                    JDBC_URL, setMySqlConnectionProperties())) {

                String sql = "DELETE FROM Photos WHERE S3Key = ?";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, key);
                    int rows = st.executeUpdate();
                    logger.log("DB rows deleted: " + rows);
                }
            }

            // 3️⃣ Delete from S3 (original + resized)
            S3Client s3 = S3Client.builder().build();

            // original
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(ORIGINAL_BUCKET)
                    .key(key)
                    .build());

            // resized
            String resizedKey = "resized-" + key;
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(RESIZED_BUCKET)
                    .key(resizedKey)
                    .build());

            // 4️⃣ Success response
            JSONObject result = new JSONObject();
            result.put("status", "ok");
            result.put("deletedKey", key);

            response.setStatusCode(200);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*",
                    "Access-Control-Allow-Methods", "DELETE,OPTIONS",
                    "Access-Control-Allow-Headers", "Content-Type"
            ));
            response.setBody(result.toString());

        } catch (Exception ex) {
            logger.log("DELETE ERROR: " + ex.toString());

            response.setStatusCode(500);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json",
                    "Access-Control-Allow-Origin", "*"
            ));
            response.setBody(
                    new JSONObject()
                            .put("status", "error")
                            .put("message", ex.getMessage())
                            .toString()
            );
        }

        return response;
    }

    // ===== IAM AUTH FOR RDS =====
    private static Properties setMySqlConnectionProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("useSSL", "true");
        props.setProperty("user", DB_USER);
        props.setProperty("password", generateAuthToken());
        return props;
    }

    private static String generateAuthToken() throws Exception {
        RdsUtilities rdsUtilities = RdsUtilities.builder().build();
        return rdsUtilities.generateAuthenticationToken(
                GenerateAuthenticationTokenRequest.builder()
                        .hostname(RDS_INSTANCE_HOSTNAME)
                        .port(RDS_INSTANCE_PORT)
                        .username(DB_USER)
                        .region(Region.AP_SOUTHEAST_1)
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build());
    }
}
