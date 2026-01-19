    package vgu.cloud26;

    import java.sql.Connection;
    import java.sql.DriverManager;
    import java.sql.PreparedStatement;
import java.sql.ResultSet;
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

public class LambdaDeletePhotoDB
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String RDS_INSTANCE_HOSTNAME =
            "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int RDS_INSTANCE_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Parse the body provided by the Orchestrator
            JSONObject body = new JSONObject(request.getBody());
            
            // Use optString to prevent "key not found" exceptions
            String key = body.optString("key");
            String email = body.optString("email");

            if (key.isEmpty() || email.isEmpty()) {
                return error(400, "Missing required fields");
            }

            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection conn = DriverManager.getConnection(JDBC_URL, setMySqlConnectionProperties())) {
                // Check ownership
                String checkSql = "SELECT Email FROM Photos WHERE S3Key = ?";
                try (PreparedStatement check = conn.prepareStatement(checkSql)) {
                    check.setString(1, key);
                    ResultSet rs = check.executeQuery();

                    if (!rs.next()) return error(404, "Photo not found");

                    String ownerEmail = rs.getString("Email");
                    // Case-insensitive comparison is safer for emails
                    if (!email.equalsIgnoreCase(ownerEmail)) {
                        return error(403, "Permission Denied: You do not own this photo.");
                    }
                }

                // Delete record
                String deleteSql = "DELETE FROM Photos WHERE S3Key = ?";
                try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                    del.setString(1, key);
                    del.executeUpdate();
                }
            }
            return success("DB record removed successfully");

        } catch (Exception e) {
            return error(500, e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent success(String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withIsBase64Encoded(false)
                .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                .withBody(new JSONObject()
                        .put("status", "ok")
                        .put("message", msg)
                        .toString());
    }

    private APIGatewayProxyResponseEvent error(int code, String msg) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(code)
                .withIsBase64Encoded(false)
                .withHeaders(Collections.singletonMap("Content-Type", "application/json"))
                .withBody(new JSONObject()
                        .put("status", "error")
                        .put("message", msg)
                        .toString());
    }

    private static Properties setMySqlConnectionProperties() throws Exception {
        Properties props = new Properties();
        props.setProperty("useSSL", "true");
        props.setProperty("user", DB_USER);
        props.setProperty("password", generateAuthToken());
        return props;
    }

    private static String generateAuthToken() throws Exception {
        RdsUtilities rds = RdsUtilities.builder().build();
        return rds.generateAuthenticationToken(
                GenerateAuthenticationTokenRequest.builder()
                        .hostname(RDS_INSTANCE_HOSTNAME)
                        .port(RDS_INSTANCE_PORT)
                        .username(DB_USER)
                        .region(Region.AP_SOUTHEAST_1)
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build());
    }
}


