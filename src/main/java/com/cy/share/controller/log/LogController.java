package com.cy.share.controller.log;


import com.cy.share.common.annotation.UnInterception;
import com.cy.share.common.constant.ResultCode;
import com.cy.share.common.exception.LogGetException;
import com.cy.share.common.exception.LogSaveException;
import com.cy.share.common.exception.LogUpdateException;
import com.cy.share.common.utils.Result;
import com.cy.share.common.utils.UserContext;
import com.cy.share.dto.QueryDto;
import com.cy.share.dto.ReleaseDto;
import com.cy.share.service.EsService;
import com.cy.share.service.LikeService;
import com.cy.share.service.LogService;
import com.cy.share.vo.FeedVo;
import com.cy.share.vo.LikeTop3Vo;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.LogEditVo;
import com.cy.share.vo.LogListVo;
import com.cy.share.vo.SearchFeedVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/log")
public class LogController {
    @Autowired
    LogService logService;
    @Autowired
    LikeService likeService;
    @Autowired
    EsService esService;

    @GetMapping("/list")
    public Result logList(QueryDto query){
        try {
            FeedVo feed = logService.queryLogList(query);
            return Result.success(feed);
        } catch (Exception e) {
            log.error("查询博客列表失败", e);
            return Result.fail(ResultCode.DATA_HANDLE_ERROR);
        }
    }
    @PostMapping("/release")
    @UnInterception
    public Result release(@RequestBody ReleaseDto dto) {
        boolean save = logService.add(dto);
        if (!save) throw new LogSaveException();
        return Result.success();
    }
    @GetMapping("/detail/{id}")
    public Result logDetail(@PathVariable("id") Integer id){
        LogDetailVo log = logService.findById(id);
        if(log==null) throw new LogGetException();
        System.out.println(log.getCreateTime());
        return Result.success(log);
    }
    @GetMapping("/edit/{id}")
    public Result logEdit(@PathVariable("id") Integer id){
        LogEditVo byId = logService.editById(id);
        if(byId==null) throw new LogGetException();
        return Result.success(byId);
    }
    @PutMapping("/update/{id}")
    public Result update( // 从URL路径中获取博客ID（必须）
                          @PathVariable("id") Integer id,
                          // 从请求体中获取更新的博客数据（必须，绑定JSON到实体）
                          @RequestBody ReleaseDto log){
        log.setId(id);
        boolean ok = logService.updatebyId(log);
        if(!ok) throw new LogUpdateException();
        return Result.success();
    }

    @PostMapping("/like/{logId}")
    public Result like(@PathVariable("logId") Integer logId) {
        Integer userId = Integer.valueOf(UserContext.get());
        likeService.like(userId, logId);
        return Result.success();
    }

    @DeleteMapping("/like/{logId}")
    public Result unlike(@PathVariable("logId") Integer logId) {
        Integer userId = Integer.valueOf(UserContext.get());
        likeService.unlike(userId, logId);
        return Result.success();
    }

    @GetMapping("/like/top3/{logId}")
    public Result likeTop3(@PathVariable("logId") Integer logId) {
        List<LikeTop3Vo> top3 = likeService.getTop3Likers(logId);
        return Result.success(top3);
    }

    @GetMapping("/like/list")
    public Result userLikeList(@RequestParam(value = "page", defaultValue = "1") Integer page,
                               @RequestParam(value = "size", defaultValue = "10") Integer size) {
        Integer userId = Integer.valueOf(UserContext.get());
        List<LogListVo> list = likeService.getUserLikedLogs(userId, page, size);
        return Result.success(list);
    }

    @GetMapping("/search")
    public Result search(@RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
                         @RequestParam(value = "cursor", required = false) String cursor,
                         @RequestParam(value = "size", defaultValue = "10") Integer size) {
        SearchFeedVo result = esService.search(keyword, cursor, size);
        return Result.success(result);
    }
}
