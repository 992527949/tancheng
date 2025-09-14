package com.tancheng.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tancheng.dto.LoginFormDTO;
import com.tancheng.dto.Result;
import com.tancheng.dto.UserDTO;
import com.tancheng.entity.User;
import com.tancheng.mapper.UserMapper;
import com.tancheng.service.IUserService;
import com.tancheng.utils.RegexUtils;
import com.tancheng.utils.UserHolder;
//import lombok.var;
//import org.omg.PortableInterceptor.USER_EXCEPTION;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.tancheng.utils.RedisConstants.*;
import static com.tancheng.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //2 不符合要求返回错误信息
        //3 符合要求，生成验证码
        String code  = RandomUtil.randomNumbers(6);
        //4 保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5 发送验证码到手机
        log.debug("发送成功，验证码："+code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //2 redis获取验证码，校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //3 不一致返回错误信息
        //4 一致，查询用户信息 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        //5 判断用户是否存在
        if (user == null) {
            //6 不存在，创建新用户
            user = createUserWithPhone(phone);
        }
        //7 保存用户信息到redis
            //生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true).setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
            //User转为hash存储
            //存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是第几天,offset
        int offset = now.getDayOfMonth() -1;
        //写入redis， setbit key offset value
        stringRedisTemplate.opsForValue().setBit(key, offset, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 获取签到记录
        //获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        //获取日期
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        //拼接key
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是第几天,offset
        int dayOfMonth = now.getDayOfMonth();
        //获取签到记录 BITFIELD key GET u（dayofmonth） 0
        List<Long> longs = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        //得到的值循环遍历，获取连续签到天数，右移与1做与运算
        if (longs == null || longs.isEmpty()) {
            return Result.ok(0);
        }
        Long num = longs.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        int count = 0;
        while (true){
            if ((num & 1) ==0) {
                break;
            }else{
                count++;
            }
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
