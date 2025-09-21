package com.lgk.lgkaicodeservice.utils;

import com.lgk.lgkaicodeservice.constant.ThumbConstant;
import com.lgk.lgkaicodeservice.model.enums.ThumbTypeEnum;

public class RedisKeyUtil {
  
    public static String getUserThumbKey(Long userId, ThumbTypeEnum type) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId + ":" + type.getValue();
    }  
  
    /**  
     * 获取 临时点赞记录 key  
     */  
    public static String getTempThumbKey(String time) {  
        return ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(time);  
    }  
  
}
