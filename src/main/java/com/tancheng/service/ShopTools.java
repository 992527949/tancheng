package com.tancheng.service;

import com.tancheng.entity.Shop;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ShopTools {

    @Autowired
    private IShopService shopService;


    @Tool(description = "Search for relevant business information based on the keywords provided by the user (such as shop name, cuisine, or address). Return the business's name, rating, and address. ")
    String searchShops(String query) {
        List<Shop> shops = shopService.queryShopByName(query);
        if (shops.isEmpty()) {
            return "没有找到相关商家";
        }

        StringBuilder result = new StringBuilder("找到以下商家：\n");
        shops.stream().limit(3).forEach(shop -> {
            result.append(String.format("🏪 %s (评分: %.1f)\n", shop.getName(), shop.getScore() / 10.0));
            result.append(String.format("📍 %s\n", shop.getAddress()));
        });
        return result.toString();
    }

    @Tool(description = "Get the current date and time in the user's timezone")
    String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }

//    @Tool(description = "获取优惠券信息")
//    public String getVoucherInfo() {
//        List<Voucher> vouchers = voucherService.queryHotVoucher();
//        if (vouchers.isEmpty()) {
//            return "暂无优惠券活动";
//        }
//
//        StringBuilder result = new StringBuilder("🎫 热门优惠券：\n");
//        vouchers.stream().limit(3).forEach(voucher -> {
//            result.append(String.format("- %s: %d元券\n", voucher.getTitle(), voucher.getActualValue()));
//        });
//        return result.toString();
//    }
}
