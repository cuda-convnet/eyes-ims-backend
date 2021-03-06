package com.tongren.controller;

import com.tongren.bean.CommonResult;
import com.tongren.bean.Constant;
import com.tongren.pojo.User;
import com.tongren.service.RedisService;
import com.tongren.service.UserService;
import com.tongren.util.MD5Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;


/**
 * 登录、注册和首页跳转
 * Created by zlren on 2017/6/6.
 */
@Controller
@RequestMapping("auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private RedisService redisService;

    /**
     * 发送短信验证码
     *
     * @param params
     * @return
     */
    @RequestMapping(value = "send_sms", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult sendSms(@RequestBody Map<String, String> params) {

        String phone = params.get(Constant.USERNAME);

        User record = new User();
        record.setUsername(phone);
        User user = this.userService.queryOne(record);
        String action = params.get("action");
        if (action.equals("注册") || action.equals("修改手机")) {
            if (user != null) {
                return CommonResult.failure("此号码已经注册");
            }
        } else if (action.equals("找回密码")) {
            if (user == null) {
                return CommonResult.failure("此号码未注册");
            }
        }

        if (redisService.get(phone) != null) {
            return CommonResult.failure("请1分钟后再试");
        }

        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < Constant.SMS_CODE_LEN; i++) {
            code.append(String.valueOf(random.nextInt(10)));
        }

        logger.info("验证码，手机号：{}，验证码：{}", phone, code);

        CommonResult result;
        try {
            result = CommonResult.success("已发送验证码", record.getId());
            // result = SMSUtil.send(phone, String.valueOf(code));
        } catch (Exception e) {
            e.printStackTrace();
            return CommonResult.failure("短信发送失败，请重试");
        }

//        // 存储在redis中，过期时间为60s
//        redisService.setSmSCode(Constant.REDIS_PRE_CODE + phone, String.valueOf(code));

        return result;
    }

    /**
     * 注册
     *
     * @param params
     * @return
     */
    @RequestMapping(value = "register", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult register(@RequestBody Map<String, String> params) {

        // username就是手机号
        String username = params.get(Constant.USERNAME);
        String password = params.get(Constant.PASSWORD);
        String inputCode = params.get(Constant.INPUT_CODE);
        String name = params.get(Constant.NAME);
        logger.info("name = {}", name);
        logger.info("inputCode = {}", inputCode);

//        String code = redisService.get(Constant.REDIS_PRE_CODE + phone);
        String code = Constant.AUTH_CODE;
        if (code == null) {
            return CommonResult.failure("验证码过期");
        } else if (!code.equals(inputCode)) {
            return CommonResult.failure("验证码错误");
        }

        if (this.userService.isExist(username)) {
            return CommonResult.failure("用户名已经存在");
        }

        try {
            User user = new User();
            user.setUsername(username);
            user.setPassword(MD5Util.generate(password));
            user.setRole(Constant.DOCTOR);
            user.setName(name);
            user.setAvatar("avatar_default.png"); // 默认头像
            this.userService.save(user);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return CommonResult.failure("注册失败");
        }

        return CommonResult.success("注册成功");
    }


    /**
     * 校验验证码
     *
     * @param params
     * @return
     */
    @RequestMapping(value = "check_code", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult checkSMSCode(@RequestBody Map<String, String> params) {

        String inputCode = params.get(Constant.INPUT_CODE);
        String username = params.get(Constant.USERNAME);

        logger.info("传过来的验证码是: {}， 手机是：{}", inputCode, username);

        //用户名是否存在
        if(!this.userService.isExist(username)){
            return CommonResult.failure("不存在该用户");
        }

        String code = Constant.AUTH_CODE;
        if (code == null) {
            return CommonResult.failure("验证码过期");
        } else if (!code.equals(inputCode)) {
            return CommonResult.failure("验证码错误");
        }

        User record = new User();
        record.setUsername(username);

        return CommonResult.success("验证成功", this.userService.queryOne(record).getId());
    }


    /**
     * 登录
     *
     * @param params
     * @return
     */
    @RequestMapping(value = "login", method = RequestMethod.POST)
    @ResponseBody
    public CommonResult login(@RequestBody Map<String, String> params) {

        // 得到用户名和密码，用户名就是phone
        String username = params.get(Constant.USERNAME);
        String password = params.get(Constant.PASSWORD);

        return this.userService.login(username, password, "null");
    }


    /**
     * 未验证跳转
     *
     * @return
     */
    @RequestMapping(value = "login_denied")
    @ResponseBody
    public CommonResult loginDenied() {
        logger.info("login_denied");
        return CommonResult.failure("请先登录");
    }


    /**
     * 权限拒绝
     *
     * @return
     */
    @RequestMapping(value = "role_denied")
    @ResponseBody
    public CommonResult roleDenied() {
        logger.info("role_denied");
        return CommonResult.failure("无此权限");
    }
}
