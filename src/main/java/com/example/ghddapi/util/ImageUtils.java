package com.example.ghddapi.util;

import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
public class ImageUtils {

    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    /**
     * 从资源文件加载图片
     */
    public byte[] loadImageFromResource(String imagePath) {
        try {
            // 处理路径，确保以/开头
            String normalizedPath = imagePath.startsWith("/") ? imagePath : "/" + imagePath;
            ClassPathResource imageResource = new ClassPathResource(normalizedPath);

            if (!imageResource.exists()) {
                log.warn("图片文件不存在: {}", normalizedPath);
                return null;
            }

            return FileCopyUtils.copyToByteArray(imageResource.getInputStream());
        } catch (IOException e) {
            log.error("加载图片失败: {}", imagePath, e);
            return null;
        }
    }

    /**
     * 获取图片格式
     */
    public int getPictureType(byte[] imageData) {
        if (imageData == null || imageData.length < 8) {
            return XWPFDocument.PICTURE_TYPE_PNG; // 默认PNG
        }

        // 检查文件头判断图片格式
        if (imageData[0] == (byte) 0xFF && imageData[1] == (byte) 0xD8) {
            return XWPFDocument.PICTURE_TYPE_JPEG;
        } else if (imageData[0] == (byte) 0x89 && imageData[1] == (byte) 0x50 &&
                imageData[2] == (byte) 0x4E && imageData[3] == (byte) 0x47) {
            return XWPFDocument.PICTURE_TYPE_PNG;
        } else if (imageData[0] == (byte) 0x47 && imageData[1] == (byte) 0x49 &&
                imageData[2] == (byte) 0x46) {
            return XWPFDocument.PICTURE_TYPE_GIF;
        } else if (imageData[0] == (byte) 0x42 && imageData[1] == (byte) 0x4D) {
            return XWPFDocument.PICTURE_TYPE_BMP;
        }

        return XWPFDocument.PICTURE_TYPE_PNG; // 默认PNG
    }

    /**
     * 获取图片尺寸（可选）
     */
    public Dimension getImageDimension(byte[] imageData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image != null) {
                return new Dimension(image.getWidth(), image.getHeight());
            }
        } catch (IOException e) {
            log.warn("获取图片尺寸失败", e);
        }
        return new Dimension(100, 50); // 默认尺寸
    }
}