package com.cy.share.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    // 添加的时候 填充
    @Override
    public void insertFill(MetaObject metaObject) {
        this.setFieldValByName("createTime", new Date(), metaObject);
        this.setFieldValByName("updateTime", new Date(), metaObject);
        this.setFieldValByName("version", 1, metaObject);
        this.setFieldValByName("deleted", 1, metaObject);
    }
    // 修改的时候 填充
    @Override
    public void updateFill(MetaObject metaObject) {
        log.info("start update fill 。。。");  // 修改时填充
        this.setFieldValByName("updateTime", new Date(), metaObject);
    }
}
