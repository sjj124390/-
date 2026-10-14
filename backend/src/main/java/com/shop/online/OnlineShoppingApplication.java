package com.shop.online;

import com.shop.online.service.SellerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 在线购物系统启动类
 *
 * 注意：不再使用 MyBatis（已迁移到 JdbcTemplate），移除 @MapperScan
 */
@SpringBootApplication
public class OnlineShoppingApplication implements CommandLineRunner {

    private final SellerService sellerService;

    public OnlineShoppingApplication(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    public static void main(String[] args) {
        SpringApplication.run(OnlineShoppingApplication.class, args);
    }

    /**
     * 应用启动时确保唯一卖家已初始化（组员C 要求 seller.id=1）
     * 默认账号 admin / 密码 admin123，首次启动后请立即修改密码
     */
    @Override
    public void run(String... args) {
        sellerService.ensureInitialized("admin", "admin123");
        System.out.println("============================================");
        System.out.println("  在线购物系统后端启动成功，端口：8080");
        System.out.println("  接口基地址：http://localhost:8080/api");
        System.out.println("  默认卖家账号：admin / admin123");
        System.out.println("============================================");
    }
}
