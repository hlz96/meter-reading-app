package com.meter.app.ledger.entity;

/**
 * 表计类型。用 code 与 DB 的 tinyint 对应(TRD 3.2)。
 */
public enum MeterType {
    ELECTRIC(1),  // 电表
    WATER(2);     // 水表

    private final int code;

    MeterType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MeterType fromCode(int code) {
        for (MeterType t : values()) {
            if (t.code == code) return t;
        }
        throw new IllegalArgumentException("未知表计类型: " + code);
    }
}
