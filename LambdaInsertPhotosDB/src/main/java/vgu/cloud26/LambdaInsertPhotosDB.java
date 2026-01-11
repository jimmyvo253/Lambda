package vgu.cloud26;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
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

public class LambdaInsertPhotosDB implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String RDS_INSTANCE_HOSTNAME =
            "database-cloud26.cpsosuuietga.ap-southeast-1.rds.amazonaws.com";
    private static final int RDS_INSTANCE_PORT = 3306;
    private static final String DB_USER = "cloud26";
    private static final String JDBC_URL =
            "jdbc:mysql://" + RDS_INSTANCE_HOSTNAME + ":" + RDS_INSTANCE_PORT + "/Cloud26";

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LambdaLogger logger = context.getLogger();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            // 1. Parse JSON body: { "Description": "...", "S3Key": "..." }
            String body = request.getBody();
            logger.log("Request body: " + body);
            JSONObject json = new JSONObject(body);

            String description = json.getString("Description");
            String s3Key = json.getString("S3Key");

            // 2. Connect to MySQL (using same IAM token logic)
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(JDBC_URL, setMySqlConnectionProperties())) {

                // 3. INSERT INTO Photos table
                String sql = "INSERT INTO Photos (Description, S3Key) VALUES (?, ?)";
                try (PreparedStatement st = conn.prepareStatement(sql)) {
                    st.setString(1, description);
                    st.setString(2, s3Key);
                    int rows = st.executeUpdate();
                    logger.log("Inserted rows: " + rows);
                }
            }

            // 4. Build success JSON
            JSONObject result = new JSONObject();
            result.put("status", "ok");
            result.put("message", "Photo inserted");
            result.put("Description", description);
            result.put("S3Key", s3Key);

            response.setStatusCode(200);
            response.setBody(result.toString());
            response.setHeaders(java.util.Map.of("Content-Type", "application/json"));

        } catch (Exception ex) {
            logger.log("ERROR in LambdaInsertPhotoDB: " + ex.toString());

            JSONObject error = new JSONObject();
            error.put("status", "error");
            error.put("message", ex.getMessage());

            response.setStatusCode(500);
            response.setBody(error.toString());
            response.setHeaders(java.util.Map.of("Content-Type", "application/json"));
        }

        return response;
    }

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
                        .region(Region.AP_SOUTHEAST_1)
                        .credentialsProvider(DefaultCredentialsProvider.create())
                        .build());
    }
}
