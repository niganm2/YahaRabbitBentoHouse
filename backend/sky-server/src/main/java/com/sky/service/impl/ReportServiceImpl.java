package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 统计指定的营业额
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        //循环添加
        while (!begin.equals(end)) {
            //获取日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }
        //存放从begin到end的营业额
        List<Double> turnoverList = new ArrayList<>();

        for (LocalDate date : dateList) {
            //查询营业额,"已完成"的订单
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //select sum(amount) from orders where order_time >= beginTime and order_time < endTime and status = 5
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = turnover == null ? 0.0 : turnover; //营业额为null则设置为0.0
            turnoverList.add(turnover);
        }

        //存放从begin到end的营业额并返回
        return TurnoverReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
    }

    /**
     * 统计用户数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            //获取日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放新增用户数 select count(id) from user where create_time >= beginTime and create_time < endTime
        List<Integer> newUserList = new ArrayList<>();
        //存放总用户数 select count(id) from user where create_time < endTime
        List<Integer> totalUserList = new ArrayList<>();

        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("end", endTime);
            //总用户数
            Integer totalUser = userMapper.countByMap(map);

            map.put("begin", beginTime);
            //新增用户数
            Integer newUser = userMapper.countByMap(map);
            totalUserList.add(totalUser);
            newUserList.add(newUser);
        }

        return UserReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
    }

    /**
     * 统计订单数据
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrderStatistics(LocalDate begin, LocalDate end) {
        //存放从begin到end的日期
        List<LocalDate> dateList = new ArrayList<>();

        dateList.add(begin);

        while (!begin.equals(end)) {
            //获取日期
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        //存放从begin到end的订单数
        List<Integer> orderCountList = new ArrayList<>();
        //存放从begin到end的有效订单数
        List<Integer> validOrderCountList = new ArrayList<>();

        //遍历dateList集合，查询每天订单数据
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);
            //查询每天订单数 SELECT count(id) FROM orders WHERE order_time >= beginTime AND order_time < endTime
            Integer orderCount = getOrderCount(beginTime, endTime, null);
            //查询每天有效订单数 SELECT count(id) FROM orders WHERE order_time >= beginTime AND order_time < endTime AND status = 5
            Integer validOrderCount = getOrderCount(beginTime, endTime, 5);

            orderCountList.add(orderCount);
            validOrderCountList.add(validOrderCount);
        }

        //区间内订单总数 stream流
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();
        //区间内有效订单总数
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        //订单完成率
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0) {
            orderCompletionRate = validOrderCount.doubleValue() / totalOrderCount;
        }

        return OrderReportVO
                .builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计销量排名top10
     *
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        //select od.name,sum(od.number) number from order_detail od,orders o where od.order_id = o.id and o.status = 5
        //and o.order_time >= beginTime and o.order_time < endTime group by od.name order by number desc limit 0,10
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        String nameList = StringUtils.join(names, ",");
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());
        String numberList = StringUtils.join(numbers, ",");

        return SalesTop10ReportVO
                .builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();
    }

    /**
     * 导出营业数据
     *
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        //1.查询数据库，获取营业数据 -- 最近30天
        LocalDate begin = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now().minusDays(1);

        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        //查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(beginTime, endTime);
        //2.通过POI将数据写入到Excel文件中
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        try {
            //基于模板创建Excel表格对象
            XSSFWorkbook excel = new XSSFWorkbook(in);
            //获取表格Sheet1
            XSSFSheet sheet = excel.getSheet("Sheet1");
            //填充数据 -- 时间
            sheet.getRow(1).getCell(1).setCellValue("时间" + begin + "至" + end);

            //获得第四行
            XSSFRow row = sheet.getRow(3);
            //填充数据 -- 营业额
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            //填充数据 -- 订单完成率
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            //填充数据 -- 新增用户数
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            //获得第五行
            row = sheet.getRow(4);
            //填充数据 -- 有效订单数
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            //填充数据 -- 平均客单价
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = begin.plusDays(i);

                LocalDateTime dateBegin = LocalDateTime.of(date, LocalTime.MIN);
                LocalDateTime dateEnd = LocalDateTime.of(date, LocalTime.MAX);
                //查询某一天的营业数据
                workspaceService.getBusinessData(dateBegin, dateEnd);

                row = sheet.getRow(7 + i); //获取某一行
                row.getCell(1).setCellValue(date.toString()); //第二个单元格 -- 日期
                row.getCell(2).setCellValue(businessDataVO.getTurnover()); //第三个单元格 -- 营业额
                row.getCell(3).setCellValue(businessDataVO.getValidOrderCount()); //第四个单元格 -- 有效订单数
                row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate()); //第五个单元格 -- 订单完成率
                row.getCell(5).setCellValue(businessDataVO.getUnitPrice()); //第六个单元格 -- 平均客单价
                row.getCell(6).setCellValue(businessDataVO.getNewUsers()); //第七个单元格 -- 新增用户数
            }

            //3.通过输出流将Excel文件下载到客户机
            ServletOutputStream out = response.getOutputStream();

            excel.write(out);
            out.close();
            excel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private Integer getOrderCount(LocalDateTime begin, LocalDateTime end, Integer status) {
        Map map = new HashMap();
        map.put("begin", begin);
        map.put("end", end);
        map.put("status", status);
        return orderMapper.countByMap(map);
    }
}
