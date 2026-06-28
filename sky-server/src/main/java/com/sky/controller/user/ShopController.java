package com.sky.controller.user;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {
    public static final String KEY = "SHOP_STATUS";
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 获取营业状态
     *
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取营业状态")
    public Result<Integer> getStatus() {
        Integer status = (Integer) redisTemplate.opsForValue().get(KEY);
        log.info("获取营业状态为,{}", status == 1 ? "营业中" : "打烊中");
        return Result.success(status);
    }

    /**
     * 获取商家信息
     *
     * @return
     */
    @GetMapping("/getMerchantInfo")
    @ApiOperation("获取商家信息")
    public Result<MerchantInfoDTO> getMerchantInfo() {
        MerchantInfoDTO merchantInfoDTO = new MerchantInfoDTO();
        merchantInfoDTO.setPhone("13800138000");
        merchantInfoDTO.setDeliveryFee(2);
        return Result.success(merchantInfoDTO);
    }

    @Data
    public static class MerchantInfoDTO {
        private String phone;
        private Integer deliveryFee;
    }
}

