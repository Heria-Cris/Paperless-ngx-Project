package com.paperless.local.service.impl;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.paperless.local.entity.User;
import com.paperless.local.mapper.UserMapper;
import com.paperless.local.service.UserService;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}
