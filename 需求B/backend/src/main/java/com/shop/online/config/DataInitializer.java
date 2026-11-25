package com.shop.online.config;

import com.shop.online.repository.CustomerRepository;
import com.shop.online.repository.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时初始化唯一卖家账号与一个演示买家账号。
 * 卖家：admin / admin123
 * 买家：buyer / 123456
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SellerRepository sellerRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SellerRepository sellerRepository,
                           CustomerRepository customerRepository,
                           PasswordEncoder passwordEncoder) {
        this.sellerRepository = sellerRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sellerRepository.count() == 0) {
            sellerRepository.insert("admin", passwordEncoder.encode("admin123"));
            log.info("已初始化卖家账号 admin / admin123");
        }
        if (!customerRepository.existsByUsername("buyer")) {
            customerRepository.insert("buyer", passwordEncoder.encode("123456"), "13800000000", "图书馆一楼大厅");
            log.info("已初始化演示买家账号 buyer / 123456");
        }
        log.info("在线购物系统（需求 B）初始化完成");
    }
}
