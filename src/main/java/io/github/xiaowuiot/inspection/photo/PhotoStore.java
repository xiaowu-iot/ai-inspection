package io.github.xiaowuiot.inspection.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.stereotype.Component;

import io.github.xiaowuiot.inspection.common.ApiException;
import io.github.xiaowuiot.inspection.config.InspectionProperties;

/**
 * 照片按月分目录存放。照片编号形如 202610/3f2a...，编号本身就是相对路径。
 */
@Component
public class PhotoStore {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path root;

    public PhotoStore(InspectionProperties props) throws IOException {
        this.root = Files.createDirectories(Path.of(props.dataDir()).toAbsolutePath().normalize().resolve("photos"));
    }

    public String save(byte[] jpeg) {
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            throw ApiException.badRequest("只支持 JPEG 照片");
        }
        String id = LocalDate.now().format(MONTH) + "/" + UUID.randomUUID().toString().replace("-", "");
        Path file = resolve(id);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, jpeg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return id;
    }

    public byte[] read(String id) {
        try {
            return Files.readAllBytes(resolve(id));
        } catch (IOException e) {
            throw ApiException.notFound("照片文件丢失");
        }
    }

    public Path resolve(String id) {
        // 编号只允许"6 位数字/32 位十六进制"，防止路径穿越
        if (id == null || !id.matches("\\d{6}/[0-9a-f]{32}")) {
            throw ApiException.notFound("照片不存在");
        }
        return root.resolve(id + ".jpg");
    }
}
