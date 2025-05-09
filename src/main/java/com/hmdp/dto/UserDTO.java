package com.hmdp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String icon;
    
    // 显式添加getter方法，防止Lombok失效
    public Long getId() {
        return id;
    }
    
    public String getNickName() {
        return nickName;
    }
    
    public String getIcon() {
        return icon;
    }
}
