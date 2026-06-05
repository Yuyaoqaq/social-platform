package com.cy.share.mapper;

import com.cy.share.dto.ReleaseDto;
import com.cy.share.pojo.Log;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.LogEditVo;
import com.cy.share.vo.LogListVo;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
* @author 21701
* @description 针对表【log】的数据库操作Mapper
* @createDate 2025-12-02 18:18:33
* @Entity com.cy.share.pojo.Log
*/
public interface LogMapper extends BaseMapper<Log> {

    List<LogListVo> queryLogList(@Param("cursor") Date cursor, @Param("size") Integer size, @Param("key") String key);

    boolean add(Log log);

    LogDetailVo findById(Integer id);

    LogEditVo editById(Integer id);

    boolean updatebyId(ReleaseDto log);

    int incrementLove(@Param("logId") Integer logId);

    int decrementLove(@Param("logId") Integer logId);
}




