package com.meter.app.export;

import com.alibaba.excel.EasyExcel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 字节流工具(导出/样例共用)。
 * EasyExcel 写入内存 ByteArrayOutputStream,不落盘。
 */
public final class ExcelUtil {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelUtil() {}

    /** 写一个单 sheet 的 xlsx:head 每项是一列的表头,rows 每项是一行的单元格值。 */
    public static byte[] write(List<List<String>> head, List<List<Object>> rows) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        EasyExcel.write(bos).head(head).sheet("Sheet1").doWrite(rows);
        return bos.toByteArray();
    }

    /**
     * 包装成附件下载响应。二进制流无法进统一 JSON envelope,故 Controller 直接返回 ResponseEntity<byte[]>。
     * 中文文件名必须 URL 编码,否则部分容器会因非法响应头抛异常。
     */
    public static ResponseEntity<byte[]> asAttachment(byte[] data, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename*=UTF-8''" + encoded + ".xlsx");
        return ResponseEntity.ok().headers(headers).body(data);
    }
}
