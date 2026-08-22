package com.stdntedu.config;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.stdntedu.resource.service.SystemTimezoneProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {
    private final SystemTimezoneProvider time;

    public MybatisMetaObjectHandler(SystemTimezoneProvider time) {
        this.time = time;
    }

    @Override public void insertFill(MetaObject metaObject) {
        LocalDateTime now = time.localDateTime();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "deleted", Boolean.class, false);
        strictInsertFill(metaObject, "version", Integer.class, 0);
    }
    @Override public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, time.localDateTime());
    }
}
