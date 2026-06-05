package com.cy.share.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cy.share.dto.QueryDto;
import com.cy.share.dto.ReleaseDto;
import com.cy.share.mapper.LogPicMapper;
import com.cy.share.mapper.UserMapper;
import com.cy.share.pojo.Log;
import com.cy.share.pojo.LogPic;
import com.cy.share.service.EsSyncProducer;
import com.cy.share.service.LikeService;
import com.cy.share.service.LogService;
import com.cy.share.mapper.LogMapper;
import com.cy.share.common.utils.UserContext;
import com.cy.share.vo.FeedVo;
import com.cy.share.vo.LogDetailVo;
import com.cy.share.vo.LogEditVo;
import com.cy.share.vo.LogListVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogServiceImpl extends ServiceImpl<LogMapper, Log>
    implements LogService {

    @Autowired
    LogMapper logMapper;
    @Autowired
    LogPicMapper logPicMapper;
    @Autowired
    LikeService likeService;
    @Autowired
    UserMapper userMapper;
    @Autowired
    EsSyncProducer esSyncProducer;

    @Override
    public FeedVo queryLogList(QueryDto query) {
        int size = query.getSize() != null ? query.getSize() : 10;
        String key = query.getAuthor();
        Date cursor = query.getCursor() != null ? new Date(query.getCursor()) : null;

        List<LogListVo> list = logMapper.queryLogList(cursor, size, key);
        if (list.isEmpty()) return new FeedVo(Collections.emptyList(), null);

        List<Integer> logIds = list.stream().map(LogListVo::getId).collect(Collectors.toList());
        List<LogPic> pics = logPicMapper.selectByLogIds(logIds);
        Map<Integer, List<String>> picMap = pics.stream()
                .collect(Collectors.groupingBy(LogPic::getLogId,
                        Collectors.mapping(LogPic::getPicurl, Collectors.toList())));
        list.forEach(vo -> vo.setPicurls(picMap.getOrDefault(vo.getId(), new ArrayList<>())));

        // 填充当前用户点赞状态
        String userIdStr = UserContext.get();
        if (userIdStr != null) {
            Integer userId = Integer.valueOf(userIdStr);
            Map<Integer, Boolean> likedMap = likeService.isLikedBatch(userId, logIds);
            list.forEach(vo -> vo.setIsLiked(likedMap.getOrDefault(vo.getId(), false)));
        } else {
            list.forEach(vo -> vo.setIsLiked(false));
        }

        // 最后一条的 createTime 作为下一页游标
        Long nextCursor = list.get(list.size() - 1).getCreateTime().getTime();
        return new FeedVo(list, nextCursor);
    }

    @Override
    @Transactional
    public boolean add(ReleaseDto dto) {
        Log log = new Log();
        log.setTitle(dto.getTitle());
        log.setInfo(dto.getInfo());
        log.setAuthor(dto.getAuthor());
        Integer love = new Random().nextInt(100000);
        log.setLove(love);
        boolean ok = logMapper.add(log);
        if (ok && dto.getPicurls() != null && !dto.getPicurls().isEmpty()) {
            List<LogPic> pics = new ArrayList<>();
            for (int i = 0; i < dto.getPicurls().size(); i++) {
                LogPic p = new LogPic();
                p.setLogId(log.getId());
                p.setPicurl(dto.getPicurls().get(i));
                p.setSort(i);
                pics.add(p);
            }
            logPicMapper.insertBatch(pics);
        }
        if (ok) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    esSyncProducer.sendBlogCreate(log.getId());
                }
            });
        }
        return ok;
    }

    @Override
    public LogDetailVo findById(Integer id) {
        LogDetailVo vo = logMapper.findById(id);
        if (vo != null) {
            vo.setPicurls(logPicMapper.selectUrlsByLogId(id));
            String userIdStr = UserContext.get();
            if (userIdStr != null) {
                vo.setIsLiked(likeService.isLiked(Integer.valueOf(userIdStr), id));
            } else {
                vo.setIsLiked(false);
            }
        }
        return vo;
    }

    @Override
    public LogEditVo editById(Integer id) {
        LogEditVo vo = logMapper.editById(id);
        if (vo != null) {
            vo.setPicurls(logPicMapper.selectUrlsByLogId(id));
        }
        return vo;
    }

    @Override
    @Transactional
    public boolean updatebyId(ReleaseDto log) {
        boolean ok = logMapper.updatebyId(log);
        if (ok && log.getPicurls() != null) {
            logPicMapper.deleteByLogId(log.getId());
            if (!log.getPicurls().isEmpty()) {
                List<LogPic> pics = new ArrayList<>();
                for (int i = 0; i < log.getPicurls().size(); i++) {
                    LogPic p = new LogPic();
                    p.setLogId(log.getId());
                    p.setPicurl(log.getPicurls().get(i));
                    p.setSort(i);
                    pics.add(p);
                }
                logPicMapper.insertBatch(pics);
            }
        }
        if (ok) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    esSyncProducer.sendBlogUpdate(log.getId());
                }
            });
        }
        return ok;
    }
}
