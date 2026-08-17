package com.meter.app.dataimport.service;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.dataimport.dto.ImportResult;
import com.meter.app.dataimport.dto.ImportTemplateResponse;
import com.meter.app.dataimport.entity.ImportTemplate;
import com.meter.app.dataimport.repository.ImportTemplateRepository;
import com.meter.app.ledger.entity.Meter;
import com.meter.app.ledger.repository.MeterRepository;
import com.meter.app.reading.dto.ReadingRequest;
import com.meter.app.reading.service.ReadingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可配置 Excel 导入(TRD §6.4)。
 * 通过模板的 fieldMapping(Excel列名→系统字段)解析每行,按表名定位表计,复用读数提交逻辑
 * (自动继承幂等、用量计算、异常标记与 READER 公司范围校验)。
 */
@Service
public class ImportService {

    // 系统字段名
    private static final String F_METER_NAME = "meterName";
    private static final String F_CURR_READING = "currReading";
    private static final String F_PHOTO_URL = "photoUrl";
    private static final String F_REMARK = "remark";

    private final ImportTemplateRepository templateRepository;
    private final MeterRepository meterRepository;
    private final ReadingService readingService;
    private final ObjectMapper objectMapper;

    public ImportService(ImportTemplateRepository templateRepository,
                         MeterRepository meterRepository,
                         ReadingService readingService,
                         ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.meterRepository = meterRepository;
        this.readingService = readingService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportTemplateResponse> listTemplates() {
        return templateRepository.findByOrgIdOrderByIdDesc(CurrentUser.orgId())
                .stream().map(ImportTemplateResponse::from).toList();
    }

    /** 新建模板。fieldMapping 是 Excel列名→系统字段 的映射。 */
    @Transactional
    public ImportTemplateResponse createTemplate(String name, Map<String, String> fieldMapping) {
        if (fieldMapping == null || fieldMapping.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段映射不能为空");
        }
        ImportTemplate t = new ImportTemplate();
        t.setOrgId(CurrentUser.orgId());
        t.setName(name);
        try {
            t.setFieldMapping(objectMapper.writeValueAsString(fieldMapping));
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "字段映射格式错误");
        }
        return ImportTemplateResponse.from(templateRepository.save(t));
    }

    /** 生成该模板的样例 Excel(只含表头行,列名取 fieldMapping 的 Excel 列名)。 */
    @Transactional(readOnly = true)
    public byte[] buildSampleHead(Long templateId) {
        ImportTemplate template = templateRepository.findByIdAndOrgId(templateId, CurrentUser.orgId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导入模板不存在"));
        Map<String, String> mapping = parseMapping(template);
        List<List<String>> head = new ArrayList<>();
        for (String excelHeader : mapping.keySet()) {
            head.add(List.of(excelHeader));
        }
        return com.meter.app.export.ExcelUtil.write(head, List.of());
    }

    /**
     * 按模板导入某周期的读数。返回成功/失败行数与错误明细。
     * 解析失败或表头缺列 → 4001。
     */
    @Transactional
    public ImportResult importReadings(Long templateId, Long periodId, MultipartFile file) {
        Long orgId = CurrentUser.orgId();
        ImportTemplate template = templateRepository.findByIdAndOrgId(templateId, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "导入模板不存在"));
        Map<String, String> mapping = parseMapping(template);

        // 校验映射必须覆盖 meterName 与 currReading
        if (!mapping.containsValue(F_METER_NAME) || !mapping.containsValue(F_CURR_READING)) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "模板必须映射表名与本期读数");
        }

        List<Map<Integer, String>> rows = readRows(file);
        if (rows.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "文件为空或无表头");
        }

        // 第一行为表头:列索引 → 列名;再结合模板得到 列索引 → 系统字段
        Map<Integer, String> header = rows.get(0);
        Map<Integer, String> colToField = new HashMap<>();
        for (Map.Entry<Integer, String> e : header.entrySet()) {
            String colName = e.getValue() == null ? "" : e.getValue().trim();
            String field = mapping.get(colName);
            if (field != null) {
                colToField.put(e.getKey(), field);
            }
        }
        if (!colToField.containsValue(F_METER_NAME) || !colToField.containsValue(F_CURR_READING)) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "表头缺少必要列(表名/本期读数)");
        }

        int success = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            int rowNum = i + 1; // Excel 行号(1-based,含表头)
            try {
                Map<String, String> record = new HashMap<>();
                for (Map.Entry<Integer, String> e : rows.get(i).entrySet()) {
                    String field = colToField.get(e.getKey());
                    if (field != null) {
                        record.put(field, e.getValue());
                    }
                }
                importOneRow(orgId, periodId, record);
                success++;
            } catch (BizException be) {
                errors.add("第" + rowNum + "行: " + be.getMessage());
            } catch (Exception ex) {
                errors.add("第" + rowNum + "行: 解析失败 " + ex.getMessage());
            }
        }
        return new ImportResult(success, errors.size(), errors);
    }

    private void importOneRow(Long orgId, Long periodId, Map<String, String> record) {
        String meterName = trim(record.get(F_METER_NAME));
        String currStr = trim(record.get(F_CURR_READING));
        if (meterName == null || currStr == null) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "表名或读数为空");
        }
        List<Meter> meters = meterRepository.findByOrgIdAndName(orgId, meterName);
        if (meters.isEmpty()) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "找不到表计: " + meterName);
        }
        if (meters.size() > 1) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "表名重复,无法定位: " + meterName);
        }
        BigDecimal curr;
        try {
            curr = new BigDecimal(currStr);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "读数不是数字: " + currStr);
        }
        ReadingRequest req = new ReadingRequest(
                meters.get(0).getId(), periodId, curr,
                trim(record.get(F_PHOTO_URL)), null, trim(record.get(F_REMARK)));
        // 复用读数提交:自动做用量计算、异常标记、置待审核、READER 公司范围校验
        readingService.submit(req);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMapping(ImportTemplate template) {
        try {
            return objectMapper.readValue(template.getFieldMapping(),
                    new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "模板字段映射解析失败");
        }
    }

    /** EasyExcel 无模型读取:返回每行 列索引→单元格字符串(含表头行)。 */
    private List<Map<Integer, String>> readRows(MultipartFile file) {
        try {
            return EasyExcel.read(file.getInputStream())
                    .sheet().headRowNumber(0).doReadSync();
        } catch (IOException e) {
            throw new BizException(ErrorCode.IMPORT_PARSE_FAIL, "文件读取失败");
        }
    }

    private String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
