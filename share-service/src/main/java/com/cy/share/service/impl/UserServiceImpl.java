package com.cy.share.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cy.share.pojo.User;
import com.cy.share.service.UserService;
import com.cy.share.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author 21701
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-12-02 18:15:04
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




