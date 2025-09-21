package com.lgk.lgkaicodeservice.model.enums;

import lombok.Getter;

@Getter
public enum LuaStatusEnum {  
    // 成功  
    SUCCESS(1L),  
    // 失败  
    FAIL(-1L),  
    ;  
  
    private final long value;  
  
    LuaStatusEnum(long value) {  
        this.value = value;  
    }  
}
