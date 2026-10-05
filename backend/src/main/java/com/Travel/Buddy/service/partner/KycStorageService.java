package com.Travel.Buddy.service.partner;

import com.Travel.Buddy.exception.PartnerApplicationException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stores partner KYC uploads on the local filesystem.
 *
 * <p>Security rules applied here, because partner documents are the most
 * sensitive personal data Travel Buddy holds:
 *
 * <ul>
 *   <li>The client-supplied filename is <strong>never</strong> used to
 *       build a path. Files are written under a server-generated random
 *       name inside a per-application directory, which removes any
 *       possibility of path traversal or overwriting an unrelated file.</li>
 *   <li>Uploads are restricted to an allow-list of content types, and
 *       the extension is derived from the <em>validated</em> type rather
 *       than from user input.</li>
 *   <li>Size is bounded by Spring's multipart limits and re-checked here.</li>
 *   <li>Raw filesystem locations are never returned to a client.</li>
 * </ul>
 */
@Service
public class KycStorageService {

    /**
     * Only document and image formats are accepted. Executables, HTML
     * and SVG are deliberately excluded because they can be rendered in
     * a browser and become a stored-XSS vector.
     */
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "application/pdf",
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private static final Map<String, String> EXTENSION_BY_TYPE =
            Map.of(
                    "application/pdf", ".pdf",
                    "image/jpeg", ".jpg",
                    "image/png", ".png",
                    "image/webp", ".webp"
            );

    private static final long MAX_FILE_BYTES =
            10L * 1024L * 1024L;

    private final SecureRandom secureRandom =
            new SecureRandom();

    private final Path rootDirectory;

    public KycStorageService(
            @Value("${app.kyc.storage-path:./uploads/kyc}")
            String storagePath
    ) {
        this.rootDirectory = Paths
                .get(storagePath)
                .toAbsolutePath()
                .normalize();
    }


    /**
     * Persists an upload and returns a {@link StoredFile} describing
     * what was written.
     */
    public StoredFile store(
            MultipartFile file,
            Long applicationId,
            String documentCode
    ) {

        validate(file);

        Path applicationDirectory =
                rootDirectory
                        .resolve("application-" + applicationId)
                        .normalize();

        /*
         * Defence in depth: even though applicationId is a server-side
         * Long, verify the resolved directory is still inside the root.
         */
        if (!applicationDirectory.startsWith(rootDirectory)) {
            throw PartnerApplicationException.badRequest(
                    "Invalid document destination"
            );
        }

        try {

            Files.createDirectories(
                    applicationDirectory
            );

            String randomName =
                    HexFormat.of().formatHex(
                            randomBytes(16)
                    ) + EXTENSION_BY_TYPE.get(
                            file.getContentType()
                                    .toLowerCase(Locale.ROOT)
                                    .trim()
                    );

            Path target =
                    applicationDirectory.resolve(randomName);

            try (InputStream input = file.getInputStream()) {

                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return new StoredFile(
                    sanitiseFileName(
                            file.getOriginalFilename()
                    ),
                    rootDirectory.relativize(target).toString(),
                    file.getContentType(),
                    file.getSize()
            );

        } catch (IOException exception) {

            throw new IllegalStateException(
                    "Failed to store the uploaded document",
                    exception
            );
        }
    }


    /**
     * Deletes a previously stored file. Missing files are ignored so
     * that replacing a document never fails on cleanup.
     */
    public void delete(String relativePath) {

        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        Path resolved =
                rootDirectory
                        .resolve(relativePath)
                        .normalize();

        if (!resolved.startsWith(rootDirectory)) {
            return;
        }

        try {
            Files.deleteIfExists(resolved);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to delete the stored document",
                    exception
            );
        }
    }

    public Path resolveForRead(String relativePath) {

        if (relativePath == null || relativePath.isBlank()) {
            throw PartnerApplicationException.notFound(
                    "No document has been uploaded yet"
            );
        }

        Path resolved =
                rootDirectory
                        .resolve(relativePath)
                        .normalize();

        if (!resolved.startsWith(rootDirectory)) {
            throw PartnerApplicationException.forbidden(
                    "Invalid document path"
            );
        }

        if (!Files.isReadable(resolved)) {
            throw PartnerApplicationException.notFound(
                    "The stored document is no longer available"
            );
        }

        return resolved;
    }

    private void validate(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw PartnerApplicationException.badRequest(
                    "No file was uploaded"
            );
        }

        if (file.getSize() > MAX_FILE_BYTES) {
            throw PartnerApplicationException.badRequest(
                    "Documents must be 10 MB or smaller"
            );
        }

        String contentType =
                file.getContentType() == null
                        ? ""
                        : file.getContentType()
                                .toLowerCase(Locale.ROOT)
                                .trim();

        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw PartnerApplicationException.badRequest(
                    "Only PDF, JPG, PNG and WEBP documents are accepted"
            );
        }
    }

    /**
     * Keeps a readable original name for display, but strips any
     * directory component so the stored value can never influence a path.
     */
    private String sanitiseFileName(String originalFileName) {

        if (originalFileName == null
                || originalFileName.isBlank()) {

            return "document";
        }

        String baseName =
                Paths.get(originalFileName)
                        .getFileName()
                        .toString();

        return baseName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    /**
     * @param fileName     original name, safe to show to the owner
     * @param relativePath opaque path relative to the KYC root
     */
    public record StoredFile(
            String fileName,
            String relativePath,
            String contentType,
            Long sizeBytes
    ) {
    }

    /**
     * Accepted upload types, so the API can advertise them instead of
     * hard-coding them in the frontend.
     */
    public static List<String> allowedContentTypes() {
        return List.copyOf(ALLOWED_CONTENT_TYPES);
    }
}
