package com.cy.share.service;

import com.cy.share.dto.QueryDto;
import com.cy.share.dto.ReleaseDto;
import com.baomidou.mybatisplus.extension.service.IService;
import com.cy.share.pojo.Log;
import com.cy.share.vo.FeedVo;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.LogEditVo;
import com.cy.share.vo.MyContentVo;

import java.util.List;

/**
* @author 21701
* @description 针对表【log】的数据库操作Service
* @createDate 2025-12-02 18:18:33
*/
public interface LogService extends IService<Log> {

    FeedVo queryLogList(QueryDto query);

    List<MyContentVo> listMyContent(Integer userId, Integer size);

    boolean add(ReleaseDto dto);

    LogDetailVo findById(Integer id);

    LogEditVo editById(Integer id);

    boolean updatebyId(ReleaseDto log);
}
