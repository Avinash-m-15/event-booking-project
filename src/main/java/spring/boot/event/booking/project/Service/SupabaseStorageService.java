package spring.boot.event.booking.project.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.UUID;
import java.util.Set;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    private static final String BUCKET_NAME = "event-images";
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final WebClient webClient;

    public SupabaseStorageService(@Value("${supabase.url}") String supabaseUrl,
                                  @Value("${supabase.key}") String supabaseKey) {
        this.webClient = WebClient.builder()
                .baseUrl(supabaseUrl)
                .defaultHeader("apikey", supabaseKey)
                .defaultHeader("Authorization", "Bearer " + supabaseKey)
                .build();
    }

    public String uploadImage(MultipartFile file) throws Exception {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are allowed.");
        }

        String ext = getExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + ext;

        webClient.post()
                .uri("/storage/v1/object/" + BUCKET_NAME + "/" + fileName)
                .header("Content-Type", contentType)
                .header("x-upsert", "false")
                .bodyValue(file.getBytes())
                .retrieve()
                .toBodilessEntity()
                .block();

        return supabaseUrl + "/storage/v1/object/public/" + BUCKET_NAME + "/" + fileName;
    }

    private String getExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf(".")).toLowerCase();
        }
        return ".jpg";
    }
}
