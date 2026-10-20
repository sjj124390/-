package com.shop.online.controller;

import com.shop.online.common.Result;
import com.shop.online.dto.LoginDTO;
import com.shop.online.dto.UpdatePasswordDTO;
import com.shop.online.entity.Product;
import com.shop.online.entity.PurchaseIntent;
import com.shop.online.entity.Seller;
import com.shop.online.exception.BusinessException;
import com.shop.online.service.IntentService;
import com.shop.online.service.ProductService;
import com.shop.online.service.SellerService;
import com.shop.online.service.TradeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 前后端联调适配层（组员 D 新增，组员 A 前端与组员 B 后端代码均零改动）
 *
 * 背景：A 的前端按 /api/product/*、/api/order/*、/api/seller/* 契约开发，
 * B 的后端实现的是 /api/products/*、/api/intents、/api/admin/* 契约，
 * 两者路径、字段名、状态枚举、业务流程均不一致。本控制器把前端 10 个接口
 * 逐一映射到 B 的 Service / 组员 C 的存储过程上：
 *
 *  前端契约                          →  后端实际调用
 *  GET  /api/product/detail          →  productService.getCurrent()/findById
 *  POST /api/order/submit            →  submit_intent SP（buyerPhone→contact, remark→note）
 *  POST /api/seller/login            →  sellerService.login（补发演示 token）
 *  GET  /api/product/list            →  productService.history()（状态枚举转 0/1/2）
 *  POST /api/product/add             →  createDraft + publish_product SP（链式）
 *  POST /api/product/freeze          →  productService.withdraw（ON_SALE→OFF_SHELF）
 *  POST /api/product/restore         →  publish_product SP（OFF_SHELF→ON_SALE）
 *  GET  /api/order/list              →  purchase_intent JOIN product（状态转 0/1/2）
 *  POST /api/order/updateStatus      →  freeze_product + finish_trade SP（链式）
 *  POST /api/seller/updatePassword   →  sellerService.updatePassword
 *
 * 商品状态映射：ON_SALE→0(在售) FROZEN→1(冻结) OFF_SHELF→1(冻结) SOLD→2(已售) DRAFT→不暴露
 * 订单状态映射：PENDING→0(待交易) SELECTED→0(待交易) COMPLETED→1(成功) CANCELLED/EXPIRED→2(失败)
 *
 * 注意：C 的存储过程内部自行开启事务，本类所有方法禁止加 @Transactional。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FacadeController {

    private final ProductService productService;
    private final IntentService intentService;
    private final TradeService tradeService;
    private final SellerService sellerService;
    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ---------------- 状态映射 ----------------

    /** 商品状态：后端枚举 → 前端数字（DRAFT 返回 -1，列表中过滤） */
    private static int mapProductStatus(String status) {
        if (status == null) return 2;
        switch (status) {
            case "ON_SALE":   return 0;
            case "FROZEN":    return 1;
            case "OFF_SHELF": return 1;
            case "SOLD":      return 2;
            default:          return -1; // DRAFT
        }
    }

    /** 意向状态：后端枚举 → 前端订单数字 */
    private static int mapOrderStatus(String status) {
        if (status == null) return 2;
        switch (status) {
            case "PENDING":
            case "SELECTED":  return 0;
            case "COMPLETED": return 1;
            default:          return 2;  // CANCELLED / EXPIRED
        }
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(FMT);
    }

    /** 商品实体 → 前端字段形状 {id, name, description, imageUrl, price, status, createTime} */
    private static Map<String, Object> toFrontProduct(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("imageUrl", p.getImageUrl());
        m.put("price", p.getPrice());
        m.put("status", mapProductStatus(p.getStatus()));
        m.put("createTime", fmt(p.getCreatedAt()));
        return m;
    }

    // ---------------- 前台接口 ----------------

    /** 商品详情：id 可选，不传取当前在售/冻结商品 */
    @GetMapping("/product/detail")
    public Result<Map<String, Object>> productDetail(@RequestParam(required = false) Long id) {
        Product p = (id == null) ? productService.getCurrent() : productService.findById(id);
        if (p == null) {
            return Result.error(1001, "暂无在售商品");
        }
        return Result.success(toFrontProduct(p));
    }

    /** 提交购买意向：{productId, buyerName, buyerPhone, remark} → 返回口令码 */
    @PostMapping("/order/submit")
    public Result<Map<String, Object>> submitOrder(@RequestBody Map<String, Object> body) {
        Long productId = toLong(body.get("productId"));
        String buyerName = toStr(body.get("buyerName"));
        String buyerPhone = toStr(body.get("buyerPhone"));
        String remark = toStr(body.get("remark"));
        if (productId == null) throw new BusinessException(400, "商品信息缺失");
        if (buyerName == null || buyerName.trim().isEmpty()) throw new BusinessException(400, "请填写联系人姓名");
        if (buyerPhone == null || buyerPhone.trim().isEmpty()) throw new BusinessException(400, "请填写联系电话");

        Map<String, Object> r = intentService.submitIntent(productId, buyerName, buyerPhone, remark);
        Map<String, Object> data = new HashMap<>();
        data.put("intentId", r.get("intentId"));
        // 前端购买成功页识别 data.code 并展示口令码
        data.put("code", r.get("rawCode"));
        data.put("expiresAt", fmt((LocalDateTime) r.get("expiresAt")));
        return Result.success("购买意向已提交，请保存您的口令码", data);
    }

    // ---------------- 卖家登录 / 密码 ----------------

    /** 卖家登录：{username, password} → {token, username}（MVP 无鉴权，token 仅供前端登录态） */
    @PostMapping("/seller/login")
    public Result<Map<String, Object>> login(@RequestBody @Validated LoginDTO dto) {
        Seller s = sellerService.login(dto.getUsername(), dto.getPassword());
        Map<String, Object> data = new HashMap<>();
        data.put("token", UUID.randomUUID().toString().replace("-", ""));
        data.put("username", s.getUsername());
        return Result.success("登录成功", data);
    }

    /** 修改密码：{username, oldPassword, newPassword, confirmPassword} */
    @PostMapping("/seller/updatePassword")
    public Result<Void> updatePassword(@RequestBody @Validated UpdatePasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(400, "两次新密码不一致");
        }
        if (dto.getNewPassword().length() < 6) {
            throw new BusinessException(400, "新密码长度至少 6 位");
        }
        sellerService.updatePassword(dto.getOldPassword(), dto.getNewPassword());
        return Result.success("密码已更新", null);
    }

    // ---------------- 商品管理 ----------------

    /** 商品列表：当前（在售/冻结）+ 历史（已售），草稿不暴露给前端 */
    @GetMapping("/product/list")
    public Result<List<Map<String, Object>>> productList() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Product p : productService.history()) {
            if (mapProductStatus(p.getStatus()) < 0) continue; // DRAFT
            list.add(toFrontProduct(p));
        }
        return Result.success(list);
    }

    /** 发布新商品：{name, price, imageUrl, description} → 草稿 + 发布（链式） */
    @PostMapping("/product/add")
    public Result<Map<String, Object>> addProduct(@RequestBody Map<String, Object> body) {
        String name = toStr(body.get("name"));
        String description = toStr(body.get("description"));
        String imageUrl = toStr(body.get("imageUrl"));
        BigDecimal price = toPrice(body.get("price"));
        if (name == null || name.trim().isEmpty()) throw new BusinessException(400, "请填写商品名称");
        if (description == null || description.trim().isEmpty()) throw new BusinessException(400, "请填写商品描述");
        if (price == null || price.signum() <= 0) throw new BusinessException(400, "请填写正确的商品价格");

        // 预检：已有在售/冻结商品时给出中文提示（发布 SP 也会拦截，这里提前一步）
        Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE status IN ('ON_SALE','FROZEN')", Integer.class);
        if (active != null && active > 0) {
            throw new BusinessException(1003, "已有在售或冻结中的商品，请先完成当前交易或冻结下架");
        }

        Product draft = productService.createDraft(name, description, imageUrl == null ? "" : imageUrl, price);
        try {
            productService.publish(draft.getId());
        } catch (RuntimeException e) {
            // 发布失败时清理本次创建的草稿，避免残留隐形数据
            jdbc.update("DELETE FROM product WHERE id=? AND status='DRAFT'", draft.getId());
            throw e;
        }
        Product published = productService.findById(draft.getId());
        return Result.success("商品发布成功", toFrontProduct(published));
    }

    /** 手动冻结：在售 → 下架（OFF_SHELF，前端显示为「冻结」），同时取消未处理的意向 */
    @PostMapping("/product/freeze")
    public Result<Void> freezeProduct(@RequestParam Long id) {
        Product p = productService.findById(id);
        if (p == null) throw new BusinessException(1001, "商品不存在");
        switch (p.getStatus()) {
            case "ON_SALE":
                productService.withdraw(id);
                return Result.success("商品已冻结", null);
            case "FROZEN":
                throw new BusinessException(1004, "商品已被买家锁定，请在「意向订单」中处理该交易");
            default:
                throw new BusinessException(1004, "当前商品状态无需冻结");
        }
    }

    /** 解除冻结：下架 → 重新上架（publish_product SP 允许 OFF_SHELF→ON_SALE） */
    @PostMapping("/product/restore")
    public Result<Void> restoreProduct(@RequestParam Long id) {
        Product p = productService.findById(id);
        if (p == null) throw new BusinessException(1001, "商品不存在");
        switch (p.getStatus()) {
            case "OFF_SHELF":
                productService.publish(id);
                return Result.success("商品已恢复在售", null);
            case "FROZEN":
                throw new BusinessException(1004, "商品已被买家锁定，请先在意向订单中完成或取消交易");
            case "ON_SALE":
                throw new BusinessException(1004, "商品当前已在售，无需解除");
            default:
                throw new BusinessException(1004, "商品已售出，不能重新上架");
        }
    }

    // ---------------- 意向订单 ----------------

    /** 订单列表：意向 JOIN 商品名，按提交时间倒序 */
    @GetMapping("/order/list")
    public Result<List<Map<String, Object>>> orderList() {
        String sql = "SELECT i.id, i.product_id, p.name AS product_name, "
                + "i.buyer_name, i.contact, i.note, i.status, i.created_at "
                + "FROM purchase_intent i JOIN product p ON p.id = i.product_id "
                + "ORDER BY i.created_at DESC, i.id DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.get("id"));
            m.put("orderId", r.get("id"));
            m.put("productId", r.get("product_id"));
            m.put("productName", r.get("product_name"));
            m.put("buyerName", r.get("buyer_name"));
            m.put("buyerPhone", r.get("contact"));
            m.put("remark", r.get("note"));
            m.put("status", mapOrderStatus((String) r.get("status")));
            Object ts = r.get("created_at");
            // Connector/J 8 默认把 DATETIME 映射为 LocalDateTime（旧驱动为 java.sql.Timestamp），两种都兼容
            LocalDateTime createdAt = ts == null ? null
                    : (ts instanceof LocalDateTime ? (LocalDateTime) ts : ((java.sql.Timestamp) ts).toLocalDateTime());
            m.put("createTime", fmt(createdAt));
            list.add(m);
        }
        return Result.success(list);
    }

    /**
     * 标记交易结果：{orderId, status}（status: 1 成功 / 2 失败）
     *
     * orderId 即意向 id。B 的领域模型里「成交」必须经由 冻结→完成 两段式 SP：
     *  - 意向仍 PENDING：先 freeze_product（商品冻结+生成待处理交易），再 finish_trade
     *  - 意向已 SELECTED：说明已冻结，直接对现有待处理交易 finish_trade
     * 标记失败时：PENDING 意向直接条件取消；SELECTED 意向走 finish_trade(失败)，商品自动恢复在售
     */
    @PostMapping("/order/updateStatus")
    public Result<Void> updateOrderStatus(@RequestBody Map<String, Object> body) {
        Long orderId = toLong(body.get("orderId"));
        Integer status = toInt(body.get("status"));
        if (orderId == null) throw new BusinessException(400, "订单 id 缺失");
        if (status == null || (status != 1 && status != 2)) throw new BusinessException(400, "非法的状态值");

        PurchaseIntent intent = intentService.findById(orderId);
        if (intent == null) throw new BusinessException(2001, "订单不存在");

        String st = intent.getStatus();
        if ("COMPLETED".equals(st)) throw new BusinessException(2002, "该订单已成交，无需重复操作");
        if ("CANCELLED".equals(st) || "EXPIRED".equals(st)) throw new BusinessException(2002, "该订单已关闭，无法操作");

        if (status == 1) {
            // ---- 标记成功 ----
            Long tradeId;
            if ("PENDING".equals(st)) {
                Product p = productService.findById(intent.getProductId());
                if (p == null || !"ON_SALE".equals(p.getStatus())) {
                    throw new BusinessException(2002, "商品当前已被锁定，请先处理进行中的交易");
                }
                tradeId = tradeService.freeze(intent.getProductId(), orderId);
            } else {
                tradeId = findPendingTradeId(orderId);
                if (tradeId == null) throw new BusinessException(2002, "未找到待处理交易，请刷新后重试");
            }
            tradeService.finish(intent.getProductId(), tradeId, true, null);
            return Result.success("交易成交成功，商品已售出", null);
        }

        // ---- 标记失败 ----
        if ("PENDING".equals(st)) {
            int rows = jdbc.update(
                    "UPDATE purchase_intent SET status='CANCELLED' WHERE id=? AND status='PENDING'", orderId);
            if (rows != 1) throw new BusinessException(2002, "订单状态已变化，请刷新后重试");
            return Result.success("已标记失败，该购买意向已取消", null);
        }
        Long tradeId = findPendingTradeId(orderId);
        if (tradeId == null) throw new BusinessException(2002, "未找到待处理交易，请刷新后重试");
        tradeService.finish(intent.getProductId(), tradeId, false, "卖家标记交易失败");
        return Result.success("已标记失败，商品已恢复在售", null);
    }

    /** 查询某意向的待处理交易 id */
    private Long findPendingTradeId(Long intentId) {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM offline_trade WHERE intent_id=? AND status='PENDING' ORDER BY id DESC LIMIT 1",
                Long.class, intentId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // ---------------- 参数转换 ----------------

    private static String toStr(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal toPrice(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }
}
