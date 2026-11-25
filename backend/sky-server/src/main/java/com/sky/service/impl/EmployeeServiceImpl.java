package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.exception.AccountLockedException;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.result.PageResult;
import com.sky.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.List;

@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        String rawPassword = employeeLoginDTO.getPassword();
        String storedPassword = employee.getPassword();
        boolean passwordMatched;
        if (isLegacyMd5(storedPassword)) {
            //兼容历史无盐MD5数据，校验通过后自动升级为BCrypt
            passwordMatched = DigestUtils.md5DigestAsHex(rawPassword.getBytes()).equalsIgnoreCase(storedPassword);
            if (passwordMatched) {
                Employee upgrade = new Employee();
                upgrade.setId(employee.getId());
                upgrade.setPassword(passwordEncoder.encode(rawPassword));
                employeeMapper.update(upgrade);
            }
        } else {
            passwordMatched = passwordEncoder.matches(rawPassword, storedPassword);
        }
        if (!passwordMatched) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (StatusConstant.DISABLE.equals(employee.getStatus())) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象，不返回密码
        employee.setPassword(null);
        return employee;
    }

    /**
     * 判断是否为历史遗留的32位无盐MD5密码
     */
    private boolean isLegacyMd5(String password) {
        return password != null && password.matches("[0-9a-fA-F]{32}");
    }

    /**
     * 新增员工
     * @param employeeDTO
     * @return
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO, employee);

        //设置状态：默认正常 1
        employee.setStatus(StatusConstant.ENABLE);
        //设置密码：默认 123456，使用BCrypt加盐存储
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));
//        //设置创建时间
//        employee.setCreateTime(LocalDateTime.now());
//        //设置更新时间
//        employee.setUpdateTime(LocalDateTime.now());
//        //设置创建人ID,修改人ID
//        employee.setCreateUser(BaseContext.getCurrentId());
//        employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    /**
     * 分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        //select * from employee limit start,pageSize
        //开始分页查询
        PageHelper.startPage(employeePageQueryDTO.getPage(), employeePageQueryDTO.getPageSize());

        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        long total = page.getTotal();
        List<Employee> records = page.getResult();
        return new PageResult(total, records);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    @Override
    public void startOrStop(Integer status, Long id) {
        //update employee set status = ? where id = ?
        Employee employee = new Employee();
        employee.setStatus(status);
        employee.setId(id);

//        Employee employee = Employee.builder()
//                .status(status)
//                .id(id)
//                .build();

        employeeMapper.update(employee);
    }

    /**
     * 根据ID查询员工信息
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        employee.setPassword("****");//避免将密码返回给前端
        return employee;
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO, employee);//属性拷贝

//        employee.setUpdateTime(LocalDateTime.now());//设置更新时间
//        employee.setUpdateUser(BaseContext.getCurrentId());//设置更新人id

        employeeMapper.update(employee);
    }

}
