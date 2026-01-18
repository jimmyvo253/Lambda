package vgu.cloud26;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Base64;
import java.util.Collections;
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

public class LambdaDeletePhotoDB implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    // ===== RDS CONFIG =====
    private static final String RDS_INSTANCE_HOSTNAME =
            "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int RDS_INSTANCE_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

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
            String s3key = json.getString("key");
            String email = json.getString("email");

            // 2️⃣ Delete from database
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(
                    JDBC_URL, setMySqlConnectionProperties())) {

                String sql = "DELETE FROM Photos WHERE S3Key = ? AND Email = ?";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, s3key);
                    st.setString(2, email);
                    int rows = st.executeUpdate();
                    logger.log("DB rows deleted: " + rows);
                }
            }

        } catch (Exception ex) {
            logger.log("DELETE ERROR: " + ex.toString());

            response.setStatusCode(500);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json"
            ));
            response.setBody(
                    new JSONObject()
                            .put("status", "error")
                            .put("message", ex.getMessage())
                            .toString()
            );
        }

        String encoded = Base64.getEncoder().encodeToString(response.toString().getBytes());
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withIsBase64Encoded(true)
                .withBody(encoded)
                .withHeaders(Collections.singletonMap("Content-Type", "application/json"));
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
