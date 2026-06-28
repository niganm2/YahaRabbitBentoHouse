package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;
    /**
     * 定时处理超时订单
     */
    @Scheduled(cron = "0 * * * * ?") // 每分钟执行一次
    public void processTimeoutOrder(){
        log.info("定时处理超时订单,{}", LocalDateTime.now());
        //查询订单当前状态，并且订单的创建时间在当前时间之前，并且支付状态是待支付
        //select * from orders where status = ？ and order_time < (当前时间 - 15分钟)
        LocalDateTime time = LocalDateTime.now().minusMinutes(15);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);
        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                //更新订单状态为4 订单取消
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("订单超时，自动取消");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }
    }

    /**
     * 定时处理处于派送状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨1点执行一次
    public void processDeliveryOrder(){
        log.info("定时处理处于派送状态的订单,{}", LocalDateTime.now());
        //查询订单当前状态，并且订单的派送时间在当前时间之前，并且支付状态是派送中
        //select * from orders where status = ？ and order_time < (当前时间 - 1h)
        LocalDateTime time = LocalDateTime.now().minusMinutes(60);
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);
        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                //更新订单状态为5 订单完成
                orders.setStatus(Orders.COMPLETED);
            }
        }
    }
}
