package com.meter.app.org.entity;

/**
 * 成员角色,对应 TRD 2.2。
 */
public enum Role {
    ADMIN,   // 管理员:全部权限
    READER,  // 抄表员:录入读数
    VIEWER   // 查看者:只读
}
