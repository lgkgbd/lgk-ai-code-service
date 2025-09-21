package com.lgk.lgkaicodeservice.model.enums;

import lombok.Getter;  
  
/**  
 * 点赞类型  
 *  
 */
@Getter  
public enum ThumbEnum {  
    // 点赞  
    INCR(1),
    // 取消点赞  
    DECR(-1),
    // 不发生改变  
    NON(0),
    ;
  
    private final int value;  
  
    ThumbEnum(int value) {
        this.value = value;  
    }

}
