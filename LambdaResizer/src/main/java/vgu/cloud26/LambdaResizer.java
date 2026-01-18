package vgu.cloud26;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

import javax.imageio.ImageIO;

import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

/**
 * Updated Resizer: 
 * 1. Uses Map input for direct Orchestrator invocation.
 * 2. Uses bucket-vts253 as the base name.
 * 3. Processes image in-memory to avoid S3 race conditions.
 */
public class LambdaResizer implements RequestHandler<Map<String, Object>, String> {

    private static final float MAX_DIM = 100;
    // Static client for better performance (connection reuse)
    private static final S3Client s3 = S3Client.builder()
            .region(Region.AP_SOUTHEAST_1)
            .build();

    @Override
    public String handleRequest(Map<String, Object> input, Context ctx) {
        ctx.getLogger().log("Resizer started processing...");

        try {
            // Convert the direct Map input from Orchestrator to JSONObject
            JSONObject body = new JSONObject(input);
            
            if (!body.has("key") || !body.has("content")) {
                return "{\"status\":\"error\",\"message\":\"Missing key or content in payload\"}";
            }

            String key = body.getString("key");
            String content = body.getString("content");

            // Decode the Base64 image sent by the Orchestrator
            byte[] srcBytes = Base64.getDecoder().decode(content);

            // Read image from memory
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(srcBytes));
            if (src == null) {
                throw new IOException("Could not decode image bytes (invalid format)");
            }

            // Perform Resize
            BufferedImage resized = resize(src);

            // Re-encode to JPG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);

            // Set bucket names based on your requirements
            String dstBucket = "resized-bucket-vts253"; 
            String dstKey = "resized-" + key;

            // Upload to S3
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(dstBucket)
                    .key(dstKey)
                    .contentType("image/jpeg")
                    .build(),
                RequestBody.fromBytes(baos.toByteArray())
            );

            ctx.getLogger().log("Successfully uploaded resized image to: " + dstKey);

            return "{\"status\":\"success\",\"saved\":\"" + dstKey + "\"}";

        } catch (Exception e) {
            ctx.getLogger().log("Error in Resizer: " + e.getMessage());
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private BufferedImage resize(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        float scale = Math.min(MAX_DIM / w, MAX_DIM / h);

        int newW = Math.max(1, (int) (w * scale));
        int newH = Math.max(1, (int) (h * scale));

        BufferedImage img = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return img;
    }
}