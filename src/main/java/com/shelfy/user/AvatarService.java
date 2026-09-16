package com.shelfy.user;

import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Set;

/**
 * Recorta a cuadrado (centrado) y redimensiona cualquier imagen subida a
 * {@value #TARGET_SIZE}x{@value #TARGET_SIZE} px en JPEG antes de
 * guardarla: así el tamaño en base de datos es predecible (unas pocas
 * decenas de KB) sin depender de lo que suba cada usuario, y el frontend
 * siempre recibe algo ya recortado en cuadrado, listo para un círculo.
 */
@Service
@RequiredArgsConstructor
public class AvatarService {

    private static final int TARGET_SIZE = 320;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final UserRepository userRepository;
    private final AvatarRepository avatarRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo está vacío");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("La imagen no puede superar los 5 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Formato no soportado. Usa PNG, JPEG o WEBP");
        }

        BufferedImage original;
        try {
            original = ImageIO.read(file.getInputStream());
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se ha podido leer la imagen");
        }
        if (original == null) {
            throw new IllegalArgumentException("El archivo no es una imagen válida");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        byte[] jpegBytes = toSquareJpeg(original);
        Instant now = Instant.now();

        Avatar avatar = avatarRepository.findById(userId)
                .orElseGet(() -> Avatar.builder().user(user).build());
        avatar.setImageData(jpegBytes);
        avatar.setUpdatedAt(now);
        avatarRepository.save(avatar);

        user.setAvatarUpdatedAt(now);

        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse remove(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        avatarRepository.findById(userId).ifPresent(avatarRepository::delete);
        user.setAvatarUpdatedAt(null);

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public Avatar get(Long userId) {
        return avatarRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Avatar", userId));
    }

    private byte[] toSquareJpeg(BufferedImage original) {
        int size = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - size) / 2;
        int y = (original.getHeight() - size) / 2;
        BufferedImage cropped = original.getSubimage(x, y, size, size);

        BufferedImage resized = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, TARGET_SIZE, TARGET_SIZE);
        g.drawImage(cropped, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(resized, "jpg", out);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se ha podido procesar la imagen", ex);
        }
        return out.toByteArray();
    }
}
